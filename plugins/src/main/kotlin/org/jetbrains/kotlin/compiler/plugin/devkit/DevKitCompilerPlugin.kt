@file:OptIn(ExperimentalContracts::class)
@file:Suppress("FINAL_UPPER_BOUND")

package org.jetbrains.kotlin.compiler.plugin.devkit

import com.github.gmazzo.buildconfig.BuildConfigExtension
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import java.io.File
import javax.inject.Inject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import org.gradle.api.DomainObjectSet
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.dsl.GradleDependencies
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Usage
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.component.external.model.TestFixturesSupport
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaCompilation
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension
import ru.vyarus.gradle.plugin.animalsniffer.signature.BuildSignatureTask

public typealias SourceSetProvider = NamedDomainObjectProvider<out KotlinSourceSet>

internal typealias JvmCompilation = KotlinWithJavaCompilation<*, *>

internal typealias CompilationProvider = NamedDomainObjectProvider<out JvmCompilation>

@Suppress("FINAL_UPPER_BOUND", "unused")
public class DevKitTestTargetT<out Fixtures : Unit, out Main : Unit>
internal constructor(
    public val version: KotlinToolingVersion,
    public val testSuite: NamedDomainObjectProvider<JvmTestSuite>,
    internal val fixturesCompilation: CompilationProvider,
    internal val fixturesFrom: Property<DevKitTestFixturesTarget>,
    makeMain: () -> CompilationProvider,
) : Named {
    override fun getName(): String = version.versionString

    public val versionName: String
        get() = version.versionName

    /**
     * Compiles this target's testFixtures from [other]'s overlay, for versions whose test framework
     * APIs are pretty similar, so one can use another's overlay.
     *
     * A target sources from exactly one other target: calling this again replaces which one, and
     * [other] may not be this target itself. Only needed when the target either owns an overlay
     * (see [DevKitCompilerPluginConfigExtension.withFixtures]) or is closest to the wrong one.
     */
    public fun sourceFixturesFrom(other: DevKitTestFixturesTarget) {
        require(this != other) { "$name cannot source its testFixtures from itself" }
        fixturesFrom.set(other)
    }

    internal val lazyMain: CompilationProvider by lazy(makeMain)

    override fun equals(other: Any?): Boolean =
        other is DevKitTestTarget && other.version == version

    override fun hashCode(): Int = version.hashCode()
}

public typealias DevKitTestTarget = DevKitTestTargetT<*, *>

public typealias DevKitMainTarget = DevKitTestTargetT<*, Nothing>

public typealias DevKitTestFixturesTarget = DevKitTestTargetT<Nothing, *>

public typealias DevKitTarget = DevKitTestTargetT<Nothing, Nothing>

public interface DevKitTestDataLibrary : GradleDependencies {
    /** Dependency collector for all platforms */
    public val common: DependencyCollector

    /** Dependency collector for JVM */
    public val jvm: DependencyCollector

    /** Dependency collector for JS */
    public val js: DependencyCollector
}

public interface DevKitTestDataLibraries : DevKitTestDataLibrary {
    /**
     * Registers a library for test data.
     *
     * You should use [DevKitCompilerPluginConfigExtension.testDataLibraries].[common], or one of
     * its platform-specific collectors, if you want to register a library for all test data.
     *
     * This provides a convenient way of registering a conditionally available library for test
     * data. You should use `configureTestDataLibrary` in your `testFixtures`, providing the
     * `condition` for the library to be available.
     *
     * The library will be passed to tests under the property "[name].[jvm|js].classpath". if
     * [addToBuildConfig], it'll also be available under TestDataLibraries.[name].
     *
     * This is *not* idempotent.
     */
    public fun register(
        name: String,
        isTransitive: Boolean = false,
        addToBuildConfig: Boolean = true,
        block: DevKitTestDataLibrary.() -> Unit = {},
    ): DevKitTestDataLibrary
}

private abstract class DevKitTestDataLibrariesImpl
@Inject
constructor(private val pluginDevKit: DevKitCompilerPluginConfigExtension) :
    DevKitTestDataLibraries {
    override fun register(
        name: String,
        isTransitive: Boolean,
        addToBuildConfig: Boolean,
        block: DevKitTestDataLibrary.() -> Unit,
    ): DevKitTestDataLibrary =
        pluginDevKit.project.objects.newInstance<DevKitTestDataLibrary>().apply {
            pluginDevKit.register(this, name, isTransitive, addToBuildConfig)
            block()
        }
}

private fun DevKitTestTarget.testTask(action: Test.() -> Unit) = testSuite {
    targets.all { this.testTask(action) }
}

