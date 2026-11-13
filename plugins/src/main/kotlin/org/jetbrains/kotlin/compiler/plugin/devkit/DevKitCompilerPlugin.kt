@file:Suppress("FINAL_UPPER_BOUND")

package org.jetbrains.kotlin.compiler.plugin.devkit

import com.github.gmazzo.buildconfig.BuildConfigExtension
import java.io.File
import javax.inject.Inject
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.DEPENDENCIES_PATH
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.PLUGIN_PATH
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.VERSIONS_PATH
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

@OptIn(ExperimentalWasmDsl::class)
public open class DevKitCompilerConfigExtension
internal constructor(project: Project, private val isPlugin: Boolean) :
    DevKitPropertiesDsl<DevKitTarget>(project, DevKitTarget::class) {
    private val buildConfig: BuildConfigExtension = project.the()
    private val kotlinExtension: KotlinMultiplatformExtension = project.the()
    private val idea: IdeaModel = project.the()
    private val d8EnvSpec: D8EnvSpec = project.the()

    internal open class Injected @Inject constructor(val archiveOperations: ArchiveOperations)

    internal val archiveOperations: ArchiveOperations =
        objects.newInstance<Injected>().archiveOperations

    private val commonMain = kotlinExtension.run { sourceSets.commonMain.get() }
    private val commonTestFixtures = kotlinExtension.sourceSets.create("commonTestFixtures")
    private val commonGenerateTests = kotlinExtension.sourceSets.create("generateTests")

    private val commonGenerateTestsCli =
        kotlinExtension.sourceSets.create(GENERATE_TESTS_CLI_SOURCE_SET_NAME)

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
     * The name of the class that the `generateTests*` tasks run. The default is
     * `<pluginPackage>.GenerateTestsKt`
     */
    public val generateTestsClass: Property<String> =
        objects.property(pluginPackage.map { "$it.GenerateTestsKt" })

    /** The directory where generated tests are placed. Defaults to build/test-gen */
    public val generatedTestsDir: DirectoryProperty =
        objects.directoryProperty().convention(layout.buildDirectory.dir("test-gen"))

    /**
     * Whether to add dependencies on the dev DevKit's modules: `compiler-plugin-runtime`,
     * `compiler-plugin-test`, and `compiler-plugin-test-generator`. Defaults to true.
     */
    public val addRuntimeDependency: Property<Boolean> = objects.property(true)

    /**
     * Whether to generate tests. Defaults to true for compiler plugins and false for compiler
     * libraries.
     */
    public val generateTestsEnabled: Property<Boolean> = objects.property(isPlugin)

    /**
     * Versions that this plugin should be tested against. By default, it includes the Kotlin
     * version applied to this build and everything listed in [TEST_AGAINST_PROPERTY].
     */
    public override val testAgainst: NamedDomainObjectCollection<DevKitTarget> =
        kotlinExtension.targets.withType<DevKitTarget>()

    /**
     * Controls which version the IDE should prefer for shared source sets. This has no impact on
     * compilation whatsoever. This only impacts the dependencies the IDE sees for shared source
     * sets. If no version matches, the IDE will use the highest version available.
     */
    public val preferredVersionForSharedSourceSetsInIde: Property<KotlinToolingVersion> =
        objects.property()

    /**
     * The Kotlin versions to classify dumps with. By default, [CLASSIFIED_DUMP_VERSIONS_PROPERTY]
     * if set, or the [testAgainst] versions.
     */
    public val classifiedDumpVersions: ListProperty<KotlinToolingVersion> =
        objects
            .listProperty<KotlinToolingVersion>()
            .convention(provider { testAgainst.map { it.version } })

    init {
        val versions = listFromGradleProperty(CLASSIFIED_DUMP_VERSIONS_PROPERTY)
        if (versions.isNotEmpty())
            classifiedDumpVersions.convention(versions.map(::KotlinToolingVersion))
    }

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

    private val collectTestData: TaskProvider<CollectTestDataTask> =
        tasks.register<CollectTestDataTask>("collectTestData") {
            rootDirPath.set(rootDir.absolutePath)
            targetFile.set(layout.buildDirectory.file("testDataInfo/testDataFilesList.txt"))
            filePatterns.set(listOf("**/*.kt", "**/*.kts", "**/*.kt.can-freeze-ide"))
            testAgainst.configureEach {
                test {
                    testDataFiles.from(provider { allResourceDirectories })
                }
            }
        }
    private val generateTestsCliNonJs: TaskProvider<GenerateTestsTask> =
        registerGenerateTestsTask(
            "generateTestsCliNonJs",
            targets = { it.isCliButNotJs },
        ) {
            description =
                "Generate compiler plugin tests for CLI versions before JS tests were introduced (i.e. below 2.3)"
        }

    private val generateTestsJs: TaskProvider<GenerateTestsTask> =
        registerGenerateTestsTask(
            "generateTestsJs",
            targets = { it.supportsJsTestInfrastructure },
        ) {
            description = "Generate compiler plugin tests for JS"
        }

    private val generateTestsIde: TaskProvider<GenerateTestsTask> =
        registerGenerateTestsTask("generateTestsIde", targets = { it.forIde }) {
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
            dependsOn(generateTestsCliNonJs, generateTestsJs, generateTestsIde)
        }

    private lateinit var surrogateTarget: DevKitTarget

    init {
        registerSupportedVersions()
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
            val supportsJsTestInfrastructure =
                this@createGenerateTestsCompilation.supportsJsTestInfrastructure
            associateWith(this@createGenerateTestsCompilation.testFixturesCompilation)
            defaultSourceSet {
                val sourceSetName =
                    when {
                        forIde -> GENERATE_TESTS_IDE_SOURCE_SET_NAME
                        supportsJsTestInfrastructure -> GENERATE_TESTS_JS_SOURCE_SET_NAME
                        else -> GENERATE_TESTS_CLI_NON_JS_SOURCE_SET_NAME
                    }
                kotlin.setSrcDirs(listOf("src/$sourceSetName/kotlin"))
                resources.setSrcDirs(listOf("src/$sourceSetName/resources"))
            }
            // Uses latest EAP, so it never has to deal with kotlin-compiler-tests-for-ide
            forceKotlinVersion(KnownVersions.kotlinVersions.last(), forIde = false)
            val task =
                when {
                    forIde -> generateTestsIde
                    supportsJsTestInfrastructure -> generateTestsJs
                    else -> generateTestsCliNonJs
                }
            task {
                classpath(runtimeDependencyFiles, output.allOutputs)
                enabled = true
            }
        }

    private var generateTestsIdeCompilation: DevKitCompilation? = null
    private var generateTestsCliNonJsCompilation: DevKitCompilation? = null
    private var generateTestsJsCompilation: DevKitCompilation? = null

    private val mainJar: TaskProvider<Jar> =
        tasks.named<Jar>(surrogateTarget.artifactsTaskName) {
            from(entryPointCompilation.output.allOutputs)
        }

    /** Creates a JS Repl for use by the testing framework */
    public val createJsRepl: TaskProvider<JsReplGeneratorTask> =
        tasks.register("createJsRepl", JsReplGeneratorTask::class) {
            description = "Creates a JS Repl for use by the testing framework"
            defaultRepl.convention(javaClass.classLoader.getResource("repl.js")?.readText())
            outputFile.set(layout.buildDirectory.file("js-test-repl/repl.js"))
        }

    private fun registerGenerateTestsTask(
        name: String,
        targets: (DevKitTarget) -> Boolean,
        action: GenerateTestsTask.() -> Unit,
    ): TaskProvider<GenerateTestsTask> {
        val generateTestsEnabled = this.generateTestsEnabled
        return tasks.register<GenerateTestsTask>(name) {
            mainClass.set(generateTestsClass)
            workingDir = rootDir
            val rootArgs = objects.listProperty<CommandLineArgumentProvider>()
            +NestedSystemPropertyArgumentProvider(
                "$DEV_KIT_PROPERTY_PREFIX.roots",
                rootArgs,
                delimiter = ";",
            )
            inputs
                .file(collectTestData.map { it.targetFile })
                .withPropertyName("testDataFilesList")
                .withPathSensitivity(PathSensitivity.RELATIVE)

            testAgainst.configureEach {
                if (!targets(this)) return@configureEach
                test {
                    val outputDir = generatedTestsDir.dir(targetName)
                    generatedTestsDirs.add(outputDir)
                    val inputFiles = files(provider { allResourceDirectories })

                    rootArgs.add(filesCommandLineArgumentProvider(inputFiles, outputDir))
                }
            }
            // createGenerateTestsCompilation enables the task later, so the property has to gate
            // it at execution time for late DSL overrides to work.
            onlyIf { generateTestsEnabled.get() }
            enabled = false
            action()
        }
    }

    init {
        testAgainst.configureEach {
            testCompilation.compileJavaTaskProvider { dependsOn(generateTests) }
            tasks.named(testCompilation.compileKotlinTaskName) { dependsOn(generateTests) }
        }
    }

    /**
     * The JS artifacts of every registered [testDataLibraries] entry, a klib for each library that
     * has a JS variant. [companionLibrariesKotlinVersion] reads the compiler version out of them.
     */
    private val klibs = objects.fileCollection()

    private val testArtifacts =
        configurations.dependencyScope("testArtifacts").also {
            dependencies {
                it(kotlin("stdlib"))
                it(kotlin("stdlib-jdk8"))
                it(kotlin("reflect"))
                it(kotlin("test"))
                it(kotlin("script-runtime"))
                it(kotlin("annotations-jvm"))
                it(kotlin("stdlib-js"))
                it(kotlin("test-js"))
            }
        }

    private val testArtifactsWasm =
        configurations.dependencyScope("testArtifactsWasm").also {
            dependencies {
                it(kotlin("stdlib-wasm-js"))
                it(kotlin("stdlib-wasm-wasi"))
                it(kotlin("test-wasm-js"))
                it(kotlin("test-wasm-wasi"))
            }
        }

    /** Libraries that all test data files can use, including [COMPANION_LIBRARIES_PROPERTY]'s */
    public val testDataLibraries: DevKitTestDataLibraries =
        objects.newInstance<DevKitTestDataLibrariesImpl>(this).also {
            register(it, "defaultTestDataLibraries", addToBuildConfig = false)
        }

    init {
        registerCompanionLibraries()
    }

    override fun companionLibrary(dependencyNotationOrSiblingName: String) {
        testDataLibraries {
            when {
                dependencyNotationOrSiblingName.isDependencyNotation ->
                    common(dependencyNotationOrSiblingName)
                else -> common(siblingDependency(dependencyNotationOrSiblingName))
            }
        }
    }

    override fun companionLibrary(project: Project, group: Any?, version: Any?) {
        testDataLibraries { common(project(project.path)) }
    }

    override val companionLibrariesKotlinVersion: Property<KotlinToolingVersion> =
        objects.property(
            provider {
                klibs.files.mapNotNull(::klibCompilerVersion).maxOrNull()
                    ?: testAgainst.minOf { it.version }
            }
        )

    /** Configure libraries that test data files can use */
    public fun testDataLibraries(block: DevKitTestDataLibraries.() -> Unit) {
        testDataLibraries.block()
    }

    internal fun register(
        library: DevKitTestDataLibrary,
        name: String,
        isTransitive: Boolean = true,
        addToBuildConfig: Boolean = true,
    ) {
        val jvmClasspath = "$name.jvm.classpath"
        val jsClasspath = "$name.js.classpath"
        val configuration =
            configurations.dependencyScope(name) {
                this.isTransitive = isTransitive
                fromDependencyCollector(library.common)
                excludeTestArtifacts()
            }
        val jvmConfiguration =
            configurations.resolvable("${name}Jvm") {
                extendsFrom(configuration)
                this.isTransitive = isTransitive
                fromDependencyCollector(library.jvm)
                excludeTestArtifacts()
            }
        val jsConfiguration =
            configurations.resolvable("${name}Js") {
                extendsFrom(configuration)
                this.isTransitive = isTransitive
                fromDependencyCollector(library.js)
                excludeTestArtifacts()
                attributes {
                    attribute(
                        Usage.USAGE_ATTRIBUTE,
                        objects.named(KotlinUsages.KOTLIN_RUNTIME),
                    )
                    attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
                }
            }
        klibs.from(jsConfiguration)
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

    /**
     * A test data library must not add its own copies of [testArtifacts].
     *
     * This reads declared dependencies only, so nothing resolves.
     */
    private fun Configuration.excludeTestArtifacts() {
        for (dependency in
            listOf(testArtifacts, testArtifactsWasm).flatMap { it.get().incoming.dependencies }) {
            val group = dependency.group ?: continue
            exclude(mapOf("group" to group, "module" to dependency.name))
        }
    }

    override fun testAgainst(
        version: KotlinToolingVersion,
        forIde: Boolean,
        action: DevKitTarget.() -> Unit,
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
                        if (generateTestsCliNonJsCompilation == null && it.isCliButNotJs)
                            generateTestsCliNonJsCompilation =
                                it.createGenerateTestsCompilation().apply {
                                    defaultSourceSet.dependsOn(commonGenerateTestsCli)
                                }
                        if (generateTestsJsCompilation == null && it.supportsJsTestInfrastructure)
                            generateTestsJsCompilation =
                                it.createGenerateTestsCompilation().apply {
                                    defaultSourceSet.dependsOn(commonGenerateTestsCli)
                                }
                        if (generateTestsIdeCompilation == null && it.forIde)
                            generateTestsIdeCompilation =
                                it.createGenerateTestsCompilation().apply {
                                    defaultSourceSet.dependsOn(commonGenerateTests)
                                }
                    }
        return target.apply(action)
    }

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
                val builder =
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

                        override fun groupVersionsCli(
                            name: String,
                            condition: (KotlinToolingVersion) -> Boolean,
                        ) {
                            group("cli") {
                                group(name) {
                                    withCompilations {
                                        val target = it.target
                                        target is DevKitTarget &&
                                            !target.forIde &&
                                            condition(target.version)
                                    }
                                }
                            }
                        }
                    }
                with(builder) {
                    /*
                    The JS test infra is 2.3.0+ and CLI-only. The group covers only the testFixtures
                    and test compilations, so it creates no `jsMain`.
                     */
                    groupVersionsCli("js") {
                        it.toKotlinVersion() >= MIN_JS_TEST_INFRASTRUCTURE_VERSION
                    }
                    group("cli") {
                        group("js") {
                            excludeCompilations {
                                it.name != TEST_FIXTURES_COMPILATION_NAME &&
                                    it.name != KotlinSourceSetTree.test.name
                            }
                        }
                    }

                    template()
                }
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
        buildConfig.sourceSets {
            testAgainst.configureEach {
                for (sourceSet in listOf(main, testFixtures, test)) named(sourceSet.name) {
                    generateTask {
                        enabled = false
                        outputDir.unset()
                    }
                }
            }
        }
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
        // Adapted from
        // https://github.com/infix-de/testBalloon/blob/main/testBalloon-compiler-plugin/build.gradle.kts
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
                    isCanBeConsumed = false
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
                    createJsRepl.map { it.outputFile.get().asFile },
                )
                +FileSystemPropertyArgumentProvider(
                    "kotlin.js.test.root.out.dir",
                    layout.buildDirectory.dir("js-test-output").map { it.asFile },
                )

                // Regenerate golden files: ./gradlew :compiler-plugin:test -PupdateTestData=true
                +StringSystemPropertyArgumentProvider(
                    "kotlin.test.update.test.data",
                    provider { gradleProperty("updateTestData").isPresent.toString() },
                )
                +StringSystemPropertyArgumentProvider(
                    "$DEV_KIT_PROPERTY_PREFIX.versions",
                    classifiedDumpVersions.map { it.joinToString(",") },
                )
                systemProperty("$DEV_KIT_PROPERTY_PREFIX.test.kotlin-version", version)
                +StringSystemPropertyArgumentProvider(
                    "$DEV_KIT_PROPERTY_PREFIX.build.kotlin-version",
                    companionLibrariesKotlinVersion.map { it.toString() },
                )
            }
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
                        if (
                            (key == "Kotlin-Compiler-Plugin-Since-Build" ||
                                key == "Kotlin-Compiler-Plugin-Until-Build") && value == "null"
                        )
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
                // 2 causal orderings are possible here for leaf source sets:
                // - targets existed first, with their source sets, then `configureSourceSets` was
                // called
                // - `configureSourceSets` was called first, then targets were registered
                // in the latter case, DevKitCompilationFactory clears the directories aptly
                // in the former case, resources.srcDirs.isEmpty(), so we leave it as is
                if (resources.srcDirs.isNotEmpty()) resources.setSrcDirs(listOf("src/$name/data"))
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
        commonGenerateTestsCli.dependsOn(commonGenerateTests)
    }

    internal fun configureDependencies() {
        testAgainst.configureEach target@{
            compilations.configureEach {
                if (
                    this != entryPointCompilation &&
                        this != generateTestsCliNonJsCompilation &&
                        this != generateTestsJsCompilation &&
                        this != generateTestsIdeCompilation
                )
                    forceKotlinVersion(this@target.version, this@target.forIde)
            }
        }
        dependencies {
            setupCompilerVersionAttribute()
            testAgainst.configureEach { registerMultiReleasePluginTransform(version) }
            // for generateTests* compilations
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
            // kotlin-test-junit5 pins junit-jupiter-api 5.10.1. The test framework of Kotlin
            // 2.4.0 and later calls AssertionsKt.fail_nonNullableLambda, which junit-jupiter 5.12
            // added. Because the test tasks run with useJUnitPlatform(), the BOM also aligns the
            // engine and the launcher.
            api(dependencies.platform("org.junit:junit-bom:5.14.4"))
            api(kotlin("compiler-internal-test-framework"))
            // TODO only add it for versions in "2.5.0-dev-9992"..<"2.5.20-dev-1201", i.e. b2c5e0d
            // until 4125732
            runtimeOnly(kotlin("scripting-jvm"))
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

        commonGenerateTests.dependencies {
            api(
                addRuntimeDependency.ifTrue {
                    "$DEV_KIT_GROUP:compiler-plugin-test-generator:$DEV_KIT_VERSION"
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
