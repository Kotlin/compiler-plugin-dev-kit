package org.jetbrains.kotlin.compiler.plugin.devkit

import com.autonomousapps.GradleTestKitSupportExtension
import com.github.gmazzo.buildconfig.BuildConfigExtension
import com.github.gmazzo.buildconfig.BuildConfigType
import java.io.File
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.project.IsolatedProject
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.domainObjectContainer
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.util.Path
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.js.KotlinWasmTargetAttribute
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

// GradleTestKitSupportExtension doesn't expose them publicly
private const val FUNCTIONAL_TEST = "functionalTest"
private const val INSTALL_FOR_FUNCTIONAL_TEST = "installForFunctionalTest"
private const val FUNCTIONAL_TEST_REPO_DIR = "build/functionalTestRepo"

// Testkit's argument providers are internal, so match them by simple name
private const val REPO_AND_VERSION_ARGUMENT_PROVIDER = "RepoAndVersionArgumentProvider"
private const val TESTKIT_INCLUDE_BUILD_REPOS_PROVIDER = "IncludeBuildReposArgumentProvider"

private const val INCLUDED_BUILD_REPOS_SYSTEM_PROPERTY =
    "com.autonomousapps.plugin-under-test.repos-included"

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

    private val companionLibrariesClasspath =
        configurations.dependencyScope("companionLibrariesClasspath")

    private val companionKlibViews: List<Configuration> =
        listOf(
            companionKlibs("companionLibrariesJs") {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
                attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
            },
            companionKlibs("companionLibrariesWasmJs") {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
                attribute(KotlinPlatformType.attribute, KotlinPlatformType.wasm)
                attribute(
                    KotlinWasmTargetAttribute.wasmTargetAttribute,
                    KotlinWasmTargetAttribute.js,
                )
            },
            companionKlibs("companionLibrariesNativeHost") {
                // Native klibs are published under `kotlin-api` and per konan target
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_API))
                attribute(KotlinPlatformType.attribute, KotlinPlatformType.native)
                attribute(KotlinNativeTarget.konanTargetAttribute, hostKonanTarget())
            },
        )

    /**
     * The klibs of [companionLibraries]. [companionLibrariesKotlinVersion] reads the compiler
     * version out of them.
     *
     * Each view is lenient.
     */
    private val companionLibraryKlibs: FileCollection =
        objects
            .fileCollection()
            .from(
                companionKlibViews.map { view ->
                    view.incoming.artifactView { lenient(true) }.files
                }
            )

    /**
     * The JVM runtime variants of [companionLibraries]. [detectedIncludedBuildProjects] also reads
     * this view.
     */
    private val companionLibrariesJvm: Configuration =
        configurations
            .resolvable("companionLibrariesJvm") {
                extendsFrom(companionLibrariesClasspath)
                extendsFrom(configurations.named(functionalTest.runtimeClasspathConfigurationName))
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                }
            }
            .get()

    /**
     * The included build projects that [companionLibraries] resolve to, as (build path, project
     * path) pairs of [Path]s. The build path is relative to this build, for example `("lib",
     * ":sub")`.
     */
    private val detectedIncludedBuildProjects: Provider<List<Pair<Path, Path>>> = provider {
        val views = companionKlibViews + listOf(companionLibrariesJvm)
        val ownBuildPath =
            Path.path(
                (views.first().incoming.resolutionResult.root.id as? ProjectComponentIdentifier)
                    ?.build
                    ?.buildPath ?: ":"
            )
        views
            .flatMap { view -> view.incoming.resolutionResult.allComponents }
            .mapNotNull { component ->
                val id = component.id as? ProjectComponentIdentifier ?: return@mapNotNull null
                if (!component.selectionReason.isCompositeSubstitution) return@mapNotNull null
                // the resolution names builds relative to the composite's root, so subtract
                // this build's own path to name a directly included build like
                // Gradle.getIncludedBuild does. A path outside this build's subtree stays
                // absolute, so it can never collide with the name of a build this build
                // includes itself.
                ownBuildPath.relativePath(Path.path(id.build.buildPath)) to
                    Path.path(id.projectPath)
            }
            .distinct()
    }

    /** The functional test repository of every included build, keyed by build name. */
    private val includedBuildRepoDirs: Map<String, String> =
        project.gradle.includedBuilds.associate { build ->
            build.name to File(build.projectDir, FUNCTIONAL_TEST_REPO_DIR).absolutePath
        }

    private fun companionKlibs(
        name: String,
        attributes: AttributeContainer.() -> Unit,
    ): Configuration =
        configurations
            .resolvable(name) {
                extendsFrom(companionLibrariesClasspath)
                attributes { attributes() }
            }
            .get()

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
        dependencies { companionLibrariesClasspath(project(project.isolated.path)) }
    }

    override fun companionLibrary(dependencyNotationOrSiblingName: String) {
        val dependencyNotation =
            if (dependencyNotationOrSiblingName.isDependencyNotation) {
                provider { dependencyNotationOrSiblingName }
            } else {
                siblingDependencyGav(dependencyNotationOrSiblingName)
            }
        companionLibraries.add(dependencyNotation)
        dependencies {
            functionalTest.runtimeOnlyConfigurationName(dependencyNotation)
            companionLibrariesClasspath(dependencyNotation)
        }
    }

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

    override val companionLibrariesKotlinVersion: Property<KotlinToolingVersion> =
        objects.property(
            provider {
                companionLibraryKlibs.files.mapNotNull(::klibCompilerVersion).maxOrNull()
                    ?: testAgainst.minOf { it.version }
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
                val functionalTestTask = tasks.getByName<Test>(FUNCTIONAL_TEST)
                // Hacky: testkit publishes none of its argument providers, so read them off
                // its own test task
                +NestedArgumentProvider(
                    functionalTestTask.jvmArgumentProviders.filter {
                        it.javaClass.simpleName == REPO_AND_VERSION_ARGUMENT_PROVIDER
                    }
                )
                +OptionalStringSystemPropertyArgumentProvider(
                    INCLUDED_BUILD_REPOS_SYSTEM_PROPERTY,
                    includedBuildReposPropertyValue(functionalTestTask),
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

    /**
     * The repositories of [detectedIncludedBuildProjects] plus the ones registered with
     * [GradleTestKitSupportExtension.withIncludedBuildProjects].
     */
    private fun includedBuildReposPropertyValue(functionalTestTask: Test): Provider<String> =
        provider {
            val testKitRepos =
                functionalTestTask.jvmArgumentProviders
                    .filter { it.javaClass.simpleName == TESTKIT_INCLUDE_BUILD_REPOS_PROVIDER }
                    .flatMap { it.asArguments() }
                    .filter { it.startsWith("-D$INCLUDED_BUILD_REPOS_SYSTEM_PROPERTY=") }
                    .flatMap {
                        it.removePrefix("-D$INCLUDED_BUILD_REPOS_SYSTEM_PROPERTY=").split(",")
                    }
            val detectedRepos =
                detectedIncludedBuildProjects().mapNotNull { (buildPath, _) ->
                    // the install task warns about every build without a repo here: one
                    // outside this build's subtree is absolute, and a nested included build
                    // has several segments
                    if (buildPath.isAbsolute || buildPath.segmentCount() > 1) null
                    else includedBuildRepoDirs[buildPath.asString()]
                }
            (testKitRepos + detectedRepos).distinct().takeIf { it.isNotEmpty() }?.joinToString(",")
        }

    private fun configurePlugins() {
        gradleTestKitSupport.apply {
            withClasspaths(functionalTestProjectsClasspath.name)
        }

        tasks.named(INSTALL_FOR_FUNCTIONAL_TEST) {
            dependsOn(
                detectedIncludedBuildProjects.map { detected ->
                    detected.mapNotNull { (buildPath, projectPath) ->
                        when {
                            buildPath.isAbsolute -> {
                                // the build is outside this build's subtree
                                project.logger.warn(
                                    """
                                        |Builds this build doesn't include can't be accessed in Gradle,
                                        |so the companion library at '$buildPath$projectPath' can't be
                                        |automatically published for the functional tests.
                                        |Please include its build directly!"""
                                        .trimMargin()
                                )
                                null
                            }
                            buildPath.segmentCount() > 1 -> {
                                // nested included builds aren't accessible
                                project.logger.warn(
                                    """
                                        |Nested included builds cannot be accessed in Gradle,
                                        |so the companion library at '$buildPath$projectPath' can't be
                                        |automatically published for the functional tests.
                                        |Please include its build directly!"""
                                        .trimMargin()
                                )
                                null
                            }
                            else ->
                                project.gradle
                                    .includedBuild(buildPath.asString())
                                    .task(projectPath.child(INSTALL_FOR_FUNCTIONAL_TEST).asString())
                        }
                    }
                }
            )
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

/**
 * The konan name of the host's Kotlin/Native target, e.g. `macos_arm64`, matching the
 * [KotlinNativeTarget.konanTargetAttribute] klib variants are published per. Kotlin/Native compiles
 * no other target without a cross-compilation toolchain, so only the host's klib can ever be
 * compiled, by the functional tests or anywhere else in this build.
 */
private fun hostKonanTarget(): String {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val arch = System.getProperty("os.arch").orEmpty().lowercase()
    val isArm64 = arch == "aarch64" || arch == "arm64"
    return when {
        os.contains("mac") || os.contains("darwin") -> if (isArm64) "macos_arm64" else "macos_x64"
        os.contains("windows") -> "mingw_x64"
        os.contains("linux") -> if (isArm64) "linux_arm64" else "linux_x64"
        else -> "linux_x64"
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
