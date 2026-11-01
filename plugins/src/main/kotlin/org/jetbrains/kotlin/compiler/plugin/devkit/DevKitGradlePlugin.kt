package org.jetbrains.kotlin.compiler.plugin.devkit

import com.autonomousapps.GradleTestKitSupportExtension
import com.github.gmazzo.buildconfig.BuildConfigExtension
import com.github.gmazzo.buildconfig.BuildConfigType
import kotlin.io.path.Path
import kotlin.io.path.name
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.project.IsolatedProject
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.domainObjectContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

/** A single Kotlin version that the consuming Gradle plugin's functional tests run against. */
public class DevKitGradlePluginTestTarget(
    override val version: KotlinToolingVersion,
    public val testTask: TaskProvider<out Test>,
) : Named, DevKitTestTarget {
    override fun getName(): String = version.toString()

    override fun equals(other: Any?): Boolean =
        other is DevKitGradlePluginTestTarget && other.version == version

    override fun hashCode(): Int = version.hashCode()
}

public sealed class ProjectOrGav {
    internal abstract val artifact: String

    public data class LocalProject(public val isolated: IsolatedProject) : ProjectOrGav() {
        override val artifact: String
            get() = isolated.name
    }

    public data class External(
        val group: String,
        override val artifact: String,
        val version: String?,
    ) : ProjectOrGav()

    public data class Sibling(override val artifact: String) : ProjectOrGav()
}

context(_: HasProject)
private val ProjectOrGav.group: String
    get() =
        when (this) {
            is External -> group
            is LocalProject,
            is Sibling -> project.group.toString()
        }

context(_: HasProject)
private val ProjectOrGav.version: String?
    get() =
        when (this) {
            is External -> version
            is LocalProject,
            is Sibling -> project.version.toString()
        }

// GradleTestKitSupportExtension doesn't expose it publicly
private const val FUNCTIONAL_TEST = "functionalTest"

private val FUNCTIONAL_TEST_ARGUMENT_PROVIDERS =
    setOf("RepoAndVersionArgumentProvider", "IncludeBuildReposArgumentProvider")

