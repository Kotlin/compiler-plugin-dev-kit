@file:Suppress("FINAL_UPPER_BOUND")

package org.jetbrains.kotlin.compiler.plugin.devkit

import com.github.gmazzo.buildconfig.BuildConfigExtension
import java.io.File
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.name
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.DEPENDENCIES_PATH
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.PLUGIN_PATH
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.VERSIONS_PATH
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.plugin.sources.AbstractKotlinSourceSet
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isDev

@OptIn(ExperimentalWasmDsl::class)
public abstract class DevKitCompilerConfigExtension
internal constructor(project: Project, private val isPlugin: Boolean) : DevKitVersionsDsl(project) {
    private val buildConfig: BuildConfigExtension = project.the()
    private val kotlinExtension: KotlinMultiplatformExtension = project.the()
    private val idea: IdeaModel = project.the()
    private val d8EnvSpec: D8EnvSpec = project.the()

    @get:Inject internal abstract val archiveOperations: ArchiveOperations

    private val commonMain = kotlinExtension.run { sourceSets.commonMain.get() }
    private val commonTestFixtures = kotlinExtension.sourceSets.create("commonTestFixtures")
    private val commonGenerateTests = kotlinExtension.sourceSets.create("generateTests")

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

    /** The fq-name of the class that implements `DevKitComponentRegistrar` */
    public val componentRegistrar: Property<String> =
        objects.property(
            provider {
                if (isPlugin) error("componentRegistrar must be set") else null
            }
        )

    /** The fq-name of the class that implements `DevKitCommandLineProcessor` */
    public val commandLineProcessor: Property<String> =
        objects.property(
            provider {
                if (isPlugin) error("commandLineProcessor must be set") else null
            }
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

    /**
     * Controls which version the IDE should prefer for shared source sets. This has no impact on
     * compilation whatsoever. This only impacts the dependencies the IDE sees for shared source
     * sets. If no version matches, the IDE will use the highest version available.
     */
    public val preferredVersionForSharedSourceSetsInIde: Property<KotlinToolingVersion> =
        objects.property()

    private val genServicesDir =
        layout.buildDirectory.dir("compiler-plugin-devkit/generated-resources")
    private val generateServices =
        tasks.register<GenerateServices>("generateServices") {
            generatedServicesDir.set(genServicesDir)
            services.putAll(
                provider {
                    buildMap {
                        if (componentRegistrar.isPresent)
                            put(
                                "org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar",
                                $$"$${pluginPackage.get()}.PluginInfo$REGISTRAR_CLASS$1$Plugin",
                            )
                        if (commandLineProcessor.isPresent)
                            put(
                                "org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor",
                                $$"$${pluginPackage.get()}.PluginInfo$PROCESSOR_CLASS$1$Plugin",
                            )
                    }
                }
            )
        }
    private val generateTestsCli: TaskProvider<GenerateTestsTask> =
        registerGenerateTestsTask("generateTestsCli", forIde = false) {
            description = "Generate compiler plugin tests for CLI"
        }
    private val generateTestsIde: TaskProvider<GenerateTestsTask> =
        registerGenerateTestsTask("generateTestsIde", forIde = true) {
            description = "Generate compiler plugin tests for IDE"
        }
    /**
     * Generates tests using [generateTestsClass], given the relevant test data directory, and
     * outputs them to [generatedTestsDir]
     */
    public val generateTests: TaskProvider<Task> =
        tasks.register("generateTests") {
            description = "Generate compiler plugin tests"
            enabled = false
            dependsOn(generateTestsCli, generateTestsIde)
        }

    private lateinit var surrogateTarget: DevKitTarget

    init {
        registerSupportedVersions()
        if (testAgainst.isEmpty()) testAgainst(kotlinToolingVersion)
    }

    private val entryPointCompilation =
        surrogateTarget.compilations.create(ENTRY_POINT_COMPILATION_NAME) {
            defaultSourceSet.kotlin.setSrcDirs(emptyList<Any>())
            defaultSourceSet.resources.setSrcDirs(listOf(genServicesDir.get()))
            tasks.named(processResourcesTaskName) { dependsOn(generateServices) }
        }

    private fun DevKitTarget.createGenerateTestsCompilation(): DevKitCompilation =
        compilations.create(GENERATE_TESTS_COMPILATION_NAME) {
            val forIde = this@createGenerateTestsCompilation.forIde
            associateWith(this@createGenerateTestsCompilation.testFixturesCompilation)
            defaultSourceSet {
                dependsOn(commonGenerateTests)
                val sourceSetName =
                    if (forIde) GENERATE_TESTS_IDE_SOURCE_SET_NAME
                    else GENERATE_TESTS_CLI_SOURCE_SET_NAME
                kotlin.setSrcDirs(listOf("src/$sourceSetName/kotlin"))
                resources.setSrcDirs(listOf("src/$sourceSetName/resources"))
            }
            // Uses latest EAP, so it never has to deal with kotlin-compiler-tests-for-ide
            forceKotlinVersion(KnownVersions.kotlinVersions.last(), forIde = false)
            (if (forIde) generateTestsIde else generateTestsCli) {
                classpath(runtimeDependencyFiles, output.allOutputs)
                enabled = true
            }
        }

    private var generateTestsIdeCompilation: DevKitCompilation? = null
    private var generateTestsCliCompilation: DevKitCompilation? = null

    private val mainJar: TaskProvider<Jar> =
        tasks.named<Jar>(surrogateTarget.artifactsTaskName) {
            from(entryPointCompilation.output.allOutputs)
            archiveAppendix.unset() // remove k2XX appendix
        }

    override fun registerVersion(version: KotlinToolingVersion, forIde: Boolean) {
        testAgainst(version, forIde = forIde)
    }

    /**
     * Which registered [testAgainst] version the generic `test` task delegates to. Defaults to
     * [DEFAULT_TEST_VERSION_PROPERTY], the Kotlin version applied to the build, or an arbitrary
     * target otherwise
     *
     * @see [defaultTestVersion] to set this using a version instead
     */
    public val defaultTestTarget: Property<DevKitTarget> =
        objects.property(
            providers.gradleProperty(DEFAULT_TEST_VERSION_PROPERTY).orNull?.let(::testAgainst)
                ?: testAgainst.findByName(kotlinToolingVersion.versionName)
                ?: testAgainst.last()
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

    private fun registerGenerateTestsTask(
        name: String,
        forIde: Boolean,
        action: GenerateTestsTask.() -> Unit,
    ): TaskProvider<GenerateTestsTask> =
        tasks.register<GenerateTestsTask>(name) {
            mainClass.set(generateTestsClass)
            workingDir = rootDir

            testAgainst.configureEach {
                if (this.forIde != forIde) return@configureEach
                test {
                    val outputDir = generatedTestsDir.dir(targetName)
                    generatedTestsDirs.add(outputDir)
                    this as AbstractKotlinSourceSet
                    argFiles(
                        files(
                            provider { withDependsOnClosure.map { it.resources.sourceDirectories } }
                        ),
                        outputDir,
                    )
                }
            }
            enabled = false
            action()
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

    private var hasIncludedCompanionBuilds = false

    init {
        listFromGradleProperty(COMPANION_LIBRARIES_PROPERTY).forEach {
            testDataLibraries {
                when {
                    it.isProjectPath -> common(project(it))
                    it.isIncludedBuildPath -> {
                        hasIncludedCompanionBuilds = true
                        common(
                            provider {
                                dependencies.create(
                                    "${project.group}:${Path(it).name}:${project.version}"
                                )
                            }
                        )
                    }
                    else -> common(it)
                }
            }
        }
    }

    /**
     * The highest Kotlin version every library in [testDataLibraries] was built using. This is used
     * to filter KMP tests for lower versions because KLIBS are not backwards compatible.
     *
     * If there are any [testDataLibraries] that come from included bulids, this defaults to the
     * minimum supported version, thus assuming that included builds are used for KLIB compatibility
     * Else, the current Kotlin bulid version is using, assuming that the libraries are built on it
     */
    public val companionLibrariesKotlinVersion: Property<KotlinToolingVersion> =
        objects.property(
            provider {
                // TODO work out using a more sophisticated method
                //  e.g. using kotlin-tooling-metadata.json
                if (hasIncludedCompanionBuilds) testAgainst.minOf { it.version }
                else kotlinToolingVersion
            }
        )

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
                packageName.set(pluginPackage)
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
        forIde: Boolean = version.isDev,
        action: DevKitTarget.() -> Unit = {},
    ): DevKitTarget {
        val target =
            testAgainst.findByName(version.versionName)
                ?: kotlinExtension
                    .createDevKitTarget(
                        version,
                        areDependenciesEmbedded = isPlugin,
                        forIde = forIde,
                        isSurrogate = !::surrogateTarget.isInitialized,
                        preferredVersionForIdeImport = preferredVersionForSharedSourceSetsInIde,
                    )
                    .also {
                        if (!::surrogateTarget.isInitialized) surrogateTarget = it
                        if (generateTestsCliCompilation == null && !it.forIde)
                            generateTestsCliCompilation = it.createGenerateTestsCompilation()
                        if (generateTestsIdeCompilation == null && it.forIde)
                            generateTestsIdeCompilation = it.createGenerateTestsCompilation()
                    }
        return target.apply(action)
    }

    /** Declares a version that the plugin should be tested against. This method is idempotent. */
    public fun testAgainst(
        version: String,
        forIde: Boolean = KotlinToolingVersion(version).isDev,
        action: DevKitTarget.() -> Unit = {},
    ): DevKitTarget = testAgainst(KotlinToolingVersion(version), forIde, action)

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    public fun versionHierarchy(template: DevKitHierarchyBuilder.() -> Unit) {
        kotlinExtension.applyHierarchyTemplate {
            withSourceSetTree(
                KotlinSourceSetTree.main,
                KotlinSourceSetTree(TEST_FIXTURES_COMPILATION_NAME),
                KotlinSourceSetTree.test,
            )

            common {
                withCompilations { true }
                group("ide") { withCompilations { (it.target as? DevKitTarget)?.forIde == true } }
                group("cli") { withCompilations { (it.target as? DevKitTarget)?.forIde == false } }
                template(
                    object : DevKitHierarchyBuilder {
                        override fun groupVersions(
                            name: String,
                            condition: (KotlinToolingVersion) -> Boolean,
                        ) {
                            group(name) {
                                withCompilations {
                                    val target = it.target
                                    // TODO expose IDE version somehow
                                    target is DevKitTarget &&
                                        condition(target.version.ideBranchPoint ?: target.version)
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    internal fun configure() {
        configurePlugins()
        configureTestTasks()
        configureMultiVersionJars()
        configureSourceSets()
        configureDependencies()
    }

    internal fun configurePlugins() {
        kotlinExtension.compilerOptions {
            optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
            optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
        }
        tasks.register<DefaultTestTask>("defaultTest") {
            enabled = false
            dependsOn(defaultTestTarget.map { it.testTask })
        }
        // Disable k2xxJar being added as friend path because it forces the task to run
        // This forces all versions to get compiled
        project.extra["kotlin.build.archivesTaskOutputAsFriendModule"] = false
        if (!isPlugin) return
        buildConfig.sourceSets.named(entryPointCompilation.defaultSourceSet.name) {
            packageName.set(pluginPackage)
            forClass("PluginInfo") {
                useKotlinOutput { internalVisibility = true }
                buildConfigField("PLUGIN_ID") {
                    type(String::class)
                    expression(pluginPackage.map { "\"$it\"" })
                }
                buildConfigField("REGISTRAR_CLASS") {
                    type("kotlin.reflect.KClass<*>")
                    expression(
                        componentRegistrar.map {
                            """
                            run {
                               class Plugin: org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerPluginRegistrar("$it") {
                                 override val pluginId: String = PLUGIN_ID
                               }
                               Plugin::class
                            }
                        """
                                .trimIndent()
                        }
                    )
                }
                buildConfigField("PROCESSOR_CLASS") {
                    type("kotlin.reflect.KClass<*>")
                    expression(
                        commandLineProcessor.map {
                            """
                            run {
                               class Plugin: org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCommandLineProcessor("$it") {
                                 override val pluginId: String = PLUGIN_ID
                               }
                               Plugin::class
                            }
                        """
                                .trimIndent()
                        }
                    )
                }
            }
        }
        mainJar {
            val includedDependencies =
                project.configurations.resolvable("includedDependencies") {
                    val from =
                        project.configurations.getByName(
                            surrogateTarget.mainCompilation.runtimeDependencyConfigurationName
                        )
                    extendsFrom(from)
                    attributes.addAllLater(from.attributes)

                    // ensure it sees the pre-transformed artifacts
                    attributes {
                        attribute(
                            KotlinCompilerVersion.attribute,
                            KotlinCompilerVersion.noVersion,
                        )
                    }
                    exclude(module = "kotlin-stdlib")
                }
            val includedEntryPoint =
                project.configurations.create("includedEntryPoint") {
                    val from =
                        project.configurations.getByName(
                            surrogateTarget.mainCompilation.runtimeDependencyConfigurationName
                        )
                    attributes.addAllLater(from.attributes)

                    // ensure it sees the pre-transformed artifacts
                    attributes {
                        attribute(
                            KotlinCompilerVersion.attribute,
                            KotlinCompilerVersion.noVersion,
                        )
                    }
                    exclude(module = "kotlin-stdlib")
                }
            dependencies {
                includedEntryPoint("$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION")
            }

            into(pluginPackage.map { "$PLUGIN_PATH/$it/$DEPENDENCIES_PATH" }) {
                from(
                    provider {
                        includedDependencies().elements().map {
                            archiveOperations.zipTree(it.asFile)
                        }
                    }
                ) {
                    eachFile {
                        // find the "expandedDir" that the fileTree uses (of the form zip-<hash>)
                        // and prepend it to the relative path so that each jar gets its own
                        // directory
                        var expandedDir = file
                        for (segment in this.relativeSourcePath.segments.asList().asReversed()) {
                            check(expandedDir.name == segment) {
                                "Expected $expandedDir to have name $segment from $relativeSourcePath"
                            }
                            expandedDir = expandedDir.parentFile
                        }
                        // relativePath is of the form someDestinationDir/relativeSourcePath/file
                        var destinationPath = relativePath
                        for (segment in this.relativeSourcePath.segments.asList().asReversed()) {
                            check(destinationPath.lastName == segment) {
                                "Expected $destinationPath to have name $segment from $relativeSourcePath"
                            }
                            destinationPath = destinationPath.parent!!
                        }
                        relativePath =
                            destinationPath
                                .append(false, expandedDir.name)
                                .append(relativeSourcePath)
                    }
                }
            }
            from(
                provider {
                    includedEntryPoint.elements().map { archiveOperations.zipTree(it.asFile) }
                }
            ) {
                exclude("$PLUGIN_PATH/")
            }
        }
    }

    internal fun configureTestTasks() {
        val testArtifacts = configurations.dependencyScope("testArtifacts")
        val testArtifactsWasm = configurations.dependencyScope("testArtifactsWasm")
        testAgainst.configureEach {
            testTask {
                val testArtifacts = testArtifacts.withKotlinVersion(version, forIde)
                val testArtifactsWasm =
                    testArtifactsWasm.withKotlinVersion(version, forIde) {
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
                +StringSystemPropertyArgumentProvider(
                    "$DEV_KIT_PROPERTY_PREFIX.versions",
                    provider { testAgainst.joinToString(",") { "${it.version}" } },
                )
                systemProperty("$DEV_KIT_PROPERTY_PREFIX.test.kotlin-version", version)
                +StringSystemPropertyArgumentProvider(
                    "$DEV_KIT_PROPERTY_PREFIX.build.kotlin-version",
                    companionLibrariesKotlinVersion.map { it.toString() },
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
            manifest {
                attributes(
                    "Kotlin-Compiler-Plugin-Multi-Release" to true,
                    "Kotlin-Compiler-Plugin-Since-Build" to ideaSinceBuild.asString,
                    "Kotlin-Compiler-Plugin-Until-Build" to ideaUntilBuild.asString { "$it.*" },
                )
                // TODO DefaultManifest is internal
                from(DefaultManifest(null)) {
                    eachEntry {
                        if (key == "Kotlin-Compiler-Plugin-Until-Build" && value == "null")
                            exclude()
                    }
                }
            }
        }
        testAgainst.configureEach {
            mainJar {
                into(pluginPackage.map { "$PLUGIN_PATH/$it/$VERSIONS_PATH/$version" }) {
                    from(mainCompilation.output.allOutputs)
                }
            }
        }
    }

    internal fun configureSourceSets() {
        versionHierarchy {}
        // TODO find a less ad-hoc way to identify test source sets
        kotlinExtension.sourceSets
            .named { it.endsWith("Test") }
            .configureEach {
                resources.setSrcDirs(listOf("src/$name/data"))
            }
        testAgainst.configureEach {
            testCompilation.apply {
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
        testAgainst.configureEach target@{
            compilations.configureEach {
                if (
                    this != entryPointCompilation &&
                        this != generateTestsCliCompilation &&
                        this != generateTestsIdeCompilation
                )
                    forceKotlinVersion(this@target.version, this@target.forIde)
            }
        }
        dependencies {
            setupCompilerVersionAttribute()
            testAgainst.configureEach { registerMultiReleasePluginTransform(version) }
            // for genereateTests*Compilation
            registerMultiReleasePluginTransform(KnownVersions.kotlinVersions.last())
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
                    "$DEV_KIT_GROUP:compiler-plugin-test:$DEV_KIT_VERSION"
                }
            )
        }

        entryPointCompilation.defaultSourceSet.dependencies {
            compileOnly(kotlin("compiler"))
            compileOnly(
                addRuntimeDependency.ifTrue {
                    "$DEV_KIT_GROUP:compiler-plugin-runtime:$DEV_KIT_VERSION"
                }
            )
        }
        // kotlin-compiler-tests-for-ide
        configurations.all {
            exclude(group = "org.jetbrains.kotlin", module = "tests-spec")
            exclude(group = "org.jetbrains.kotlin", module = "tests-compiler-utils")
            exclude(group = "org.jetbrains.kotlin", module = "test-infrastructure-utils.common")
            exclude(group = "org.jetbrains.kotlin", module = "test-infrastructure-utils")
            exclude(group = "org.jetbrains.kotlin", module = "test-infrastructure")
            exclude(group = "org.jetbrains.kotlin", module = "tests-common-new")
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
private fun Project.applyNeededPlugins() {
    pluginManager.apply {
        apply("org.jetbrains.kotlin.multiplatform")
        apply("com.github.gmazzo.buildconfig")
        apply("com.github.node-gradle.node")
        apply("idea")
        apply("maven-publish")
        apply("$DEV_KIT_GROUP.functional-test-publishing")
        apply(D8Plugin::class.java)
    }
}

public class DevKitCompilerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.applyNeededPlugins()
        val pluginDevKit =
            project.extensions.create<DevKitCompilerConfigExtension>("pluginDevKit", project, true)
        pluginDevKit.configure()
    }
}

public class DevKitCompilerLibrary : Plugin<Project> {
    override fun apply(project: Project) {
        project.applyNeededPlugins()
        val pluginDevKit =
            project.extensions.create<DevKitCompilerConfigExtension>("pluginDevKit", project, false)
        pluginDevKit.configure()
    }
}