context(kotlinExtension: KotlinJvmProjectExtension)
private fun DevKitTestTarget.test(action: KotlinSourceSet.() -> Unit) = testCompilation {
    defaultSourceSet(action)
}

context(kotlinExtension: KotlinJvmProjectExtension)
private fun DevKitTestTarget.testCompilation(action: JvmCompilation.() -> Unit) = testSuite {
    sources { kotlinExtension.target.compilations.getByName(this@sources.name).action() }
}

/** Adds every source directory of [other] to this source set, or none if [other] has no value. */
private fun JvmCompilation.srcDirsFrom(other: Provider<out JvmCompilation>) {
    val empty = files()
    defaultSourceSet {
        kotlin.srcDir(other.defaultSourceSet.map { it.kotlin.sourceDirectories }.orElse(empty))
        resources.srcDir(
            other.defaultSourceSet.map { it.resources.sourceDirectories }.orElse(empty)
        )
    }
    javaSourceSet.java.srcDir(other.map { it.javaSourceSet.java.sourceDirectories }.orElse(empty))
}

private fun JvmCompilation.srcDirsFrom(other: JvmCompilation) = srcDirsFrom(provider { other })

@OptIn(ExperimentalWasmDsl::class)
public open class DevKitCompilerPluginConfigExtension
internal constructor(override val project: Project) : HasProject {
    private val buildConfig: BuildConfigExtension = project.the()
    private val testing: TestingExtension = project.the()
    private val kotlinExtension: KotlinJvmProjectExtension = project.the()
    private val idea: IdeaModel = project.the()
    private val d8EnvSpec: D8EnvSpec = project.the()

    private val compilations = kotlinExtension.target.compilations
    private val sharedMain = compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
    private val sharedTest = compilations.getByName(KotlinCompilation.TEST_COMPILATION_NAME)

    private val sharedTestFixtures =
        compilations.getByName(TestFixturesSupport.TEST_FIXTURE_SOURCESET_NAME)

    private val mainJar = tasks.named<Jar>(kotlinExtension.target.artifactsTaskName)
    private val testFixturesJar = tasks.named<Jar>(sharedTestFixtures.javaSourceSet.jarTaskName)

    /**
     * The package used by convention for BuildConfig generation and where [generateTestsClass]
     * resides. It's also assumed to be the plugin ID. Defaults to [PLUGIN_PACKAGE_PROPERTY], else
     * [Project.getGroup]
     */
    public val pluginPackage: Property<String> =
        objects.property(
            providers
                .gradleProperty(PLUGIN_PACKAGE_PROPERTY)
                .orElse(provider { project.group.toString() })
        )

    /**
     * The name of the class within testFixtures that generates tests. Defaults to
     * group.GenerateTestsKt
     */
    public val generateTestsClass: Property<String> =
        objects.property(pluginPackage.map { "$it.GenerateTestsKt" })

    /** The directory where generated tests are placed. Defaults to build/test-gen */
    public val generatedTestsDir: DirectoryProperty =
        objects.directoryProperty().convention(layout.buildDirectory.dir("test-gen"))

    /**
     * Whether to add an implementation dependency on `compiler-plugin-runtime`. Defaults to true
     */
    public val addRuntimeDependency: Property<Boolean> = objects.property(true)

    /**
     * Versions that this plugin should be tested against. By default, it includes the Kotlin
     * version applied to this build and everything listed in [TEST_AGAINST_PROPERTY].
     *
     * @see [developFor], [testAgainstWithFixtures], [developForWithFixtures]
     */
    public val testAgainst: ExtensiblePolymorphicDomainObjectContainer<DevKitTestTarget> =
        objects.polymorphicDomainObjectContainer(DevKitTestTarget::class.java).apply {
            registerFactory(DevKitTestTarget::class.java) { versionString ->
                val version = KotlinToolingVersion(versionString)
                DevKitTestTargetT<Nothing, Nothing>(
                    version,
                    testing.suites.register<JvmTestSuite>("${version.versionName}Test"),
                    compilations.register("${version.versionName}TestFixtures"),
                    objects.property(
                        provider {
                            val candidates = withFixtures.filterNot { it.version == version }
                            val resolved = version.resolveAmong(candidates.map { it.version })
                            candidates.firstOrNull { it.version == resolved }
                        }
                    ),
                ) {
                    compilations.register(version.versionName)
                }
            }
        }

    init {
        // The shared source sets compile against the build's own Kotlin version, so that version
        // always needs a target, whatever the default test target ends up being.
        testAgainst(kotlinToolingVersion)
        listFromGradleProperty(TEST_AGAINST_PROPERTY).forEach { testAgainst(it) }
    }

    /**
     * Which registered [testAgainst] version the generic `test` task delegates to. Defaults to
     * [DEFAULT_TEST_VERSION_PROPERTY], or to the Kotlin version applied to this build if that
     * property isn't set.
     *
     * @see [defaultTestVersion] to set this using a version instead
     */
    public val defaultTestTarget: Property<DevKitTestTarget> =
        objects.property(
            testAgainst(
                providers
                    .gradleProperty(DEFAULT_TEST_VERSION_PROPERTY)
                    .getOrElse(kotlinToolingVersion.versionString)
            )
        )

    /**
     * Sets the [testAgainst] version the generic `test` task delegates to. Defaults to the Kotlin
     * version applied to this build. This is identical to setting [defaultTestTarget] with the
     * target corresponding to [version]. You can change this version multiple times.
     *
     * @see [defaultTestTarget] if you have a reference to the [DevKitTestTarget] already
     */
    public fun defaultTestVersion(version: KotlinToolingVersion) {
        defaultTestVersion(version.versionString)
    }

    /**
     * Sets the [testAgainst] version the generic `test` task delegates to. Defaults to the Kotlin
     * version applied to this build. This is identical to setting [defaultTestTarget] with the
     * target corresponding to [version]. You can change this version multiple times.
     *
     * @see [defaultTestTarget] if you have a reference to the [DevKitTestTarget] already
     */
    public fun defaultTestVersion(version: String) {
        defaultTestTarget.set(testAgainst.named(version))
    }

    /**
     * Versions that own a `main` overlay.
     *
     * @see [testAgainst], [withFixtures], [testAgainstWithFixtures], [developForWithFixtures]
     */
    public val developFor: NamedDomainObjectSet<DevKitMainTarget> =
        objects.namedDomainObjectSet(DevKitMainTarget::class.java)

    /**
     * Versions that own a `testFixtures` overlay. Every other version reuses the closest of these.
     *
     * @see [testAgainst], [developFor], [testAgainstWithFixtures], [developForWithFixtures]
     */
    public val withFixtures: NamedDomainObjectSet<DevKitTestFixturesTarget> =
        objects.namedDomainObjectSet(DevKitTestFixturesTarget::class.java)

    /** The (lazily configured) testFixtures source set for this target. */
    public val DevKitTestFixturesTarget.testFixtures: SourceSetProvider
        get() = kotlinExtension.run { fixturesCompilation.defaultSourceSet }

    private val DevKitMainTarget.mainCompilation: CompilationProvider
        get() = lazyMain

    /** The (lazily configured) main source set for this target. */
    public val DevKitMainTarget.main: SourceSetProvider
        get() = kotlinExtension.run { mainCompilation.defaultSourceSet }

    /** Creates a JS Repl for use by the testing framework */
    public val createJsRepl: TaskProvider<JsReplGeneratorTask> =
        tasks.register("createJsRepl", JsReplGeneratorTask::class) {
            description = "Creates a JS Repl for use by the testing framework"
            defaultRepl.convention(javaClass.classLoader.getResource("repl.js")?.readText())
            outputFile = layout.buildDirectory.file("js-test-repl/repl.js").get().asFile
        }
    /**
     * Generates tests using [generateTestsClass], given the relevant test data directory, and
     * outputs them to [generatedTestsDir]
     */
    public val generateTests: TaskProvider<GenerateTestsTask> = kotlinExtension.run {
        tasks.register("generateTests", GenerateTestsTask::class) {
            description = "Generate compiler plugin tests"
            generatedTestsDir.set(this@DevKitCompilerPluginConfigExtension.generatedTestsDir)

            classpath(sharedTestFixtures.runtimeDependencyFiles, sharedTestFixtures.output)
            mainClass.set(generateTestsClass)
            workingDir = rootDir

            testAgainst.configureEach {
                test {
                    argFiles(resources.sourceDirectories, generatedTestsDir.dir(name))
                }
            }
        }
    }

    init {
        testAgainst.configureEach {
            kotlinExtension.run {
                testCompilation { compileJavaTaskProvider { dependsOn(generateTests) } }
            }
        }
    }

    /** Libraries that all test data files can use, including [COMPANION_LIBRARIES_PROPERTY]'s */
    public val testDataLibraries: DevKitTestDataLibraries =
        objects.newInstance<DevKitTestDataLibrariesImpl>(this).also {
            register(it, "defaultTestDataLibraries", addToBuildConfig = false)
        }

    init {
        listFromGradleProperty(COMPANION_LIBRARIES_PROPERTY).forEach {
            testDataLibraries { if (it.isProjectPath) common(project(it)) else common(it) }
        }
    }

    /** Configure libraries that test data files can use */
    public fun testDataLibraries(block: DevKitTestDataLibraries.() -> Unit) {
        testDataLibraries.block()
    }

    internal fun register(
        library: DevKitTestDataLibrary,
        name: String,
        isTransitive: Boolean = false,
        addToBuildConfig: Boolean = true,
    ) {
        val jvmClasspath = "$name.jvm.classpath"
        val jsClasspath = "$name.js.classpath"
        val configuration =
            configurations.dependencyScope(name) {
                this.isTransitive = isTransitive
                fromDependencyCollector(library.common)
            }
        val jvmConfiguration =
            configurations.resolvable("${name}Jvm") {
                extendsFrom(configuration)
                this.isTransitive = isTransitive
                fromDependencyCollector(library.jvm)
            }
        val jsConfiguration =
            configurations.resolvable("${name}Js") {
                extendsFrom(configuration)
                this.isTransitive = isTransitive
                fromDependencyCollector(library.js)
                attributes {
                    attribute(
                        Usage.USAGE_ATTRIBUTE,
                        objects.named(KotlinUsages.KOTLIN_RUNTIME),
                    )
                    attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
                }
            }
        testAgainst.configureEach {
            testTask {
                setClasspathProperty(jvmClasspath, jvmConfiguration)
                setClasspathProperty(jsClasspath, jsConfiguration)
            }
        }
        buildConfig.sourceSets.named(TestFixturesSupport.TEST_FIXTURE_SOURCESET_NAME) {
            if (!addToBuildConfig) return@named
            forClass("TestDataLibraries") {
                buildConfigField(
                    "org.jetbrains.kotlin.compiler.plugin.devkit.services.TestDataLibrary",
                    name,
                    """TestDataLibrary("$jvmClasspath", "$jsClasspath")""",
                )
            }
        }
    }

    /**
     * Declares a version that the plugin should be tested against. This method is idempotent.
     *
     * @see [testAgainstWithFixtures], [developFor], [developForWithFixtures]
     */
    public fun testAgainst(
        version: KotlinToolingVersion,
        action: DevKitTestTarget.() -> Unit = {},
    ): DevKitTestTarget = testAgainst(version.versionString, action)

    /**
     * Declares a version that the plugin should be tested against. This method is idempotent.
     *
     * @see [testAgainstWithFixtures], [developFor], [developForWithFixtures]
     */
    public fun testAgainst(
        version: String,
        action: DevKitTestTarget.() -> Unit = {},
    ): DevKitTestTarget = testAgainst.maybeCreate(version).apply(action)

    /**
     * Declares a version that the plugin should be tested against and that owns a testFixtures
     * overlay. Shortcut for:
     * ```kotlin
     * testAgainst(version).withFixtures().apply(action)
     * ```
     *
     * This method is idempotent.
     *
     * @see [testAgainst], [developForWithFixtures], [developFor]
     */
    public fun testAgainstWithFixtures(
        version: KotlinToolingVersion,
        action: DevKitTestFixturesTarget.() -> Unit = {},
    ): DevKitTestFixturesTarget = testAgainst(version).withFixtures().apply(action)

    /**
     * Declares a version that the plugin should be tested against and that owns a testFixtures
     * overlay. Shortcut for:
     * ```kotlin
     * testAgainst(version).withFixtures().apply(action)
     * ```
     *
     * This method is idempotent.
     *
     * @see [testAgainst], [developForWithFixtures], [developFor]
     */
    public fun testAgainstWithFixtures(
        version: String,
        action: DevKitTestFixturesTarget.() -> Unit = {},
    ): DevKitTestFixturesTarget = testAgainst(version).withFixtures().apply(action)

    /**
     * Declares a version that the plugin should be tested against and that owns a main overlay.
     * Shortcut for:
     * ```kotlin
     * testAgainst(version).withMain().apply(action)
     * ```
     *
     * This method is idempotent.
     *
     * @see [testAgainst], [testAgainstWithFixtures], [developForWithFixtures]
     */
    public fun developFor(
        version: KotlinToolingVersion,
        action: DevKitMainTarget.() -> Unit = {},
    ): DevKitMainTarget = testAgainst(version).withMain().apply(action)

    /**
     * Declares a version that the plugin should be tested against and that owns a main overlay.
     * Shortcut for:
     * ```kotlin
     * testAgainst(version).withMain().apply(action)
     * ```
     *
     * This method is idempotent.
     *
     * @see [testAgainst], [testAgainstWithFixtures], [developForWithFixtures]
     */
    public fun developFor(
        version: String,
        action: DevKitMainTarget.() -> Unit = {},
    ): DevKitMainTarget = testAgainst(version).withMain().apply(action)

    /**
     * Declares a version that the plugin should be tested against and that owns both a main and a
     * testFixtures overlay. Shortcut for:
     * ```kotlin
     * developFor(version).withFixtures().apply(action)
     * ```
     *
     * This method is idempotent.
     *
     * @see [testAgainst], [testAgainstWithFixtures], [developFor]
     */
    public fun developForWithFixtures(
        version: KotlinToolingVersion,
        action: DevKitTarget.() -> Unit = {},
    ): DevKitTarget = developFor(version).withFixtures().apply(action)

    /**
     * Declares a version that the plugin should be tested against and that owns both a main and a
     * testFixtures overlay. Shortcut for:
     * ```kotlin
     * developFor(version).withFixtures().apply(action)
     * ```
     *
     * This method is idempotent.
     *
     * @see [testAgainst], [testAgainstWithFixtures], [developFor]
     */
    public fun developForWithFixtures(
        version: String,
        action: DevKitTarget.() -> Unit = {},
    ): DevKitTarget = developFor(version).withFixtures().apply(action)

    /**
     * Declares that this version owns a `testFixtures` overlay. This is the same as having declared
     * the target using [testAgainstWithFixtures] or [developForWithFixtures].
     *
     * @see [sourceFixturesFrom] if you want to reuse some other version's overlay, but add a little
     *   of your own on top
     */
    public fun <Main : Unit> DevKitTestTargetT<*, Main>.withFixtures(
        action: KotlinSourceSet.() -> Unit = {}
    ): DevKitTestTargetT<Nothing, Main> {
        @Suppress("KotlinConstantConditions") // KTIJ-39636
        contract {
            returns() implies (this@withFixtures is DevKitTestFixturesTarget)
        }
        @Suppress("UNCHECKED_CAST")
        if (withFixtures.add(this as DevKitTestFixturesTarget)) {
            // Stop it from automatically using the closest version's fixtures
            fixturesFrom.unsetConvention()
        }
        testFixtures(action)
        return this
    }

    /**
     * Declares that this version owns a `main` overlay. This is the same as having declared the
     * target using [developFor] or [developForWithFixtures]
     */
    public fun <Fixtures : Unit> DevKitTestTargetT<Fixtures, *>.withMain(
        action: KotlinSourceSet.() -> Unit = {}
    ): DevKitTestTargetT<Fixtures, Nothing> {
        @Suppress("KotlinConstantConditions") // KTIJ-39636
        contract {
            returns() implies (this@withMain is DevKitMainTarget)
        }
        @Suppress("UNCHECKED_CAST") developFor.add(this as DevKitMainTarget)
        main(action)
        return this
    }

    internal fun configure() {
        configurePlugins()
        configureTestTasks()
        configureMultiVersionJars()
        configureSourceSets()
        configureDependencies()
        configureCompilationWiring()
    }

    internal fun configurePlugins() {
        for (compilation in listOf(sharedMain, sharedTest, sharedTestFixtures)) {
            configurations.named(compilation.compileDependencyConfigurationName) {
                withCompilerVersionAttribute(kotlinToolingVersion)
            }
        }
        sharedTest.defaultSourceSet.resources.setSrcDirs(listOf("src/test/data"))
        buildConfig {
            packageName.set(pluginPackage)
            forClass("PluginInfo") {
                useKotlinOutput { internalVisibility = true }
                buildConfigField("PLUGIN_ID") {
                    type(String::class)
                    expression(pluginPackage.map { "\"$it\"" })
                }
            }
        }
        kotlinExtension.apply {
            compilerOptions {
                optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
                optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
            }
        }
        project.configure<AnimalSnifferExtension> {
            sourceSets = listOf()
            excludeJars("kotlin-compiler*")
            ignore("java.*", "javax.*", "org.omg.*", "org.w3c.dom.*")
            testAgainst.configureEach {
                val signature = configurations.create("${versionName}Signature")
                dependencies {
                    // TODO add all Kotlin lib signatures, not just compiler
                    signature(kotlin("compiler", version.versionString))
                    signature(kotlin("compiler-internal-test-framework", version.versionString))
                }
                val signatureGenTask =
                    tasks.register<BuildSignatureTask>("${versionName}Signature") {
                        signatures(configurations.getByName("signature"))
                        files(signature)
                    }
                signatures += project.files(signatureGenTask.map { it.outputFiles })
            }
        }
        testing.suites.named<JvmTestSuite>(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME) {
            targets.all {
                testTask {
                    enabled = false
                    dependsOn(defaultTestTarget.map { it.testSuite })
                }
            }
        }
        project.pluginManager.withPlugin("com.gradleup.shadow") {
            tasks.shadowJar {
                enableAutoRelocation.set(true)
                relocationPrefix.set(pluginPackage)
                archiveClassifier.set("")
                // relocate plugin package to itself, so it isn't accidentally auto-relocated
                relocators.add(provider { SimpleRelocator(pluginPackage(), pluginPackage()) })
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
                mergeServiceFiles()
                dependencies {
                    exclude(dependency("org.jetbrains:.*"))
                    exclude(dependency("org.intellij:.*"))
                    exclude(dependency("org.jetbrains.kotlin:.*"))
                }
            }
            mainJar { archiveClassifier.set("unshaded") }

            // https://github.com/GradleUp/shadow/blob/ec0900ee6ef6f6965e568e32e51bbd34ab299635/src/main/kotlin/com/github/jengelman/gradle/plugins/shadow/ShadowJavaPlugin.kt#L58
            configurations {
                fun Configuration.useShadowJar() {
                    setExtendsFrom(emptyList())
                    extendsFrom(shadow)
                    outgoing.artifacts.clear()
                    outgoing.variants.clear()
                    outgoing.artifact(tasks.shadowJar)
                    attributes {
                        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
                    }
                }
                named("apiElements") { useShadowJar() }
                named("runtimeElements") { useShadowJar() }
            }
        }
    }

    internal fun configureTestTasks() {
        val testArtifacts = configurations.dependencyScope("testArtifacts")
        val testArtifactsWasm = configurations.dependencyScope("testArtifactsWasm")
        testAgainst.configureEach {
            testTask {
                val testArtifacts = testArtifacts.withKotlinVersion(version)
                val testArtifactsWasm =
                    testArtifactsWasm.withKotlinVersion(version) {
                        isTransitive = false
                        attributes {
                            attribute(
                                KotlinPlatformType.attribute,
                                KotlinPlatformType.wasm,
                            )
                        }
                    }

                dependsOn(createJsRepl)

                useJUnitPlatform()
                workingDir = rootDir

                setLibraryProperty(
                    "kotlin-stdlib-jdk8",
                    testArtifacts,
                    "kotlin.full.stdlib.path",
                )
                setLibraryProperty("kotlin-reflect", testArtifacts, "kotlin.reflect.jar.path")
                setLibraryProperty("kotlin-test", testArtifacts)
                setLibraryProperty("kotlin-script-runtime", testArtifacts)
                setLibraryProperty(
                    "kotlin-annotations-jvm",
                    testArtifacts,
                    "kotlin.mockJDK.annotations.path",
                )
                setLibraryProperty(
                    "kotlin-stdlib",
                    testArtifacts,
                    "org.jetbrains.kotlin.test.kotlin-common-stdlib",
                )

                systemProperty("idea.ignore.disabled.plugins", "true")
                systemProperty("idea.home.path", rootDir)

                // Properties required to run JS tests from the internal test framework.
                with(d8EnvSpec) { dependsOn(project.d8SetupTaskProvider) }

                setLibraryProperty("kotlin-stdlib-js", testArtifacts)
                setLibraryProperty("kotlin-test-js", testArtifacts)
                setLibraryProperty("kotlin-stdlib-wasm-js", testArtifactsWasm)
                setLibraryProperty("kotlin-stdlib-wasm-wasi", testArtifactsWasm)
                setLibraryProperty("kotlin-test-wasm-js", testArtifactsWasm)
                setLibraryProperty("kotlin-test-wasm-wasi", testArtifactsWasm)

                +StringSystemPropertyArgumentProvider(
                    "javascript.engine.path.V8",
                    d8EnvSpec.executable,
                )
                +FileSystemPropertyArgumentProvider(
                    "javascript.engine.path.repl",
                    createJsRepl.map { it.outputFile },
                )
                +FileSystemPropertyArgumentProvider(
                    "kotlin.js.test.root.out.dir",
                    layout.buildDirectory.dir("js-test-output").map { it.asFile },
                )

                // Regenerate golden files: ./gradlew :compiler-plugin:test -PupdateTestData=true
                +StringSystemPropertyArgumentProvider(
                    "kotlin.test.update.test.data",
                    provider { providers.gradleProperty("updateTestData").isPresent.toString() },
                )
            }
        }
        dependencies {
            testArtifacts(kotlin("stdlib"))
            testArtifacts(kotlin("stdlib-jdk8"))
            testArtifacts(kotlin("reflect"))
            testArtifacts(kotlin("test"))
            testArtifacts(kotlin("script-runtime"))
            testArtifacts(kotlin("annotations-jvm"))
            testArtifacts(kotlin("stdlib-js"))
            testArtifacts(kotlin("test-js"))

            testArtifactsWasm(kotlin("stdlib-wasm-js"))
            testArtifactsWasm(kotlin("stdlib-wasm-wasi"))
            testArtifactsWasm(kotlin("test-wasm-js"))
            testArtifactsWasm(kotlin("test-wasm-wasi"))
        }

        tasks
            .register<Test>("allVersionedTests") {
                description = "Run all versioned tests."
                group = "verification"
                enabled = false
                testAgainst.configureEach {
                    testSuite { targets.all { this@register.dependsOn(testTask) } }
                }
            }
            .also {
                tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(it) }
            }
    }

    internal fun configureMultiVersionJars() {
        tasks.withType<Jar> {
            manifest { attributes("Kotlin-Compiler-Plugin-Multi-Release" to true) }
        }
        val versionMarker =
            tasks.register("versionMarker") {
                val markerFile = layout.buildDirectory.file("compiler-plugin-devkit/.marker")
                outputs.file(markerFile).withPropertyName("versionMarker")
                doLast {
                    markerFile.get().asFile.apply {
                        parentFile.mkdirs()
                        writeText("")
                    }
                }
            }
        context(target: DevKitTestTarget)
        fun Jar.copyVersionFiles(
            compilation: CompilationProvider,
            removeKotlinModule: Boolean = false,
        ) = metaInf {
            into("kotlin/plugin/versions/${target.version}") {
                // Ensures the directory exists in the jar even if `main` has no output.
                from(versionMarker)
                from(compilation.map { it.output.allOutputs }) {
                    if (removeKotlinModule) exclude("META-INF/*kotlin_module")
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                }
            }
        }
        developFor.configureEach {
            val mainCompilation = mainCompilation
            mainJar { copyVersionFiles(mainCompilation, true) }
            project.pluginManager.withPlugin("com.gradleup.shadow") {
                tasks.shadowJar { copyVersionFiles(mainCompilation, true) }
            }
        }
        testAgainst.configureEach {
            testFixturesJar { copyVersionFiles(fixturesCompilation, false) }
        }
    }

    internal fun configureSourceSets() {
        developFor.configureEach {
            mainCompilation {
                defaultSourceSet {
                    kotlin.setSrcDirs(listOf("$versionName/src/main/kotlin"))
                    resources.setSrcDirs(listOf("${versionName}/src/main/resources"))
                }
                javaSourceSet.java.setSrcDirs(listOf("$versionName/src/main/java"))
            }
        }
        testAgainst.configureEach {
            fixturesCompilation {
                defaultSourceSet {
                    kotlin.setSrcDirs(listOf("$versionName/src/testFixtures/kotlin"))
                    resources.setSrcDirs(listOf("${versionName}/src/testFixtures/resources"))
                }
                javaSourceSet.java.setSrcDirs(listOf("$versionName/src/testFixtures/java"))

                srcDirsFrom(fixturesFrom.flatMap { it.fixturesCompilation })
                srcDirsFrom(sharedTestFixtures)
            }
        }
        testAgainst.configureEach {
            kotlinExtension.run {
                testCompilation {
                    defaultSourceSet {
                        kotlin.setSrcDirs(listOf("$versionName/src/test/kotlin"))
                        resources.setSrcDirs(listOf("${versionName}/src/test/data"))
                    }
                    javaSourceSet.apply {
                        java.setSrcDirs(listOf("$versionName/src/test/java"))
                        val directory = generatedTestsDir.dir(this.name)
                        java.srcDir(directory)
                        // This is needed until IDEA fixes IDEA-339729.
                        idea.module
                            .getOrCreateLazyGeneratedSourcesDirs()
                            .addLater(directory.map(Directory::getAsFile))
                    }
                    srcDirsFrom(sharedTest)
                }
            }
        }
    }

    internal fun configureDependencies() = kotlinExtension.run {
        testAgainst.configureEach { testCompilation { forceKotlinVersion(version) } }
        developFor.configureEach { mainCompilation { forceKotlinVersion(version) } }
        testAgainst.configureEach { fixturesCompilation { forceKotlinVersion(version) } }
        dependencies {
            setupCompilerVersionAttribute()

            val sharedTestFixturesApi =
                configurations.named(sharedTestFixtures.defaultSourceSet.apiConfigurationName)

            sharedTestFixturesApi.configure { dependencies.remove(project()) }
            sharedTestFixturesApi(files(mainJar))

            testAgainst.configureEach { registerMultiReleasePluginTransform(version) }

            testAgainst.configureEach {
                fixturesCompilation.defaultSourceSet { apiConfigurationName(files(mainJar)) }
                testSuite { dependencies { implementation(testFixtures(project())) } }
            }

            sharedMain.defaultSourceSet.compileOnlyConfigurationName(kotlin("compiler"))

            sharedTestFixturesApi(kotlin("stdlib"))
            sharedTestFixturesApi(kotlin("test-junit5"))
            sharedTestFixturesApi(kotlin("compiler-internal-test-framework"))
            sharedTestFixturesApi(kotlin("compiler"))
            sharedTestFixtures.defaultSourceSet.runtimeOnlyConfigurationName("junit:junit:4.13.2")

            addIf(
                addRuntimeDependency,
                sharedMain.defaultSourceSet.implementationConfigurationName,
                "$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION",
            )
            addIf(
                addRuntimeDependency,
                sharedTestFixturesApi.name,
                "$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION",
            )
            addIf(
                addRuntimeDependency,
                sharedTestFixturesApi.name,
                testFixtures("$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION"),
            )
        }
    }

    // Based on mrjar and Gradle test-fixtures sourceSet setup
    // https://github.com/gradle/gradle/blob/2ad2f1e3a67ad8f2d07c532874e36d55a786c7e9/platforms/jvm/plugins-jvm-test-fixtures/src/main/java/org/gradle/api/plugins/JavaTestFixturesPlugin.java#L67
    // https://github.com/melix/mrjar-gradle-plugin
    internal fun configureCompilationWiring() = kotlinExtension.run {
        // See also KT-78269
        developFor.configureEach {
            mainCompilation {
                associateWith(sharedMain)
                tasks.named<BaseKotlinCompile>(compileKotlinTaskName) {
                    // Remove main jar from friendPaths to avoid circular dependency error
                    friendPaths -= files(mainJar)
                }
            }
        }

        testAgainst.configureEach {
            fixturesCompilation {
                associateWith(sharedTestFixtures)
                tasks.named<BaseKotlinCompile>(compileKotlinTaskName) {
                    // Remove testFixtures jar from friendPaths to avoid circular dependency error
                    friendPaths -= files(testFixturesJar)
                }
            }
            testCompilation { associateWith(fixturesCompilation.get()) }
        }

        testAgainst.configureEach { testCompilation { associateWith(sharedTest) } }
        testAgainst.configureEach {
            testTask {
                // associateWith adds runtime dependencies on shared modules classes
                // this is rather annoying since they take priority over shared module jars,
                // so we remove them explicitly
                classpath -=
                    project.files(
                        listOf(sharedMain, sharedTest, sharedTestFixtures).map { it.output }
                    )
            }
        }
    }
}

