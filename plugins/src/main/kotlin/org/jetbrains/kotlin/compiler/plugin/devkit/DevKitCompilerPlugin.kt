@file:OptIn(ExperimentalContracts::class)

package org.jetbrains.kotlin.compiler.plugin.devkit

import com.github.gmazzo.buildconfig.BuildConfigExtension
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.attributes.Usage
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.component.external.model.TestFixturesSupport
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.named
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
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension
import ru.vyarus.gradle.plugin.animalsniffer.signature.BuildSignatureTask

public sealed class DevKitTestTargetT<
    out Fixtures : NamedDomainObjectProvider<SourceSet>?,
    out Main : NamedDomainObjectProvider<SourceSet>?,
> : Named {
    override fun getName(): String = version.versionString

    public val versionName: String
        get() = version.versionName

    public abstract val version: KotlinToolingVersion
    public abstract val testSuite: NamedDomainObjectProvider<JvmTestSuite>
    internal abstract val nullableTestFixtures: Fixtures
    internal abstract val nullableMain: Main
}

public typealias DevKitTestTarget = DevKitTestTargetT<*, *>

public typealias DevKitMainTarget = DevKitTestTargetT<*, NamedDomainObjectProvider<SourceSet>>

public typealias DevKitTestFixturesTarget =
    DevKitTestTargetT<NamedDomainObjectProvider<SourceSet>, *>

public typealias DevKitTarget =
    DevKitTestTargetT<NamedDomainObjectProvider<SourceSet>, NamedDomainObjectProvider<SourceSet>>

@Suppress("UNCHECKED_CAST")
private class DevKitTestTargetImpl<
    out Fixtures : NamedDomainObjectProvider<SourceSet>?,
    out Main : NamedDomainObjectProvider<SourceSet>?,
>(
    override val version: KotlinToolingVersion,
    override val testSuite: NamedDomainObjectProvider<JvmTestSuite>,
) : DevKitTestTargetT<Fixtures, Main>() {
    override fun equals(other: Any?): Boolean =
        other is DevKitTestTarget && other.version == version

    override fun hashCode(): Int = version.hashCode()

    override var nullableTestFixtures: @UnsafeVariance Fixtures = null as Fixtures
    override var nullableMain: @UnsafeVariance Main = null as Main
}

private fun DevKitTestTarget.testTask(action: Test.() -> Unit) = testSuite {
    targets.all { this.testTask(action) }
}

private fun DevKitTestTarget.test(action: SourceSet.() -> Unit) = testSuite {
    sources(action)
}

