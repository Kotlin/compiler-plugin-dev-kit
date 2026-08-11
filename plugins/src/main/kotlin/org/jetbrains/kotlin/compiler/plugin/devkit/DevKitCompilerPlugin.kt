@file:Suppress("FINAL_UPPER_BOUND")

package org.jetbrains.kotlin.compiler.plugin.devkit

import com.github.gmazzo.buildconfig.BuildConfigExtension
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import java.io.File
import javax.inject.Inject
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.dsl.GradleDependencies
import org.gradle.api.attributes.Usage
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.component.external.model.TestFixturesSupport
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinHierarchyDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

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

@OptIn(ExperimentalWasmDsl::class)
public open class DevKitCompilerPluginConfigExtension
internal constructor(override val project: Project) : HasProject {
    private val buildConfig: BuildConfigExtension = project.the()
    private val kotlinExtension: KotlinMultiplatformExtension = project.the()
    private val idea: IdeaModel = project.the()
    private val d8EnvSpec: D8EnvSpec = project.the()

    // TODO get rid of jvm target entirely!
    private val mainTarget = kotlinExtension.jvm()

    private val commonMain = kotlinExtension.run { sourceSets.commonMain.get() }
    private val commonTestFixtures = kotlinExtension.sourceSets.create("commonTestFixtures")
    private val commonTest = kotlinExtension.run { sourceSets.commonTest.get() }

    private val mainJar = tasks.named<Jar>(mainTarget.artifactsTaskName)
    private val testFixturesJar = tasks.named<Jar>("testFixturesJar")

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
     */
    public val testAgainst: NamedDomainObjectCollection<DevKitTarget> =
        kotlinExtension.targets.withType<DevKitTarget>()

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
    public val defaultTestTarget: Property<DevKitTarget> =
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
     * @see [defaultTestTarget]
     */
    public fun defaultTestVersion(version: KotlinToolingVersion) {
        defaultTestVersion(version.versionString)
    }

    /**
     * Sets the [testAgainst] version the generic `test` task delegates to. Defaults to the Kotlin
     * version applied to this build. This is identical to setting [defaultTestTarget] with the
     * target corresponding to [version]. You can change this version multiple times.
     *
     * @see [defaultTestTarget]
     */
    public fun defaultTestVersion(version: String) {
        defaultTestTarget.set(testAgainst(version))
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

            classpath(
                defaultTestTarget.map { it.testFixturesCompilation.runtimeDependencyFiles },
                defaultTestTarget.map { it.testFixturesCompilation.output.allOutputs },
            )
            mainClass.set(generateTestsClass)
            workingDir = rootDir

            testAgainst.configureEach {
                test {
                    // TODO allow shared tests between versions too
                    argFiles(
                        commonTest.resources.sourceDirectories + resources.sourceDirectories,
                        generatedTestsDir.dir(targetName),
                    )
                }
            }
        }

    init {
        testAgainst.configureEach {
            testCompilation.compileJavaTaskProvider { dependsOn(generateTests) }
            tasks.named(testCompilation.compileKotlinTaskName) { dependsOn(generateTests) }
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
        buildConfig.sourceSets.named(commonTestFixtures.name) {
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

    /** Declares a version that the plugin should be tested against. This method is idempotent. */
    public fun testAgainst(
        version: KotlinToolingVersion,
        action: DevKitTarget.() -> Unit = {},
    ): DevKitTarget {
        val target =
            testAgainst.findByName(version.versionName)
                ?: kotlinExtension.createDevKitTarget(version)
        return target.apply(action)
    }

    /** Declares a version that the plugin should be tested against. This method is idempotent. */
    public fun testAgainst(
        version: String,
        action: DevKitTarget.() -> Unit = {},
    ): DevKitTarget = testAgainst(KotlinToolingVersion(version), action)

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyDsl.applyPluginDevKitHierarchyTemplate(
        template: KotlinHierarchyBuilder.() -> Unit
    ) {
        applyHierarchyTemplate {
            withSourceSetTree(
                KotlinSourceSetTree.main,
                KotlinSourceSetTree(TEST_FIXTURES_COMPILATION_NAME),
                KotlinSourceSetTree.test,
            )

            common {
                withCompilations { true }
                template()
            }
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.withVersions(condition: (KotlinToolingVersion) -> Boolean) {
        withCompilations {
            val target = it.target
            target is DevKitTarget && condition(target.version)
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.withVersions(range: ClosedRange<KotlinToolingVersion>) {
        withVersions { it in range }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.groupVersions(
        name: String,
        condition: (KotlinToolingVersion) -> Boolean,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions(condition)
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.groupVersions(
        name: String,
        range: ClosedRange<KotlinToolingVersion>,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions(range)
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.pre(
        version: KotlinToolingVersion,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions { it < version }
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.preDev(
        version: KotlinVersion,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions { it.toKotlinVersion() < version }
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.post(
        version: KotlinToolingVersion,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions { it >= version }
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.postDev(
        version: KotlinVersion,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions { it.toKotlinVersion() >= version }
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.pre(
        version: String,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        pre(KotlinToolingVersion(version), name, build)
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.preDev(
        major: Int,
        minor: Int,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        preDev(KotlinVersion(major, minor), name, build)
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.preDev(
        major: Int,
        minor: Int,
        patch: Int,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        preDev(KotlinVersion(major, minor, patch), name, build)
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.post(
        version: String,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        post(KotlinToolingVersion(version), name, build)
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.postDev(
        major: Int,
        minor: Int,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        postDev(KotlinVersion(major, minor), name, build)
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.postDev(
        major: Int,
        minor: Int,
        patch: Int,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        postDev(KotlinVersion(major, minor, patch), name, build)
    }

    internal fun configure() {
        configurePlugins()
        configureTestTasks()
        configureMultiVersionJars()
        configureSourceSets()
        configureDependencies()
    }

    internal fun configurePlugins() {
        for (compilation in
            listOf(
                KotlinCompilation.MAIN_COMPILATION_NAME,
                TestFixturesSupport.TEST_FIXTURE_SOURCESET_NAME,
                KotlinCompilation.TEST_COMPILATION_NAME,
            )) {
            configurations.named(
                mainTarget.compilations.getByName(compilation).compileDependencyConfigurationName
            ) {
                withCompilerVersionAttribute(kotlinToolingVersion)
            }
        }
        commonTest.resources.setSrcDirs(listOf("src/commonTest/data"))
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
        kotlinExtension.compilerOptions {
            optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
            optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
        }
        // configure default test task
        mainTarget.testRuns.named(KotlinTargetWithTests.DEFAULT_TEST_RUN_NAME) {
            executionTask {
                enabled = false
                dependsOn(defaultTestTarget.map { it.testTask })
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
                }
                named(mainTarget.apiElementsConfigurationName) { useShadowJar() }
                named(mainTarget.runtimeElementsConfigurationName) { useShadowJar() }
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
        context(target: DevKitTarget)
        fun Jar.copyVersionFiles(compilation: DevKitCompilation) = metaInf {
            into("kotlin/plugin/versions/${target.version}") {
                // Ensures the directory exists in the jar even if `main` has no output.
                from(versionMarker)
                from(compilation.output.allOutputs) {
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                }
            }
        }
        testAgainst.configureEach {
            mainJar { copyVersionFiles(mainCompilation) }
            project.pluginManager.withPlugin("com.gradleup.shadow") {
                tasks.shadowJar { copyVersionFiles(mainCompilation) }
            }
        }
        testAgainst.configureEach {
            testFixturesJar { copyVersionFiles(testFixturesCompilation) }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    internal fun configureSourceSets() {
        kotlinExtension.applyPluginDevKitHierarchyTemplate {}
        testAgainst.configureEach {
            testCompilation.apply {
                defaultSourceSet {
                    resources.setSrcDirs(listOf("src/$name/data"))
                }
                java.apply {
                    val directory = generatedTestsDir.dir(this@configureEach.targetName)
                    srcDir(directory)
                    // This is needed until IDEA fixes IDEA-339729.
                    idea.module
                        .getOrCreateLazyGeneratedSourcesDirs()
                        .addLater(directory.map(Directory::getAsFile))
                }
            }
        }
    }

    internal fun configureDependencies() {
        testAgainst.configureEach {
            for (compilation in listOf(mainCompilation, testFixturesCompilation, testCompilation)) {
                compilation.forceKotlinVersion(version)
            }
        }
        dependencies {
            setupCompilerVersionAttribute()
            testAgainst.configureEach { registerMultiReleasePluginTransform(version) }
        }

        commonMain.dependencies {
            compileOnly(kotlin("compiler"))
            implementation(
                addRuntimeDependency.ifTrue {
                    "$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION"
                }
            )
        }
        commonTestFixtures.dependencies {
            api(kotlin("stdlib"))
            api(kotlin("test-junit5"))
            api(kotlin("compiler-internal-test-framework"))
            api(kotlin("compiler"))
            runtimeOnly("junit:junit:4.13.2")

            api(
                addRuntimeDependency.ifTrue {
                    "$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION"
                }
            )
            api(
                addRuntimeDependency.ifTrue {
                    dependencies.testFixtures(
                        "$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION"
                    )
                }
            )
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
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.github.gmazzo.buildconfig")
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