private fun IdeaModule.getOrCreateLazyGeneratedSourcesDirs(): DomainObjectSet<File> =
    generatedSourceDirs as? DomainObjectSet
        ?: project.objects
            .domainObjectSet(File::class.java)
            .apply {
                val oldGeneratedDirs = generatedSourceDirs
                addAllLater(project.provider { oldGeneratedDirs })
            }
            .also { generatedSourceDirs = it }

private fun Test.setLibraryProperty(
    jarName: String,
    testArtifacts: Provider<out FileCollection>,
    vararg extraPropNames: String,
) {
    +LibraryPathArgumentProvider(
        listOf("org.jetbrains.kotlin.test.$jarName") + extraPropNames,
        jarName,
        testArtifacts,
    )
}

@OptIn(ExperimentalWasmDsl::class)
public class DevKitCompilerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply {
            apply("org.jetbrains.kotlin.jvm")
            apply("jvm-test-suite")
            apply("com.github.gmazzo.buildconfig")
            apply("ru.vyarus.animalsniffer")
            apply("java-test-fixtures")
            apply("com.github.node-gradle.node")
            apply("idea")
            apply("maven-publish")
            apply("$DEV_KIT_GROUP.functional-test-publishing")
            apply(D8Plugin::class.java)
        }
        val pluginDevKit =
            project.extensions.create<DevKitCompilerPluginConfigExtension>("pluginDevKit", project)
        pluginDevKit.configure()
    }
}