@OptIn(ExperimentalWasmDsl::class)
public abstract class DevKitCompilerPluginConfigExtension
internal constructor(override val project: Project) : HasProject {
    internal val buildConfig: BuildConfigExtension = project.the()
    internal val testing: TestingExtension = project.the()
    internal val sourceSets: SourceSetContainer = project.the()
    internal val kotlinExtension: KotlinJvmProjectExtension = project.the()
    internal val idea: IdeaModel = project.the()
    internal val d8EnvSpec: D8EnvSpec = project.the()

    internal val sharedMain = sourceSets.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
    internal val sharedTest = sourceSets.getByName(KotlinCompilation.TEST_COMPILATION_NAME)

    internal val sharedTestFixtures =
        sourceSets.getByName(TestFixturesSupport.TEST_FIXTURE_SOURCESET_NAME)

    /**
     * The package used by convention for BuildConfig generation and where [generateTestsClass]
     * resides. It's also assumed to be the plugin ID. Defaults to [Project.getGroup]
     */
    public val pluginPackage: Property<String> =
        objects.property(provider { project.group.toString() })

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
     * version applied to this build.
     *
     * @see [developFor], [testAgainstWithFixtures], [developForWithFixtures]
     */
    public val testAgainst: ExtensiblePolymorphicDomainObjectContainer<DevKitTestTarget> =
        objects.polymorphicDomainObjectContainer(DevKitTestTarget::class.java).apply {
            registerFactory(DevKitTestTarget::class.java) {
                val version = KotlinToolingVersion(it)
                DevKitTestTargetImpl<
                    NamedDomainObjectProvider<SourceSet>?,
                    NamedDomainObjectProvider<SourceSet>?,
                >(
                    version,
                    testing.suites.register<JvmTestSuite>("${version.versionName}Test"),
                )
            }
        }

    /**
     * Which registered [testAgainst] version the generic `test` task delegates to. Defaults to the
     * Kotlin version applied to this build.
     *
     * @see [defaultTestVersion] to set this using a version instead
     */
    public val defaultTestTarget: Property<DevKitTestTarget> =
        objects.property(testAgainst(kotlinToolingVersion))

    /**
     * Sets the [testAgainst] version the generic `test` task delegates to. Defaults to the Kotlin
     * version applied to this build. This is identical to setting [defaultTestTarget] with the
     * target corresponding to [version]. You can change this version multiple times.
     *
     * @see [defaultTestTarget] if you have a reference to the [DevKitTestTarget] already
     */
    public fun defaultTestVersion(version: KotlinToolingVersion) {
        defaultTestTarget.set(testAgainst.named(version.versionString))
    }

    /**
     * Versions that the plugin wants explicit customization for.
     *
     * @see [testAgainst], [testAgainstWithFixtures], [developForWithFixtures]
     */
    public val developFor: NamedDomainObjectSet<DevKitMainTarget> =
        objects.namedDomainObjectSet(DevKitMainTarget::class.java)

    /**
     * Versions that testFixtures should be customized for.
     *
     * @see [testAgainst], [developFor], [testAgainstWithFixtures], [developForWithFixtures]
     */
    public val withFixtures: NamedDomainObjectSet<DevKitTestFixturesTarget> =
        objects.namedDomainObjectSet(DevKitTestFixturesTarget::class.java)

    /** The (lazily configured) testFixtures source set for this target. */
    public val <
        Fixtures : NamedDomainObjectProvider<SourceSet>?,
        Main : NamedDomainObjectProvider<SourceSet>?,
    > DevKitTestTargetT<Fixtures, Main>.testFixtures: Fixtures
        get() {
            @Suppress("KotlinConstantConditions") // KTIJ-39636
            contract {
                returnsNotNull() implies
                    (this@testFixtures
                        is DevKitTestTargetT<NamedDomainObjectProvider<SourceSet>, Main>)
            }
            return nullableTestFixtures
        }

    /** The (lazily configured) main source set for this target. */
    public val <
        Fixtures : NamedDomainObjectProvider<SourceSet>?,
        Main : NamedDomainObjectProvider<SourceSet>?,
    > DevKitTestTargetT<Fixtures, Main>.main: Main
        get() {
            @Suppress("KotlinConstantConditions") // KTIJ-39636
            contract {
                returnsNotNull() implies
                    (this@main is DevKitTestTargetT<Fixtures, NamedDomainObjectProvider<SourceSet>>)
            }
            return nullableMain
        }

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
    public val generateTests: TaskProvider<GenerateTestsTask> =
        tasks.register("generateTests", GenerateTestsTask::class) {
            description = "Generate compiler plugin tests"
            generatedTestsDir.set(this@DevKitCompilerPluginConfigExtension.generatedTestsDir)

            classpath(sharedTestFixtures.runtimeClasspath)
            mainClass.set(generateTestsClass)
            workingDir = rootDir

            testAgainst.configureEach {
                test {
                    argFiles(resources.sourceDirectories, generatedTestsDir.dir(name))
                }
            }
        }

    init {
        testAgainst.configureEach {
            test { tasks.named(compileJavaTaskName) { dependsOn(generateTests) } }
        }
    }

    /** Libraries that all test data files can use */
    public val defaultRuntimeLibraries: NamedDomainObjectProvider<DependencyScopeConfiguration> =
        registerRuntimeLibraries("defaultRuntimeLibraries", addToBuildConfig = false)

    /**
     * Registers a runtime library for this plugin. The library will be passed to tests under the
     * name "[name].[jvm|js].classpath", if [addToBuildConfig], it'll also be available under
     * RuntimeClasspaths.[name]. This is *not* idempotent. Libraries for each `name` should be
     * registered once by one of the `registerRuntimeLibraries` methods.
     *
     * @see [registerRuntimeLibrariesJvm], [registerRuntimeLibrariesJs] if you only care about one
     *   platform
     */
    public fun registerRuntimeLibraries(
        name: String,
        isTransitive: Boolean = false,
        forJvm: Boolean = true,
        forJs: Boolean = true,
        addToBuildConfig: Boolean = true,
        configure: DependencyScopeConfiguration.() -> Unit = {},
    ): NamedDomainObjectProvider<DependencyScopeConfiguration> {
        val jvm = "$name.jvm.classpath"
        val js = "$name.js.classpath"
        val configuration =
            configurations.dependencyScope(name) {
                this.isTransitive = isTransitive
            }
        val jvmConfiguration =
            configurations.resolvable("${name}Jvm") {
                extendsFrom(configuration)
                this.isTransitive = isTransitive
            }
        val jsConfiguration =
            configurations.resolvable("${name}Js") {
                extendsFrom(configuration)
                this.isTransitive = isTransitive
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
                if (forJvm) setClasspathProperty(jvm, jvmConfiguration)
                if (forJs) setClasspathProperty(js, jsConfiguration)
            }
        }
        buildConfig.sourceSets.named(TestFixturesSupport.TEST_FIXTURE_SOURCESET_NAME) {
            if (!addToBuildConfig) return@named
            forClass("RuntimeClasspaths") {
                if (forJvm)
                    buildConfigField(
                        "List<java.io.File>",
                        if (forJs) "${name}Jvm" else name,
                        """org.jetbrains.kotlin.compiler.plugin.devkit.services.classpathFiles("$jvm")""",
                    )
                if (forJs)
                    buildConfigField(
                        "List<java.io.File>",
                        if (forJvm) "${name}Js" else name,
                        """org.jetbrains.kotlin.compiler.plugin.devkit.services.classpathFiles("$js")""",
                    )
            }
        }
        configuration(configure)

        return configuration
    }

    /**
     * Registers a runtime library for this plugin. The library will be passed to tests under the
     * name "[name].jvm.classpath", if [addToBuildConfig], it'll also be available under
     * RuntimeClasspaths.[name]. This is *not* idempotent. Libraries for each `name` should be
     * registered once by one of the `registerRuntimeLibraries` methods. Shortcut for
     * [registerRuntimeLibraries] with `forJs = false`.
     *
     * @see [registerRuntimeLibraries], [registerRuntimeLibrariesJs]
     */
    public fun registerRuntimeLibrariesJvm(
        name: String,
        isTransitive: Boolean = false,
        addToBuildConfig: Boolean = true,
        configure: DependencyScopeConfiguration.() -> Unit = {},
    ): NamedDomainObjectProvider<DependencyScopeConfiguration> =
        registerRuntimeLibraries(
            name,
            isTransitive = isTransitive,
            forJs = false,
            addToBuildConfig = addToBuildConfig,
            configure = configure,
        )

    /**
     * Registers a runtime library for this plugin. The library will be passed to tests under the
     * name "[name].js.classpath", if [addToBuildConfig], it'll also be available under
     * RuntimeClasspaths.[name]. This is *not* idempotent. Libraries for each `name` should be
     * registered once by one of the `registerRuntimeLibraries` methods. Shortcut for
     * [registerRuntimeLibraries] with `forJvm = false`.
     *
     * @see [registerRuntimeLibraries], [registerRuntimeLibrariesJvm]
     */
    public fun registerRuntimeLibrariesJs(
        name: String,
        isTransitive: Boolean = false,
        addToBuildConfig: Boolean = true,
        configure: DependencyScopeConfiguration.() -> Unit = {},
    ): NamedDomainObjectProvider<DependencyScopeConfiguration> =
        registerRuntimeLibraries(
            name,
            isTransitive = isTransitive,
            forJvm = false,
            addToBuildConfig = addToBuildConfig,
            configure = configure,
        )

    /**
     * Declares a version that the plugin should be tested against. This method is idempotent.
     *
     * @see [testAgainstWithFixtures], [developFor], [developForWithFixtures]
     */
    public fun testAgainst(
        version: KotlinToolingVersion,
        action: DevKitTestTarget.() -> Unit = {},
    ): DevKitTestTarget = testAgainst.maybeCreate(version.versionString).apply(action)

    /**
     * Declares a version that the plugin should be tested against and explicitly customizes its
     * test fixtures. Shortcut for:
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
     * Declares a version that the plugin should be tested against and has explicit customization
     * for. Shortcut for:
     * ```kotlin
     * testAgainst(version).withMain().apply(action)
     * ```
     *
     * This method is idempotent.
     *
     * @see [testAgainstWithFixtures], [developForWithFixtures]
     */
    public fun developFor(
        version: KotlinToolingVersion,
        action: DevKitMainTarget.() -> Unit = {},
    ): DevKitMainTarget = testAgainst(version).withMain().apply(action)

    /**
     * Declares a version that the plugin should be tested against, customizes its test fixtures,
     * and has explicit customization for. Shortcut for:
     * ```kotlin
     * developFor(version).withFixtures().apply(action)
     * ```
     *
     * This method is idempotent.
     *
     * @see [testAgainstWithFixtures], [developFor]
     */
    public fun developForWithFixtures(
        version: KotlinToolingVersion,
        action: DevKitTarget.() -> Unit = {},
    ): DevKitTarget = developFor(version).withFixtures().apply(action)

    /**
     * Adds testFixture customization for this version. This is the same as having declared the
     * target using [testAgainstWithFixtures] or [developForWithFixtures]
     */
    public fun <Main : NamedDomainObjectProvider<SourceSet>?> DevKitTestTargetT<*, Main>
        .withFixtures(
        action: SourceSet.() -> Unit = {}
    ): DevKitTestTargetT<NamedDomainObjectProvider<SourceSet>, Main> {
        @Suppress("KotlinConstantConditions") // KTIJ-39636
        contract {
            returns() implies
                (this@withFixtures is DevKitTestTargetT<NamedDomainObjectProvider<SourceSet>, Main>)
        }
        when (this) {
            is DevKitTestTargetImpl -> {}
        }
        if (testFixtures != null) testFixtures(action)
        else {
            nullableTestFixtures = sourceSets.register("${version.versionName}TestFixtures", action)
            check(testFixtures != null) // smart cast!
            withFixtures.add(this)
        }
        return this
    }

    /**
     * Adds main customization for this version. This is the same as having declared the target
     * using [developFor] or [developForWithFixtures]
     */
    public fun <Fixtures : NamedDomainObjectProvider<SourceSet>?> DevKitTestTargetT<Fixtures, *>
        .withMain(
        action: Action<in SourceSet> = {}
    ): DevKitTestTargetT<Fixtures, NamedDomainObjectProvider<SourceSet>> {
        @Suppress("KotlinConstantConditions") // KTIJ-39636
        contract {
            returns() implies
                (this@withMain is DevKitTestTargetT<Fixtures, NamedDomainObjectProvider<SourceSet>>)
        }
        when (this) {
            is DevKitTestTargetImpl -> {}
        }
        if (main != null) main.configure(action)
        else {
            nullableMain = sourceSets.register(version.versionName, action)
            check(main != null) // smart cast!
            developFor.add(this)
        }
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
        for (set in listOf(sharedMain, sharedTest, sharedTestFixtures)) {
            configurations.named(set.compileClasspathConfigurationName) {
                withCompilerVersionAttribute(kotlinToolingVersion)
            }
            configurations.named(set.runtimeClasspathConfigurationName) {
                withCompilerVersionAttribute(kotlinToolingVersion)
            }
        }
        sharedTest.resources.setSrcDirs(listOf("src/test/data"))
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
            sourceSets = listOf(sharedMain, sharedTest)
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
        developFor.configureEach {
            tasks.named<Jar>(sharedMain.jarTaskName) {
                metaInf {
                    into("kotlin/plugin/versions/$version") {
                        // Ensures the directory exists in the jar even if `main` has no output.
                        from(versionMarker)
                        from(main.map { it.output }) {
                            exclude("META-INF/*kotlin_module")
                            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        }
                    }
                }
            }
        }
        withFixtures.configureEach {
            tasks.named<Jar>(sharedTestFixtures.jarTaskName) {
                metaInf {
                    into("kotlin/plugin/versions/$version") {
                        // Ensures the directory exists in the jar even if `testFixtures` has no
                        // output.
                        from(versionMarker)
                        from(testFixtures.map { it.output }) {
                            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        }
                    }
                }
            }
        }
    }

    internal fun configureSourceSets() {
        developFor.configureEach {
            main {
                kotlin.setSrcDirs(listOf("$versionName/src/main/kotlin"))
                java.setSrcDirs(listOf("$versionName/src/main/java"))
                resources.setSrcDirs(listOf("${versionName}/src/main/resources"))
            }
        }
        withFixtures.configureEach {
            testFixtures {
                java.setSrcDirs(listOf("$versionName/src/testFixtures/java"))
                kotlin.setSrcDirs(listOf("$versionName/src/testFixtures/kotlin"))
                resources.setSrcDirs(listOf("${versionName}/src/testFixtures/resources"))

                kotlin.srcDir(sharedTestFixtures.kotlin.sourceDirectories)
                java.srcDir(sharedTestFixtures.java.sourceDirectories)
                resources.srcDir(sharedTestFixtures.resources.sourceDirectories)
            }
        }
        testAgainst.configureEach {
            testSuite {
                sources {
                    kotlin.setSrcDirs(listOf("$versionName/src/test/kotlin"))
                    java.setSrcDirs(listOf("$versionName/src/test/java"))
                    resources.setSrcDirs(listOf("${versionName}/src/test/data"))

                    kotlin.srcDir(sharedTest.kotlin.sourceDirectories)
                    java.srcDir(sharedTest.java.sourceDirectories)
                    resources.srcDir(sharedTest.resources.sourceDirectories)
                    val directory = generatedTestsDir.dir(this.name)
                    java.srcDir(directory)
                    // This is needed until IDEA fixes IDEA-339729.
                    idea.module
                        .getOrCreateLazyGeneratedSourcesDirs()
                        .addLater(directory.map(Directory::getAsFile))
                }
            }
        }
    }

    internal fun configureDependencies() {
        testAgainst.configureEach { test { forceKotlinVersion(version) } }
        developFor.configureEach { main { forceKotlinVersion(version) } }
        withFixtures.configureEach { testFixtures { forceKotlinVersion(version) } }
        dependencies {
            setupCompilerVersionAttribute()
            testAgainst.configureEach { registerMultiReleasePluginTransform(version) }

            withFixtures.configureEach {
                this.testFixtures { implementationConfigurationName(project()) }
            }
            testAgainst.configureEach {
                testSuite { dependencies { implementation(testFixtures(project())) } }
            }

            sharedMain.compileOnlyConfigurationName(kotlin("compiler"))

            sharedTestFixtures.apiConfigurationName(kotlin("stdlib"))
            sharedTestFixtures.apiConfigurationName(kotlin("test-junit5"))
            sharedTestFixtures.apiConfigurationName(kotlin("compiler-internal-test-framework"))
            sharedTestFixtures.apiConfigurationName(kotlin("compiler"))
            sharedTestFixtures.runtimeOnlyConfigurationName("junit:junit:4.13.2")

            addIf(
                addRuntimeDependency,
                sharedMain.implementationConfigurationName,
                "$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION",
            ) {
                exclude(
                    group = "org.jetbrains.kotlin",
                    module = "kotlin-compiler",
                )
            }
            addIf(
                addRuntimeDependency,
                sharedTestFixtures.apiConfigurationName,
                testFixtures("$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION"),
            ) {
                exclude(
                    group = "org.jetbrains.kotlin",
                    module = "kotlin-compiler-internal-test-framework",
                )
            }
        }
    }

    // Based on mrjar and Gradle test-fixtures sourceSet setup
    // https://github.com/gradle/gradle/blob/2ad2f1e3a67ad8f2d07c532874e36d55a786c7e9/platforms/jvm/plugins-jvm-test-fixtures/src/main/java/org/gradle/api/plugins/JavaTestFixturesPlugin.java#L67
    // https://github.com/melix/mrjar-gradle-plugin
    internal fun configureCompilationWiring() = kotlinExtension.run {
        // See also KT-78269
        val sharedMainCompilation = sharedMain.compilation.get()
        val sharedTestCompilation = sharedTest.compilation.get()
        val sharedTestFixturesCompilation = sharedTestFixtures.compilation.get()
        developFor.configureEach {
            main {
                compilation {
                    associateWith(sharedMainCompilation)
                    tasks.named<BaseKotlinCompile>(compileKotlinTaskName) {
                        // Remove main jar from friendPaths to avoid circular dependency error
                        friendPaths -= files(tasks.named(sharedMain.jarTaskName))
                    }
                }
            }
        }

        withFixtures.configureEach {
            testFixtures {
                compilation {
                    associateWith(sharedTestFixturesCompilation)
                    tasks.named<BaseKotlinCompile>(compileKotlinTaskName) {
                        // Remove main jar from friendPaths to avoid circular dependency error
                        friendPaths -= files(tasks.named(sharedTestFixtures.jarTaskName))
                    }
                }
            }
            val testFixturesCompilation = testFixtures.flatMap { it.compilation }
            test { compilation { associateWith(testFixturesCompilation.get()) } }
        }

        testAgainst.configureEach { test { compilation { associateWith(sharedTestCompilation) } } }
        testAgainst.configureEach {
            testTask {
                // associateWith adds runtime dependencies on shared modules classes
                // this is rather annoying since they take priority over shared module jars,
                // so we remove them explicitly
                classpath -=
                    project.files(
                        listOf(
                                sharedMainCompilation,
                                sharedTestCompilation,
                                sharedTestFixturesCompilation,
                            )
                            .map { it.output.allOutputs }
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
            apply(DevKitFunctionalTestPublishingPlugin::class.java)
            apply(D8Plugin::class.java)
        }
        val pluginDevKit =
            project.extensions.create<DevKitCompilerPluginConfigExtension>("pluginDevKit", project)
        pluginDevKit.configure()
    }
}
