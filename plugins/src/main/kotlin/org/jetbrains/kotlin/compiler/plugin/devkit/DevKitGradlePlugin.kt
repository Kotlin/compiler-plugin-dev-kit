package org.jetbrains.kotlin.compiler.plugin.devkit

import com.autonomousapps.GradleTestKitSupportExtension
import com.github.gmazzo.buildconfig.BuildConfigExtension
import com.github.gmazzo.buildconfig.BuildConfigType
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.GradleInternal
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.polymorphicDomainObjectContainer
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

/** A single Kotlin version that the consuming Gradle plugin's functional tests run against. */
public interface DevKitGradlePluginTestTarget : Named {
    override fun getName(): String = version.versionString

    public val version: KotlinToolingVersion
    public val testTask: TaskProvider<Test>
}

private class DevKitGradlePluginTestTargetImpl(
    override val version: KotlinToolingVersion,
    override val testTask: TaskProvider<Test>,
) : DevKitGradlePluginTestTarget {
    override fun equals(other: Any?): Boolean =
        other is DevKitGradlePluginTestTarget && other.version == version

    override fun hashCode(): Int = version.hashCode()
}

// GradleTestKitSupportExtension doesn't expose it publicly
private const val FUNCTIONAL_TEST = "functionalTest"

private val FUNCTIONAL_TEST_ARGUMENT_PROVIDERS =
    setOf("RepoAndVersionArgumentProvider", "IncludeBuildReposArgumentProvider")

public open class GradlePluginConfigExtension(override val project: Project) : HasProject {
    private val buildConfig: BuildConfigExtension = project.the()
    private val gradleTestKitSupport: GradleTestKitSupportExtension = project.the()
    private val sourceSets: SourceSetContainer = project.the()
    private val main = sourceSets.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
    private val functionalTest = sourceSets.getByName(FUNCTIONAL_TEST)

    private val kmpTarget = providers.gradleProperty(KMP_TARGET)

    /**
     * The package name of this Gradle plugin. Defaults to [PLUGIN_PACKAGE_PROPERTY], else the
     * project group.
     */
    public val pluginPackage: Property<String> =
        objects.property(
            providers
                .gradleProperty(PLUGIN_PACKAGE_PROPERTY)
                .orElse(provider { project.group.toString() })
        )

    /**
     * The compiler plugin project that this Gradle plugin applies. Defaults to the project at the
     * path in [COMPILER_PLUGIN_PROPERTY], if that is set.
     */
    public val compilerPlugin: Property<Project> = objects.property()