public open class GradlePluginConfigExtension(project: Project) :
    DevKitPropertiesDsl<DevKitGradlePluginTestTarget>(
        project,
        DevKitGradlePluginTestTarget::class,
    ) {
    private val buildConfig: BuildConfigExtension = project.the()
    private val gradleTestKitSupport: GradleTestKitSupportExtension = project.the()
    private val sourceSets: SourceSetContainer = project.the()
    private val main = sourceSets.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
    private val functionalTest = sourceSets.getByName(FUNCTIONAL_TEST)

    public val kmpTarget: Provider<String> = gradleProperty(KMP_TARGET_PROPERTY)

    /**
     * The compiler plugin project that this Gradle plugin applies. Defaults to the project at the
     * path in [COMPILER_PLUGIN_PROPERTY], if that is set.
     */
    public val compilerPlugin: Property<ProjectOrGav> = objects.property()

    init {
        gradleProperty(COMPILER_PLUGIN_PROPERTY).orNull?.let {
            compilerPlugin.convention(
                when {
                    it.isProjectPath -> ProjectOrGav.LocalProject(project.project(it).isolated)
                    it.isDependencyNotation -> {
                        val dep = dependencies.create(it) {}
                        ProjectOrGav.External(dep.group, dep.name, dep.version)
                    }
                    else -> ProjectOrGav.Sibling(it)
                }
            )
        }
    }

    /** Libraries that this Gradle plugin adds to its users */
    public val companionLibraries: ListProperty<String> = objects.listProperty()

    /**
     * Indicate that [project] is used within `functionalTest` suite. By default, includes
     * [compilerPlugin] and [companionLibraries].
     */
    public fun functionalTestProject(project: Project) {
        // functionalTestRuntimeOnly is automatically pulled in by installForFunctionalTest
        // User might reflectively call methods from Gradle output, so we need these projects
        // on our runtime classpath
        val path = project.isolated.path
        dependencies { functionalTest.runtimeOnlyConfigurationName(project(path)) }
    }

    private val functionalTestProjectsClasspath:
        NamedDomainObjectProvider<DependencyScopeConfiguration> =
        configurations.dependencyScope("functionalTestProjectsClasspath")

    override fun companionLibrary(project: Project, group: Any?, version: Any?) {
        companionLibraries.add(
            siblingDependencyGav(project.isolated.name, group?.toString(), version?.toString())
        )
        functionalTestProject(project)
    }

    override fun companionLibrary(dependencyNotationOrSiblingName: String) {
        val dependencyNotation =
            when {
                dependencyNotationOrSiblingName.isIncludedBuildPath -> {
                    val name = Path(dependencyNotationOrSiblingName).name
                    includedBuildProjects.add(name)
                    siblingDependencyGav(name)
                }
                dependencyNotationOrSiblingName.isDependencyNotation ->
                    provider { dependencyNotationOrSiblingName }
                else -> siblingDependencyGav(dependencyNotationOrSiblingName)
            }
        companionLibraries.add(dependencyNotation)
        dependencies { functionalTest.runtimeOnlyConfigurationName(dependencyNotation) }
    }

    /**
     * Lists the included builds that should be passed to
     * [GradleTestKitSupportExtension.withIncludedBuildProjects]. The test-kit extension only allows
     * setting this once, so we collect them and only set them in [Project.afterEvaluate]
     */
    public val includedBuildProjects: ListProperty<String> = objects.listProperty()

    init {
        registerCompanionLibraries()
    }

    /**
     * Whether to add an implementation dependency on `gradle-plugin-runtime`. Plugins that don't
     * use its APIs can turn this off to avoid an unused dependency leaking into their published
     * POM.
     */
    public val addRuntimeDependency: Property<Boolean> = objects.property(true)

    /**
     * The Kotlin versions to run the `functionalTest` suite against, one task per version. By
     * default, it includes the Kotlin version applied to this build, everything listed in the
     * [DevKitPropertiesDsl] properties.
     */
    public override val testAgainst: NamedDomainObjectCollection<DevKitGradlePluginTestTarget> =
        objects.domainObjectContainer(DevKitGradlePluginTestTarget::class)

    init {
        registerSupportedVersions()
    }

    override fun testAgainst(
        version: KotlinToolingVersion,
        forIde: Boolean,
        action: DevKitGradlePluginTestTarget.() -> Unit,
    ): DevKitGradlePluginTestTarget? {
        if (forIde) return null
        val target =
            testAgainst.findByName(version.toString())
                ?: DevKitGradlePluginTestTarget(
                        version,
                        tasks.register<KotlinJvmTest>("${version.versionName}FunctionalTest") {
                            targetName = version.toString()
                        },
                    )
                    .also(testAgainst::add)
        return target.apply(action)
    }

    /** properties to place inside gradle.properties for the `functionalTest` suite. */
    public val functionalTestProperties: ListProperty<String> = objects.listProperty<String>()

    /**
     * The highest Kotlin version every library in [companionLibraries] was built using. This is
     * used to filter KMP tests for lower versions because KLIBS are not backwards compatible.
     *
     * If there are any [companionLibraries] that come from included bulids, this defaults to the
     * minimum supported version, thus assuming that included builds are used for KLIB compatibility
     * Else, the current Kotlin bulid version is using, assuming that the libraries are built on it
     */
    public val companionLibrariesKotlinVersion: Property<KotlinToolingVersion> =
        objects.property(
            provider {
                // TODO work out using a more sophisticated method
                //  e.g. using kotlin-tooling-metadata.json
                if (includedBuildProjects().isNotEmpty()) testAgainst.minOf { it.version }
                else kotlinToolingVersion
            }
        )

    private fun configureDependencies() = dependencies {
        main.compileOnlyConfigurationName(kotlin("gradle-plugin-api"))
        functionalTestProjectsClasspath(
            compilerPlugin.map { plugin ->
                when (plugin) {
                    is Sibling,
                    is External ->
                        "${plugin.group}:${plugin.artifact}${plugin.version?.let { ":$it" }.orEmpty()}"
                    is LocalProject -> project(plugin.isolated.path)
                }
            }
        )

        main.implementationConfigurationName(
            addRuntimeDependency.ifTrue { "$DEV_KIT_GROUP:gradle-plugin-runtime:$DEV_KIT_VERSION" }
        )
        functionalTest.implementationConfigurationName(
            testFixtures("$DEV_KIT_GROUP:gradle-plugin-runtime:$DEV_KIT_VERSION")
        )
    }

    private fun configureTestTasks() {
        testAgainst.configureEach {
            testTask {
                val functionalTestSourceSet =
                    project.extensions.getByType<SourceSetContainer>().named(FUNCTIONAL_TEST)
                group = "verification"
                description = "Runs the functional tests against Kotlin $version."
                testClassesDirs = files(functionalTestSourceSet.map { it.output.classesDirs })
                classpath = files(functionalTestSourceSet.map { it.runtimeClasspath })
                dependsOn("deleteOldFuncTests", "installForFunctionalTest")
                mustRunAfter("test")
                maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                jvmArgs("-XX:+HeapDumpOnOutOfMemoryError", "-XX:MaxMetaspaceSize=1g")
                systemProperty("org.gradle.testkit.dir", project.gradle.gradleUserHomeDir)
                systemProperty("$DEV_KIT_PROPERTY_PREFIX.test.kotlin-version", version)
                +StringSystemPropertyArgumentProvider(
                    "$DEV_KIT_PROPERTY_PREFIX.build.kotlin-version",
                    companionLibrariesKotlinVersion.map { it.toString() },
                )
                +PropertiesSystemPropertyArgumentProvider(
                    "$DEV_KIT_PROPERTY_PREFIX.test.gradle-properties",
                    functionalTestProperties,
                )
                +NestedArgumentProvider(
                    (tasks.getByName(FUNCTIONAL_TEST) as Test)
                        .jvmArgumentProviders
                        // Hacky: pick up repo and included-build repos from functionalTest
                        .filter { it.javaClass.simpleName in FUNCTIONAL_TEST_ARGUMENT_PROVIDERS }
                )
                +OptionalStringSystemPropertyArgumentProvider(KMP_TARGET_PROPERTY, kmpTarget)
            }
        }
        val allVersionedFunctionalTests =
            tasks.register<DefaultTestTask>("allVersionedFunctionalTests") {
                description = "Run all functional tests."
                group = "verification"
                enabled = false
                testAgainst.configureEach { this@register.dependsOn(testTask) }
            }

        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(allVersionedFunctionalTests) }
        val defaultFunctionalTest =
            tasks.register<DefaultTestTask>("defaultFunctionalTest") {
                enabled = false
                dependsOn(defaultTestTarget.map { it.testTask })
            }
        tasks.named<Test>(FUNCTIONAL_TEST) {
            enabled = false
            classpath = files()
            testClassesDirs = files()
            dependsOn(defaultFunctionalTest)
            // hide task
            group = null
        }
    }

    private fun configurePlugins() {
        gradleTestKitSupport.apply {
            withClasspaths(functionalTestProjectsClasspath.name)
            // TODO find another way to allow user to configure included builds here without
            //  afterEvaluate
            project.afterEvaluate {
                with(ProviderFlatMap.Unsafe) {
                    val includedBuildProjects = includedBuildProjects()
                    if (includedBuildProjects.isNotEmpty())
                        withIncludedBuildProjects(
                            *includedBuildProjects.map { ":$it:" }.toTypedArray()
                        )
                }
            }
        }

        buildConfig {
            packageName.convention(pluginPackage)
            forClass("PluginInfo") {
                useKotlinOutput { topLevelConstants = false }
                val pluginId =
                    buildConfigField("PLUGIN_ID") {
                        type(String::class)
                        expression(pluginPackage.map { "\"$it\"" })
                    }
                val pluginArtifact =
                    buildConfigField("PLUGIN_ARTIFACT") {
                        type("org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact")
                        expression(
                            compilerPlugin.map {
                                """
                                    |SubpluginArtifact(
                                    |  groupId = "${it.group}",
                                    |  artifactId = "${it.artifact}",
                                    |  version = "${it.version}",
                                    |)
                                """
                                    .trimMargin()
                            }
                        )
                    }
                val companionLibraryCoordinates =
                    buildConfigField("COMPANION_LIBRARY_COORDINATES") {
                        type("List<String>")
                        expression(
                            companionLibraries.map { library ->
                                library.joinToString(", ", "listOf(", ")") { "\"$it\"" }
                            }
                        )
                    }
                buildConfigField("PLUGIN_INFO") {
                    type.value(
                        provider {
                            if (addRuntimeDependency()) "$DEV_KIT_GROUP.DevKitPluginInfo"
                            else "kotlin.Nothing"
                        }
                            .map { BuildConfigType(it) }
                    )
                    expression(
                        provider {
                            if (
                                addRuntimeDependency() &&
                                    compilerPlugin.isPresent &&
                                    companionLibraries.isPresent
                            )
                                """
                                |DevKitPluginInfo(
                                |  id = ${pluginId.name},
                                |  artifact = ${pluginArtifact.name},
                                |  companionLibraryCoordinates = ${companionLibraryCoordinates.name},
                                |)
                                """
                                    .trimMargin()
                            else ""
                        }
                            .filter { it.isNotEmpty() }
                    )
                }
            }
        }
    }

    internal fun configure() {
        configureDependencies()
        configureTestTasks()
        configurePlugins()
    }
}

public class DevKitGradlePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        // TODO should we apply SAM and assign plugins?
        pluginManager.apply {
            apply("org.jetbrains.kotlin.jvm")
            apply("com.github.gmazzo.buildconfig")
            apply("java-gradle-plugin")
            apply("$DEV_KIT_GROUP.functional-test-publishing")
        }

        val pluginDevKit =
            project.extensions.create<GradlePluginConfigExtension>("pluginDevKit", project)
        pluginDevKit.configure()
    }
}
