@file:OptIn(ExperimentalContracts::class)

package org.jetbrains.kotlin.compiler.plugin.devkit

import com.github.gmazzo.buildconfig.BuildConfigExtension
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.internal.JavaPluginHelper
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.component.external.model.TestFixturesSupport
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.ide.api.GeneratorTask
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.internal.generator.generator.Generator
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension
import ru.vyarus.gradle.plugin.animalsniffer.signature.BuildSignatureTask

private val KotlinToolingVersion.capitalizedVersionName
    get() = "K$baseVersionName"

// The Kotlin plugin mirrors each SourceSet's java.srcDirs into a "kotlin" SourceDirectorySet,
// but never the reverse. Used instead of .allSource to get root source dirs (not resources, and
// not matched leaf files).
private val SourceSet.kotlin: SourceDirectorySet
    get() = extensions.getByName("kotlin") as SourceDirectorySet

public sealed class DevKitTestTargetT<
    out Fixtures : NamedDomainObjectProvider<SourceSet>?,
    out Main : NamedDomainObjectProvider<SourceSet>?,
> : Named {
    override fun getName(): String = version.versionString

    public val versionName: String
        get() = version.versionName

    public abstract val version: KotlinToolingVersion
    public abstract val testSuite: NamedDomainObjectProvider<JvmTestSuite>
    internal abstract val _testFixtures: Fixtures
    internal abstract val _main: Main
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

    override var _testFixtures: @UnsafeVariance Fixtures = null as Fixtures
    override var _main: @UnsafeVariance Main = null as Main
}

private fun DevKitTestTarget.testTask(action: Test.() -> Unit) = testSuite {
    targets.all { this.testTask(action) }
}