    init {
        providers.gradleProperty(COMPILER_PLUGIN_PROPERTY).orNull?.let {
            compilerPlugin.convention(project.project(it))
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

    /** Adds a library to this Gradle plugin's users */
    public fun companionLibrary(companion: Project, group: Any? = null, version: Any? = null) {
        val group = provider { group ?: project.group }
        val version = provider { version ?: project.version }
        companionLibraries.add(provider { "${group()}:${companion.isolated.name}:${version()}" })
        functionalTestProject(companion)
    }

    /** Adds a library to this Gradle plugin's users */
    public fun companionLibrary(dependencyNotation: String) {
        companionLibraries.add(dependencyNotation)
    }

    init {
        listFromGradleProperty(COMPANION_LIBRARIES_PROPERTY).forEach {
            if (it.isProjectPath) companionLibrary(project.project(it)) else companionLibrary(it)
        }
    }

    /**
     * Whether to add an implementation dependency on `gradle-plugin-runtime`. Plugins that don't
     * use its APIs can turn this off to avoid an unused dependency leaking into their published
     * POM.
     */
    public val addRuntimeDependency: Property<Boolean> = objects.property(true)

    /**
     * The Kotlin versions to run the `functionalTest` suite against, one task per version. By
     * default, it includes the Kotlin version applied to this build and everything listed in
     * [TEST_AGAINST_PROPERTY].
     */
    public val testAgainst:
        ExtensiblePolymorphicDomainObjectContainer<DevKitGradlePluginTestTarget> =
        objects.polymorphicDomainObjectContainer(DevKitGradlePluginTestTarget::class)

    init {
        testAgainst.registerFactory(DevKitGradlePluginTestTarget::class.java) {
            val version = KotlinToolingVersion(it)
            DevKitGradlePluginTestTargetImpl(
                version,
                tasks.register<Test>("${version.versionName}FunctionalTest"),
            )
        }
        testAgainst(kotlinToolingVersion)
        listFromGradleProperty(TEST_AGAINST_PROPERTY).forEach { testAgainst(it) }
    }

    /**
     * Registers a new Kotlin version to run the `functionalTest` suite against. This operation is
     * idempotent.
     */
    public fun testAgainst(
        version: KotlinToolingVersion,
        action: DevKitGradlePluginTestTarget.() -> Unit = {},
    ): DevKitGradlePluginTestTarget = testAgainst(version.versionString, action)

    /**
     * Registers a new Kotlin version to run the `functionalTest` suite against. This operation is
     * idempotent.
     */
    public fun testAgainst(
        version: String,
        action: DevKitGradlePluginTestTarget.() -> Unit = {},
    ): DevKitGradlePluginTestTarget = testAgainst.maybeCreate(version).apply(action)

    /**
     * Which registered [testAgainst] version the generic `functionalTest` task delegates to.
     * Defaults to [DEFAULT_TEST_VERSION_PROPERTY], or to the Kotlin Gradle Plugin version currently
     * applied to this build if that property isn't set. If no [testAgainst] entry matches,
     * `functionalTest` is left alone.
     */
    public val defaultTestTarget: Property<DevKitGradlePluginTestTarget> =
        objects.property(
            testAgainst(
                providers
                    .gradleProperty(DEFAULT_TEST_VERSION_PROPERTY)
                    .getOrElse(kotlinToolingVersion.versionString)
            )
        )

    /** The default Kotlin version to run the `functionalTest` suite against. */
    public fun defaultTestVersion(version: KotlinToolingVersion) {
        defaultTestVersion(version.versionString)
    }

    /** The default Kotlin version to run the `functionalTest` suite against. */
    public fun defaultTestVersion(version: String) {
        defaultTestTarget.set(testAgainst.named(version))
    }

    /**
     * properties to place inside gradle.properties for the `functionalTest` suite. By default,
     * it'll try to add your `devKitSpace` credentials.
     */
    public val functionalTestProperties: ListProperty<String> = objects.listProperty<String>()

    init {
        val settings = (project.gradle as GradleInternal).settings
        // Very adhoc, but only needed for development
        val devKitRepo =
            settings.dependencyResolutionManagement.repositories
                .withType<MavenArtifactRepository>()
                .matching {
                    it.url.path.contains("compiler-plugin-dev-kit")
                }
        functionalTestProperties.addAll(
            provider {
                val credentials =
                    devKitRepo.singleOrNull()?.credentials ?: return@provider emptyList()
                listOf(
                    "devKitSpaceUsername=${credentials.username}",
                    "devKitSpacePassword=${credentials.password}",
                )
            }
        )
    }

    public companion object {
        /**
         * Property that's passed to each `functionalTest` to specify which KMP target to run
         * against.
         */
        public const val KMP_TARGET: String = "$DEV_KIT_GROUP.gradle.test.kmpTarget"
    }

    private fun configureDependencies() = dependencies {
        main.compileOnlyConfigurationName(kotlin("gradle-plugin-api"))
        functionalTestProjectsClasspath(compilerPlugin.map { project(it.isolated.path) })

        addIf(
            addRuntimeDependency,
            main.implementationConfigurationName,
            "$DEV_KIT_GROUP:gradle-plugin-runtime:$DEV_KIT_VERSION",
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
                description = "Runs the functional tests against Kotlin ${version.versionString}."
                testClassesDirs = files(functionalTestSourceSet.map { it.output.classesDirs })
                classpath = files(functionalTestSourceSet.map { it.runtimeClasspath })
                dependsOn("deleteOldFuncTests", "installForFunctionalTest")
                mustRunAfter("test")
                maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                jvmArgs("-XX:+HeapDumpOnOutOfMemoryError", "-XX:MaxMetaspaceSize=1g")
                systemProperty("$DEV_KIT_GROUP.gradle.test.kotlin-version", version.versionString)
                +PropertiesSystemPropertyArgumentProvider(
                    "$DEV_KIT_GROUP.gradle.test.properties",
                    functionalTestProperties,
                )
                +NestedArgumentProvider(
                    (tasks.getByName(FUNCTIONAL_TEST) as Test)
                        .jvmArgumentProviders
                        // Hacky: pick up repo and included-build repos from functionalTest
                        .filter { it.javaClass.simpleName in FUNCTIONAL_TEST_ARGUMENT_PROVIDERS }
                )
                +OptionalStringSystemPropertyArgumentProvider(KMP_TARGET, kmpTarget)
            }
        }
        val allVersionedFunctionalTests =
            tasks.register<Test>("allVersionedFunctionalTests") {
                description = "Run all functional tests."
                group = "verification"
                enabled = false
                testAgainst.configureEach { this@register.dependsOn(testTask) }
            }

        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(allVersionedFunctionalTests) }
    }

    private fun configurePlugins() {
        tasks.named<Test>(FUNCTIONAL_TEST) {
            enabled = false
            classpath = files()
            testClassesDirs = files()
            dependsOn(defaultTestTarget.map { it.testTask })
        }

        gradleTestKitSupport.apply {
            // TODO this definitely violates Isolated Projects. DevKit should instead
            //  publish needed dependencies into functionalTestRepo itself
            withClasspaths(functionalTestProjectsClasspath.name)
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
                                |  groupId = "${project.group}",
                                |  artifactId = "${it.isolated.name}",
                                |  version = "${project.version}",
                                |)
                                """
                                    .trimMargin()
                            }
                        )
                    }
                val extraLibraryCoordinates =
                    buildConfigField("EXTRA_LIBRARY_COORDINATES") {
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
                                |  extraLibraryCoordinates = ${extraLibraryCoordinates.name},
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
            apply(DevKitFunctionalTestPublishingPlugin::class.java)
        }

        val pluginDevKit =
            project.extensions.create<GradlePluginConfigExtension>("pluginDevKit", project)
        pluginDevKit.configure()
    }
}