public abstract class DevKitCompilerPluginConfigExtension(override val project: Project) :
    HasProject {
    private val buildConfig = project.extensions.getByType<BuildConfigExtension>()

    public fun registerRuntimeLibraries(
        name: String,
        isTransitive: Boolean = false,
        forJvm: Boolean = true,
        forJs: Boolean = true,
        addToBuildConfig: Boolean = true,
        configure: DependencyScopeConfiguration.() -> Unit = {},
    ): NamedDomainObjectProvider<DependencyScopeConfiguration> {
        val jvm: String = "$name.jvm.classpath"
        val js: String = "$name.js.classpath"
        val configuration =
            project.configurations.dependencyScope(name) {
                this.isTransitive = isTransitive
            }
        val jvmConfiguration =
            project.configurations.resolvable("${name}Jvm") {
                extendsFrom(configuration)
                this.isTransitive = isTransitive
            }
        val jsConfiguration =
            project.configurations.resolvable("${name}Js") {
                extendsFrom(configuration)
                this.isTransitive = isTransitive
                attributes {
                    attribute(
                        Usage.USAGE_ATTRIBUTE,
                        project.objects.named(KotlinUsages.KOTLIN_RUNTIME),
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

        return configuration.also { it(configure) }
    }

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

    public val defaultRuntimeLibraries: NamedDomainObjectProvider<DependencyScopeConfiguration> =
        registerRuntimeLibraries("defaultRuntimeLibraries", addToBuildConfig = false)
    public abstract val generateTestsClass: Property<String>
    public abstract val testGenDirectory: DirectoryProperty

    /**
     * Whether to add an implementation dependency on `compiler-plugin-runtime`. Plugins that don't
     * use its APIs can turn this off to avoid an unused dependency leaking into their published
     * POM.
     */
    public abstract val addRuntimeDependency: Property<Boolean>

    /**
     * Which registered [testAgainst] version the generic `test` task delegates to. Defaults to the
     * Kotlin Gradle Plugin version currently applied to this build.
     */
    public abstract val defaultTestTarget: Property<DevKitTestTarget>

    public fun defaultTestVersion(version: KotlinToolingVersion) {
        defaultTestTarget.set(testAgainst.find { it.version == version })
    }

    public abstract val testAgainst: ExtensiblePolymorphicDomainObjectContainer<DevKitTestTarget>
    public abstract val developFor: NamedDomainObjectSet<DevKitMainTarget>
    public abstract val withFixtures: NamedDomainObjectSet<DevKitTestFixturesTarget>

    init {
        testAgainst.registerFactory(DevKitTestTarget::class.java) {
            val version = KotlinToolingVersion(it)
            DevKitTestTargetImpl<
                NamedDomainObjectProvider<SourceSet>?,
                NamedDomainObjectProvider<SourceSet>?,
            >(
                version,
                project.project.extensions
                    .getByType<TestingExtension>()
                    .suites
                    .register<JvmTestSuite>("${version.versionName}Test"),
            )
        }
    }

    public fun testAgainst(
        version: KotlinToolingVersion,
        action: Action<DevKitTestTarget> = Action {},
    ): DevKitTestTarget = testAgainst.maybeCreate(version.versionString).apply(action::execute)

    public fun testAgainstWithFixtures(
        version: KotlinToolingVersion,
        action: Action<DevKitTestFixturesTarget> = Action {},
    ): DevKitTestFixturesTarget =
        context(project) { testAgainst(version).withFixtures().apply(action::execute) }

    public fun developFor(
        version: KotlinToolingVersion,
        action: Action<DevKitMainTarget> = Action {},
    ): DevKitMainTarget =
        context(project) { testAgainst(version).withMain().apply(action::execute) }

    public fun developForWithFixtures(
        version: KotlinToolingVersion,
        action: Action<DevKitTarget> = Action {},
    ): DevKitTarget =
        context(project) { testAgainst(version).withFixtures().withMain().apply(action::execute) }

    public val <
        Fixtures : NamedDomainObjectProvider<SourceSet>?,
        Main : NamedDomainObjectProvider<SourceSet>?,
    > DevKitTestTargetT<Fixtures, Main>.testFixtures: Fixtures
        get() {
            contract {
                returnsNotNull() implies
                    (this@testFixtures
                        is DevKitTestTargetImpl<NamedDomainObjectProvider<SourceSet>, Main>)
            }
            return _testFixtures
        }

    public val <
        Fixtures : NamedDomainObjectProvider<SourceSet>?,
        Main : NamedDomainObjectProvider<SourceSet>?,
    > DevKitTestTargetT<Fixtures, Main>.main: Main
        get() {
            contract {
                returnsNotNull() implies
                    (this@main
                        is DevKitTestTargetImpl<Fixtures, NamedDomainObjectProvider<SourceSet>>)
            }
            return _main
        }

    public fun <Main : NamedDomainObjectProvider<SourceSet>?> DevKitTestTargetT<*, Main>
        .withFixtures(
        action: Action<in SourceSet> = {}
    ): DevKitTestTargetT<NamedDomainObjectProvider<SourceSet>, Main> {
        contract {
            returns() implies
                (this@withFixtures
                    is DevKitTestTargetImpl<NamedDomainObjectProvider<SourceSet>, Main>)
        }
        when (this) {
            is DevKitTestTargetImpl -> {}
        }
        if (testFixtures != null) testFixtures.configure(action)
        else {
            _testFixtures =
                project.extensions
                    .getByType<SourceSetContainer>()
                    .register("${version.versionName}TestFixtures", action)
            check(testFixtures != null) // smart cast!
            withFixtures.add(this)
        }
        return this
    }

    public fun <Fixtures : NamedDomainObjectProvider<SourceSet>?> DevKitTestTargetT<Fixtures, *>
        .withMain(
        action: Action<in SourceSet> = {}
    ): DevKitTestTargetT<Fixtures, NamedDomainObjectProvider<SourceSet>> {
        contract {
            returns() implies
                (this@withMain
                    is DevKitTestTargetImpl<Fixtures, NamedDomainObjectProvider<SourceSet>>)
        }
        when (this) {
            is DevKitTestTargetImpl -> {}
        }
        if (main != null) main.configure(action)
        else {
            _main =
                project.extensions
                    .getByType<SourceSetContainer>()
                    .register(version.versionName, action)
            check(main != null) // smart cast!
            developFor.add(this)
        }
        return this
    }
}

public abstract class JsReplGeneratorTask : GeneratorTask<String>() {
    @get:org.gradle.api.tasks.Input
    @get:org.gradle.api.tasks.Optional
    public abstract val defaultRepl: Property<String>

    init {
        generator =
            object : Generator<String> {
                override fun read(inputFile: File?): String? =
                    defaultRepl.orNull ?: inputFile?.readText()

                override fun defaultInstance(): String? = defaultRepl.orNull

                override fun configure(`object`: String?) {}

                override fun write(`object`: String?, outputFile: File?) {
                    if (`object` != null && outputFile != null) outputFile.writeText(`object`)
                }
            }
    }
}

// Public so consumers of this plugin (e.g. :compiler-tests) can reuse it for their own
// classpath-derived system properties
public fun Test.setClasspathProperty(
    propName: String,
    configuration: Provider<out FileCollection>,
) {
    +ClasspathSystemPropertyArgumentProvider(propName, configuration)
}

@OptIn(ExperimentalWasmDsl::class)
public class DevKitCompilerPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val resourceLoader = this@DevKitCompilerPlugin.javaClass.classLoader
        pluginManager.apply {
            apply("org.jetbrains.kotlin.jvm")
            apply("jvm-test-suite")
            apply("com.github.gmazzo.buildconfig")
            apply("ru.vyarus.animalsniffer")
            apply("java-test-fixtures")
            apply("com.github.node-gradle.node")
            apply("idea")
            apply("maven-publish")
            apply(DevKitFunctionalTestPublishingPlugin::class.java)
        }

        project.plugins.apply(D8Plugin::class.java)

        val kotlinExtension = extensions.getByType<KotlinJvmProjectExtension>()
        val mainCompilation =
            kotlinExtension.target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
        val testCompilation =
            kotlinExtension.target.compilations.getByName(KotlinCompilation.TEST_COMPILATION_NAME)

        val defaultTestSuite = JavaPluginHelper.getDefaultTestSuite(project)

        val testFixturesFeature =
            JavaPluginHelper.getJavaComponent(project)
                .features
                .getByName(TestFixturesSupport.TEST_FIXTURES_FEATURE_NAME)

        val realMain = mainCompilation.javaSourceSet
        val realTest = testCompilation.javaSourceSet
        val realTestFixtures = testFixturesFeature.sourceSet

        val animalsniffer = extensions.getByType<AnimalSnifferExtension>()
        val buildConfig = extensions.getByType<BuildConfigExtension>()
        val idea = extensions.getByType<IdeaModel>()

        val pluginDevKit =
            project.extensions.create<DevKitCompilerPluginConfigExtension>("pluginDevKit", project)

        pluginDevKit.addRuntimeDependency.convention(true)
        pluginDevKit.defaultTestTarget.convention(pluginDevKit.testAgainst(kotlinToolingVersion))
        val generateTestsClass =
            pluginDevKit.generateTestsClass.convention(
                project.provider { "$group.GenerateTestsKt" }
            )
        val testGenDirectory =
            pluginDevKit.testGenDirectory.convention(layout.buildDirectory.dir("test-gen"))

        val testArtifacts = configurations.register("testArtifacts")
        val testArtifactsWasm =
            configurations.register("testArtifactsWasm") {
                isTransitive = false
                attributes { attribute(KotlinPlatformType.attribute, KotlinPlatformType.wasm) }
            }

        fun SourceSet.addTestGenDir() =
            testGenDirectory.dir(name).let {
                java.srcDir(it)
                // This is needed until IDEA fixes IDEA-339729.
                idea.module.generatedSourceDirs.add(it.get().asFile)
            }

        val jsReplOutputFile = layout.buildDirectory.file("js-test-repl/repl.js")

        val createJsRepl =
            tasks.register("createJsRepl", JsReplGeneratorTask::class) {
                description = "Creates a JS Repl for use by the testing framework"
                defaultRepl.convention(resourceLoader.getResource("repl.js")?.readText())
                outputFile = jsReplOutputFile.get().asFile
            }

        fun Test.setLibraryProperty(
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

        fun Test.configure(
            testArtifacts: Provider<out Configuration>,
            testArtifactsWasm: Provider<out Configuration>,
        ) {
            dependsOn(createJsRepl)

            useJUnitPlatform()
            workingDir = rootDir

            // Properties required to run the internal test framework. Some framework code bypasses
            // provider abstraction entirely and reads the TestCompilePaths properties directly
            // So we need both org.jetbrains.kotlin.test and these TestCompilePaths names.
            setLibraryProperty("kotlin-stdlib-jdk8", testArtifacts, "kotlin.full.stdlib.path")
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
            val d8EnvSpec = project.the<D8EnvSpec>()
            with(d8EnvSpec) { dependsOn(project.d8SetupTaskProvider) }

            setLibraryProperty("kotlin-stdlib-js", testArtifacts)
            setLibraryProperty("kotlin-test-js", testArtifacts)
            setLibraryProperty("kotlin-stdlib-wasm-js", testArtifactsWasm)
            setLibraryProperty("kotlin-stdlib-wasm-wasi", testArtifactsWasm)
            setLibraryProperty("kotlin-test-wasm-js", testArtifactsWasm)
            setLibraryProperty("kotlin-test-wasm-wasi", testArtifactsWasm)

            +StringSystemPropertyArgumentProvider("javascript.engine.path.V8", d8EnvSpec.executable)
            +FileSystemPropertyArgumentProvider("javascript.engine.path.repl", jsReplOutputFile)
            +FileSystemPropertyArgumentProvider(
                "kotlin.js.test.root.out.dir",
                layout.buildDirectory.dir("js-test-output"),
            )
        }

        dependencies {
            // Dependencies required to run the internal test framework.
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
        defaultTestSuite.targets.all {
            testTask {
                enabled = false
                classpath = files()
                testClassesDirs = files()
                dependsOn(pluginDevKit.defaultTestTarget.map { it.testSuite })
            }
        }

        fun JavaExec.addTestSourceSet(set: SourceSet) {
            inputs
                .files(set.resources.sourceDirectories)
                .withPathSensitivity(PathSensitivity.RELATIVE)
            argFiles(
                set.resources.sourceDirectories,
                testGenDirectory.dir(set.name),
            )
        }

        val generateTests =
            tasks.register("generateTests", JavaExec::class) {
                description = "Generate compiler plugin tests"
                outputs.dir(testGenDirectory).withPropertyName("generatedTests")

                classpath(realTestFixtures.runtimeClasspath)
                mainClass.set(generateTestsClass)
                workingDir = rootDir
                doFirst {
                    // We need to delete previously generated tests to handle
                    // the case when the generated runner was removed from the generation
                    testGenDirectory.asFile.get().deleteRecursively()
                }
            }

        tasks.named(testCompilation.compileKotlinTaskName) { dependsOn(generateTests) }

        fun ResolutionStrategy.forceKotlinVersion(version: KotlinToolingVersion) = eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(version.versionString)
        }

        fun NamedDomainObjectProvider<out Configuration>.withKotlinVersion(
            version: KotlinToolingVersion,
            action: ResolvableConfiguration.() -> Unit = {},
        ) =
            configurations.resolvable("$name${version.capitalizedVersionName}") {
                extendsFrom(this@withKotlinVersion)
                resolutionStrategy.forceKotlinVersion(version)
                action()
            }

        fun SourceSet.withCompilerVersionAttribute(version: KotlinToolingVersion) {
            configurations.named(compileClasspathConfigurationName) {
                // Force the packed jar (not Gradle's raw classes dir) on the compile classpath:
                // MultiReleasePluginTransform reads the per-version overlay from a jar's META-INF.
                attributes.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements.JAR),
                )
                // force it to use version-specific jars when compiling tests
                attributes.attribute(
                    KotlinCompilerVersion.attribute,
                    KotlinCompilerVersion(version),
                )
            }
            configurations.named(runtimeClasspathConfigurationName) {
                attributes.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements.JAR),
                )
                // force it to use version-specific jars when running tests
                attributes.attribute(
                    KotlinCompilerVersion.attribute,
                    KotlinCompilerVersion(version),
                )
            }
        }

        fun SourceSet.forceKotlinVersion(version: KotlinToolingVersion) {
            // TODO which of these are actually needed?
            configurations.named(implementationConfigurationName) {
                resolutionStrategy.forceKotlinVersion(version)
            }
            configurations.named(compileOnlyConfigurationName) {
                resolutionStrategy.forceKotlinVersion(version)
            }
            configurations.named(compileClasspathConfigurationName) {
                resolutionStrategy.forceKotlinVersion(version)
            }
            configurations.named(runtimeClasspathConfigurationName) {
                resolutionStrategy.forceKotlinVersion(version)
            }
            withCompilerVersionAttribute(version)
        }

        fun SourceSet.extendsFrom(
            otherSourceSet: SourceSet,
            extractName: (SourceSet) -> String,
        ) {
            configurations.named(extractName(this)) {
                extendsFrom(configurations.named(extractName(otherSourceSet)))
            }
        }

        realMain.withCompilerVersionAttribute(kotlinToolingVersion)
        realTest.withCompilerVersionAttribute(kotlinToolingVersion)
        realTestFixtures.withCompilerVersionAttribute(kotlinToolingVersion)
        realTest.resources.setSrcDirs(listOf("src/test/data"))

        val versionMarker =
            // Registers task to generate empty version marker file
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

        // Based on mrjar and Gradle test-fixtures sourceSet setup
        // https://github.com/gradle/gradle/blob/2ad2f1e3a67ad8f2d07c532874e36d55a786c7e9/platforms/jvm/plugins-jvm-test-fixtures/src/main/java/org/gradle/api/plugins/JavaTestFixturesPlugin.java#L67
        // https://github.com/melix/mrjar-gradle-plugin
        with(pluginDevKit) {
            developFor.configureEach {
                main {
                    java.setSrcDirs(
                        listOf("$versionName/src/main/java", "$versionName/src/main/kotlin")
                    )
                    resources.setSrcDirs(listOf("${versionName}/src/main/resources"))
                    forceKotlinVersion(version)
                    dependencies {
                        compileOnlyConfigurationName(kotlin("compiler"))
                        // allow it to see shared main sources
                        compileOnlyConfigurationName(realMain.output.classesDirs)
                    }
                    extendsFrom(realMain) { it.implementationConfigurationName }
                    extendsFrom(realMain) { it.compileOnlyConfigurationName }
                }
                tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME) {
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
                testFixtures {
                    java.setSrcDirs(listOf("$versionName/src/testFixtures/java"))
                    kotlin.setSrcDirs(listOf("$versionName/src/testFixtures/kotlin"))
                    resources.setSrcDirs(listOf("${versionName}/src/testFixtures/resources"))

                    kotlin.srcDir(realTestFixtures.kotlin.sourceDirectories)
                    java.srcDir(realTestFixtures.java.sourceDirectories)
                    resources.srcDir(realTestFixtures.resources.sourceDirectories)
                    forceKotlinVersion(version)
                    dependencies {
                        implementationConfigurationName(project())
                        compileOnlyConfigurationName(kotlin("compiler"))
                    }
                    extendsFrom(realTestFixtures) { it.implementationConfigurationName }
                    extendsFrom(realTestFixtures) { it.compileOnlyConfigurationName }
                    kotlinExtension.target.compilations.named(name) {
                        associateWith(mainCompilation)
                    }
                }

                // KGP automatically associates "test" with "testFixtures" (in addition to
                // "test" with "main"), granting access to its internal API.
                kotlinExtension.target.compilations.named(testSuite.name) {
                    associateWith(kotlinExtension.target.compilations.getByName(testFixtures.name))
                }

                testFixturesFeature.jarTask {
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
        pluginDevKit.testAgainst.configureEach {
            testSuite {
                dependencies {
                    implementation(testFixtures(project()))
                }
                sources {
                    kotlin.setSrcDirs(listOf("$versionName/src/test/kotlin"))
                    java.setSrcDirs(listOf("$versionName/src/test/java"))
                    resources.setSrcDirs(listOf("${versionName}/src/test/data"))

                    kotlin.srcDir(realTest.kotlin.sourceDirectories)
                    java.srcDir(realTest.java.sourceDirectories)
                    resources.srcDir(realTest.resources.sourceDirectories)
                    addTestGenDir()
                    forceKotlinVersion(version)
                    // KGP automatically associates the sourceSet literally named "test" with
                    // "main", granting access to its internal API.
                    kotlinExtension.target.compilations.named(name) {
                        associateWith(mainCompilation)
                    }
                    generateTests { addTestSourceSet(this@sources) }
                    tasks.named(compileJavaTaskName) { dependsOn(generateTests) }

                    extendsFrom(realTest) { it.implementationConfigurationName }
                    extendsFrom(realTest) { it.compileOnlyConfigurationName }

                    withCompilerVersionAttribute(version)
                }
            }

            testTask {
                configure(
                    testArtifacts.withKotlinVersion(version),
                    testArtifactsWasm.withKotlinVersion(version) {
                        this.isTransitive = false
                        attributes {
                            attribute(KotlinPlatformType.attribute, KotlinPlatformType.wasm)
                        }
                    },
                )
            }

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
            animalsniffer.signatures += files(signatureGenTask.map { it.outputFiles })

            dependencies.registerMultiReleasePluginTransform(project.provider { version })
        }

        val allVersionedTests =
            tasks.register<Test>("allVersionedTests") {
                description = "Run all versioned tests."
                group = "verification"
                enabled = false
                pluginDevKit.testAgainst.configureEach {
                    testSuite { targets.all { this@register.dependsOn(testTask) } }
                }
            }
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(allVersionedTests) }

        realTest.addTestGenDir()

        dependencies {
            realMain.compileOnlyConfigurationName(kotlin("compiler"))

            realTestFixtures.apiConfigurationName(kotlin("stdlib"))
            realTestFixtures.apiConfigurationName(kotlin("test-junit5"))
            realTestFixtures.apiConfigurationName(kotlin("compiler-internal-test-framework"))
            realTestFixtures.apiConfigurationName(kotlin("compiler"))
            realTestFixtures.runtimeOnlyConfigurationName("junit:junit:4.13.2")

            addIf(
                pluginDevKit.addRuntimeDependency,
                realMain.implementationConfigurationName,
                "$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION",
            ) {
                exclude(
                    group = "org.jetbrains.kotlin",
                    module = "kotlin-compiler",
                )
            }
            addIf(
                pluginDevKit.addRuntimeDependency,
                realTestFixtures.apiConfigurationName,
                testFixtures("$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION"),
            ) {
                exclude(
                    group = "org.jetbrains.kotlin",
                    module = "kotlin-compiler-internal-test-framework",
                )
            }
        }

        animalsniffer.sourceSets = listOf(realMain, realTest)
        animalsniffer.excludeJars("kotlin-compiler*")
        animalsniffer.ignore("java.*", "javax.*", "org.omg.*", "org.w3c.dom.*")

        buildConfig.packageName(group.toString())
        buildConfig.forClass("PluginInfo") {
            useKotlinOutput { internalVisibility = true }
            buildConfigField("String", "PLUGIN_ID", "\"$group\"")
        }

        kotlinExtension.compilerOptions {
            optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
            optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
        }

        tasks.withType<Jar> {
            manifest { attributes("Kotlin-Compiler-Plugin-Multi-Release" to true) }
        }

        dependencies.setupCompilerVersionAttribute()
    }
}
