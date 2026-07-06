package org.jetbrains.kotlin.compiler.plugin.devkit

import com.github.gmazzo.buildconfig.BuildConfigExtension
import java.io.File
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.attributes.Usage
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.api.GeneratorTask
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.internal.generator.generator.Generator
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.HasProject
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

public interface DevKitKotlinCompilerTestTarget : Named {
    override fun getName(): String = version.versionString

    public val versionName: String
        get() = version.versionName

    public val version: KotlinToolingVersion
    public val testSuite: NamedDomainObjectProvider<JvmTestSuite>
}

public interface DevKitKotlinCompilerTestFixturesTarget : DevKitKotlinCompilerTestTarget {
    public val testFixtures: NamedDomainObjectProvider<SourceSet>
}

public interface DevKitKotlinCompilerMainTarget : DevKitKotlinCompilerTestTarget {
    public val main: NamedDomainObjectProvider<SourceSet>
}

public interface DevKitKotlinCompilerTarget :
    DevKitKotlinCompilerMainTarget, DevKitKotlinCompilerTestFixturesTarget

private class DevKitKotlinCompilerTestTargetImpl(
    override val version: KotlinToolingVersion,
    override val testSuite: NamedDomainObjectProvider<JvmTestSuite>,
) : DevKitKotlinCompilerTestTarget {
    override fun equals(other: Any?): Boolean =
        other is DevKitKotlinCompilerTestTarget && other.version == version

    override fun hashCode(): Int = version.hashCode()
}

public fun Project.DevKitKotlinCompilerTestTarget(
    version: KotlinToolingVersion
): DevKitKotlinCompilerTestTarget =
    DevKitKotlinCompilerTestTargetImpl(
        version,
        project.extensions
            .getByType<TestingExtension>()
            .suites
            .register<JvmTestSuite>("${version.versionName}Test"),
    )

private class DevKitKotlinCompilerTestFixturesTargetImpl(
    override val version: KotlinToolingVersion,
    override val testSuite: NamedDomainObjectProvider<JvmTestSuite>,
    override val testFixtures: NamedDomainObjectProvider<SourceSet>,
) : DevKitKotlinCompilerTestFixturesTarget {
    override fun equals(other: Any?): Boolean =
        other is DevKitKotlinCompilerTestTarget && other.version == version

    override fun hashCode(): Int = version.hashCode()
}

public fun Project.DevKitKotlinCompilerTestFixturesTarget(
    version: KotlinToolingVersion
): DevKitKotlinCompilerTestFixturesTarget =
    DevKitKotlinCompilerTestFixturesTargetImpl(
        version,
        project.extensions
            .getByType<TestingExtension>()
            .suites
            .register<JvmTestSuite>("${version.versionName}Test"),
        project.extensions
            .getByType<SourceSetContainer>()
            .register("${version.versionName}TestFixtures"),
    )

private class DevKitKotlinCompilerMainTargetImpl(
    override val version: KotlinToolingVersion,
    override val testSuite: NamedDomainObjectProvider<JvmTestSuite>,
    override val main: NamedDomainObjectProvider<SourceSet>,
) : DevKitKotlinCompilerMainTarget {
    override fun equals(other: Any?): Boolean =
        other is DevKitKotlinCompilerTestTarget && other.version == version

    override fun hashCode(): Int = version.hashCode()
}

public fun Project.DevKitKotlinCompilerMainTarget(
    version: KotlinToolingVersion
): DevKitKotlinCompilerMainTarget =
    DevKitKotlinCompilerMainTargetImpl(
        version,
        project.extensions
            .getByType<TestingExtension>()
            .suites
            .register<JvmTestSuite>("${version.versionName}Test"),
        project.extensions.getByType<SourceSetContainer>().register(version.versionName),
    )

private class DevKitKotlinCompilerTargetImpl(
    override val version: KotlinToolingVersion,
    override val testSuite: NamedDomainObjectProvider<JvmTestSuite>,
    override val testFixtures: NamedDomainObjectProvider<SourceSet>,
    override val main: NamedDomainObjectProvider<SourceSet>,
) : DevKitKotlinCompilerTarget {
    override fun equals(other: Any?): Boolean =
        other is DevKitKotlinCompilerTestTarget && other.version == version

    override fun hashCode(): Int = version.hashCode()
}

public fun Project.DevKitKotlinCompilerTarget(
    version: KotlinToolingVersion
): DevKitKotlinCompilerTarget =
    DevKitKotlinCompilerTargetImpl(
        version,
        project.extensions
            .getByType<TestingExtension>()
            .suites
            .register<JvmTestSuite>("${version.versionName}Test"),
        project.extensions
            .getByType<SourceSetContainer>()
            .register("${version.versionName}TestFixtures"),
        project.extensions.getByType<SourceSetContainer>().register(version.versionName),
    )

public abstract class DevKitCompilerPluginConfigExtension(override val project: Project) :
    HasProject {
    public abstract val pluginAnnotations: Property<Project>
    public abstract val generateTestsClass: Property<String>
    public abstract val testGenDirectory: DirectoryProperty

    /**
     * Whether to add an implementation dependency on `compiler-plugin-runtime`. Plugins that
     * don't use its APIs can turn this off to avoid an unused dependency leaking into their
     * published POM.
     */
    public abstract val addRuntimeDependency: Property<Boolean>

    /**
     * Which registered [testAgainst] version the generic `test` task delegates to. Defaults to
     * the Kotlin Gradle Plugin version currently applied to this build. If no [testAgainst] entry
     * matches, `test` is left alone.
     */
    public abstract val defaultTestVersion: Property<KotlinToolingVersion>

    public abstract val testAgainst: NamedDomainObjectSet<DevKitKotlinCompilerTestTarget>
    public val developFor: NamedDomainObjectCollection<DevKitKotlinCompilerMainTarget> =
        testAgainst.withType<DevKitKotlinCompilerMainTarget>()
    public val withFixtures: NamedDomainObjectCollection<DevKitKotlinCompilerTestFixturesTarget> =
        testAgainst.withType<DevKitKotlinCompilerTestFixturesTarget>()

    public fun kotlinVersionsToTestAgainst(
        block: Action<NamedDomainObjectSet<DevKitKotlinCompilerTestTarget>>
    ): Unit = block.execute(testAgainst)

    public fun testAgainst(
        version: KotlinToolingVersion,
        action: Action<DevKitKotlinCompilerTestTarget> = Action {},
    ): DevKitKotlinCompilerTestTarget =
        project.DevKitKotlinCompilerTestTarget(version).also(testAgainst::add).also(action::execute)

    public fun testAgainstWithFixtures(
        version: KotlinToolingVersion,
        action: Action<DevKitKotlinCompilerTestFixturesTarget> = Action {},
    ): DevKitKotlinCompilerTestFixturesTarget =
        project.DevKitKotlinCompilerTestFixturesTarget(version).also(testAgainst::add).also(action::execute)

    public fun developFor(
        version: KotlinToolingVersion,
        action: Action<DevKitKotlinCompilerMainTarget> = Action {},
    ): DevKitKotlinCompilerMainTarget =
        project.DevKitKotlinCompilerMainTarget(version).also(testAgainst::add).also(action::execute)


    public fun developForWithFixtures(
        version: KotlinToolingVersion,
        action: Action<DevKitKotlinCompilerTarget> = Action {},
    ): DevKitKotlinCompilerTarget =
        project.DevKitKotlinCompilerTarget(version).also(testAgainst::add).also(action::execute)
}

public abstract class JsReplGeneratorTask : GeneratorTask<String>() {
    @get:org.gradle.api.tasks.Input
    @get:org.gradle.api.tasks.Optional
    public abstract val defaultRepl: Property<String>

    init {
        generator =
            object : Generator<String> {
                override fun read(inputFile: File?): String? = inputFile?.readText()

                override fun defaultInstance(): String? = defaultRepl.orNull

                override fun configure(`object`: String?) {}

                override fun write(`object`: String?, outputFile: File?) {
                    if (`object` != null && outputFile != null) outputFile.writeText(`object`)
                }
            }
    }
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
        }

        project.plugins.apply(D8Plugin::class.java)

        val kotlinExtension = extensions.getByType<KotlinJvmProjectExtension>()
        val sourceSets = extensions.getByType<SourceSetContainer>()
        val animalsniffer = extensions.getByType<AnimalSnifferExtension>()
        val buildConfig = extensions.getByType<BuildConfigExtension>()
        val idea = extensions.getByType<IdeaModel>()

        val annotationsRuntimeClasspath =
            configurations.dependencyScope("annotationsRuntimeClasspath") {
                isTransitive = false
            }
        val annotationsJvmRuntimeClasspath =
            configurations.resolvable("annotationsJvmRuntimeClasspath") {
                extendsFrom(annotationsRuntimeClasspath)
            }
        val annotationsJsRuntimeClasspath =
            configurations.resolvable("annotationsJsRuntimeClasspath") {
                extendsFrom(annotationsRuntimeClasspath)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
                    attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
                }
            }

        val pluginDevKit =
            project.extensions.create<DevKitCompilerPluginConfigExtension>("pluginDevKit", project)
        val pluginAnnotations = pluginDevKit.pluginAnnotations
        pluginDevKit.addRuntimeDependency.convention(true)
        pluginDevKit.defaultTestVersion.convention(kotlinToolingVersion)
        val generateTestsClass = pluginDevKit.generateTestsClass.convention("GenerateTestsKt")
        val testGenDirectory =
            pluginDevKit.testGenDirectory.convention(layout.buildDirectory.dir("test-gen"))

        // force configuration
        afterEvaluate { pluginDevKit.testAgainst.toList() }

        rootProject.childProjects.values.forEach {
            var noMultiplatformProjects = true
            it.plugins.withId("org.jetbrains.kotlin.multiplatform") {
                if (noMultiplatformProjects) {
                    pluginAnnotations.convention(it)
                    noMultiplatformProjects = false
                } else {
                    pluginAnnotations.convention(null)
                }
            }
        }

        val testArtifacts: Configuration = configurations.create("testArtifacts")

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
            testArtifacts: Configuration,
            propName: String,
            jarName: String,
        ) {
            val path =
                testArtifacts.files
                    .find { """$jarName-\d.*""".toRegex().matches(it.name) }
                    ?.absolutePath ?: return
            systemProperty(propName, path)
        }

        fun Test.configure(
            testArtifacts: Configuration,
            annotationsJvmRuntimeClasspath: Configuration,
            annotationsJsRuntimeClasspath: Configuration,
        ) {
            dependsOn(annotationsJvmRuntimeClasspath)
            dependsOn(annotationsJsRuntimeClasspath)
            dependsOn(createJsRepl)

            useJUnitPlatform()
            workingDir = rootDir

            systemProperty(
                "annotationsRuntime.jvm.classpath",
                annotationsJvmRuntimeClasspath.asPath,
            )
            systemProperty(
                "annotationsRuntime.js.classpath",
                annotationsJsRuntimeClasspath.asPath,
            )

            // Properties required to run the internal test framework.
            setLibraryProperty(
                testArtifacts,
                "org.jetbrains.kotlin.test.kotlin-stdlib",
                "kotlin-stdlib",
            )
            setLibraryProperty(
                testArtifacts,
                "org.jetbrains.kotlin.test.kotlin-stdlib-jdk8",
                "kotlin-stdlib-jdk8",
            )
            setLibraryProperty(
                testArtifacts,
                "org.jetbrains.kotlin.test.kotlin-reflect",
                "kotlin-reflect",
            )
            setLibraryProperty(
                testArtifacts,
                "org.jetbrains.kotlin.test.kotlin-test",
                "kotlin-test",
            )
            setLibraryProperty(
                testArtifacts,
                "org.jetbrains.kotlin.test.kotlin-script-runtime",
                "kotlin-script-runtime",
            )
            setLibraryProperty(
                testArtifacts,
                "org.jetbrains.kotlin.test.kotlin-annotations-jvm",
                "kotlin-annotations-jvm",
            )

            systemProperty("idea.ignore.disabled.plugins", "true")
            systemProperty("idea.home.path", rootDir)

            // Properties required to run JS tests from the internal test framework.
            val d8EnvSpec = project.the<D8EnvSpec>()
            with(d8EnvSpec) { dependsOn(project.d8SetupTaskProvider) }

            setLibraryProperty(
                testArtifacts,
                "org.jetbrains.kotlin.test.kotlin-stdlib-js",
                "kotlin-stdlib-js",
            )
            setLibraryProperty(
                testArtifacts,
                "org.jetbrains.kotlin.test.kotlin-test-js",
                "kotlin-test-js",
            )

            systemProperty("javascript.engine.path.V8", d8EnvSpec.executable.get())
            systemProperty("javascript.engine.path.repl", "${jsReplOutputFile.get().asFile}")
            systemProperty(
                "kotlin.js.test.root.out.dir",
                "${layout.buildDirectory.get().asFile}/js-test-output",
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
        }

        // Deferred so consuming build scripts get a chance to populate `testAgainst` and set
        // `defaultTestVersion` first. If a matching version is registered, `test` delegates to
        // its suite (see the `${versionName}Test` suites below) instead of running anything
        // itself; every other suite stays independently runnable by name.
        afterEvaluate {
            val target = pluginDevKit.testAgainst.find { it.version == pluginDevKit.defaultTestVersion.get() }
            if (target != null) {
                tasks.named<Test>("test") {
                    enabled = false
                    dependsOn(target.testSuite.get())
                }
            }
        }

        fun JavaExec.addTestSourceSet(set: SourceSet) {
            inputs.files(set.resources.sourceDirectories)
            args(
                testGenDirectory.dir(set.name).get().asFile.absolutePath,
                set.resources.sourceDirectories.joinToString(
                    ",",
                    transform = File::getAbsolutePath,
                ),
            )
        }

        val generateTests =
            tasks.register("generateTests", JavaExec::class) {
                description = "Generate compiler plugin tests"
                addTestSourceSet(sourceSets.named("test").get())
                outputs.dir(testGenDirectory).withPropertyName("generatedTests")

                classpath = sourceSets.named("testFixtures").get().runtimeClasspath
                mainClass.set(generateTestsClass)
                workingDir = rootDir
            }

        tasks.named("compileTestKotlin") { dependsOn(generateTests) }

        fun ResolutionStrategy.forceKotlinVersion(version: KotlinToolingVersion) = eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(version.versionString)
        }

        fun Configuration.withKotlinVersion(
            version: KotlinToolingVersion,
            action: ResolvableConfiguration.() -> Unit = {},
        ) =
            configurations.resolvable("$name${version.capitalizedVersionName}") {
                extendsFrom(this@withKotlinVersion)
                resolutionStrategy.forceKotlinVersion(version)
                action()
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
        }

        fun SourceSet.extendsFrom(
            otherSourceSet: Provider<SourceSet>,
            extractName: (SourceSet) -> String,
        ) {
            configurations.named(extractName(this)) {
                extendsFrom(
                    otherSourceSet.flatMap {
                        configurations.named(extractName(it))
                    }
                )
            }
        }

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
        pluginDevKit.developFor.configureEach {
            main {
                java.setSrcDirs(
                    listOf("$versionName/src/main/java", "$versionName/src/main/kotlin")
                )
                resources.setSrcDirs(listOf("${versionName}/src/main/resources"))
                forceKotlinVersion(version)
                dependencies {
                    compileOnlyConfigurationName(kotlin("compiler"))
                    // allow it to see shared main sources
                    // TODO test this
                    compileOnlyConfigurationName(
                        sourceSets.named("main").map { it.output.classesDirs }
                    )
                }
                extendsFrom(sourceSets.named("main")) { it.implementationConfigurationName }
                extendsFrom(sourceSets.named("main")) { it.compileOnlyConfigurationName }
            }
            tasks.named<Jar>("jar") {
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
        pluginDevKit.withFixtures.configureEach {
            testFixtures {
                java.setSrcDirs(
                    listOf(
                        "$versionName/src/testFixtures/java",
                        "$versionName/src/testFixtures/kotlin",
                    )
                )
                resources.setSrcDirs(listOf("${versionName}/src/testFixtures/resources"))
                forceKotlinVersion(version)
                dependencies {
                    implementationConfigurationName(project())
                    compileOnlyConfigurationName(kotlin("compiler"))
                    // allow it to see shared test fixture sources
                    compileOnlyConfigurationName(
                        sourceSets.named("testFixtures").map { it.output.classesDirs }
                    )
                }
                extendsFrom(sourceSets.named("testFixtures")) {
                    it.implementationConfigurationName
                }
                extendsFrom(sourceSets.named("testFixtures")) {
                    it.compileOnlyConfigurationName
                }
            }

            tasks.named<Jar>("testFixturesJar") {
                metaInf {
                    into("kotlin/plugin/versions/$version") {
                        // Ensures the directory exists in the jar even if `testFixtures` has no
                        // output.
                        from(versionMarker)
                        from(testFixtures.map { it.output }) {
                            exclude("META-INF/*kotlin_module")
                            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        }
                    }
                }
            }
        }
        pluginDevKit.testAgainst.configureEach {
            val testArtifacts = testArtifacts.withKotlinVersion(version)

            val annotationsJvmRuntimeClasspath = annotationsJvmRuntimeClasspath.flatMap {
                it.withKotlinVersion(version)
            }
            val annotationsJsRuntimeClasspath = annotationsJsRuntimeClasspath.flatMap {
                it.withKotlinVersion(version) {
                    attributes {
                        attribute(
                            Usage.USAGE_ATTRIBUTE,
                            objects.named(KotlinUsages.KOTLIN_RUNTIME),
                        )
                        attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
                    }
                }
            }

            testSuite {
                dependencies {
                    implementation(testFixtures(project()))
                    // allow it to see shared test sources
                    // TODO test this
                    compileOnly(files(sourceSets.named("test").map { it.output.classesDirs }))
                }
                sources {
                    java.setSrcDirs(
                        listOf("$versionName/src/test/java", "$versionName/src/test/kotlin")
                    )
                    resources.setSrcDirs(listOf("${versionName}/src/test/resources"))
                    addTestGenDir()
                    forceKotlinVersion(version)
                    generateTests { addTestSourceSet(this@sources) }
                    tasks.named(compileJavaTaskName) { dependsOn(generateTests) }

                    extendsFrom(sourceSets.named("test")) { it.implementationConfigurationName }
                    extendsFrom(sourceSets.named("test")) { it.compileOnlyConfigurationName }

                    configurations.named(runtimeClasspathConfigurationName) {
                        // force it to use version-specific jars when running tests
                        attributes.attribute(
                            KotlinCompilerVersion.attribute,
                            KotlinCompilerVersion(version),
                        )
                    }
                }

                targets.all {
                    testTask {
                        // Include common tests
                        testClassesDirs +=
                            files(sourceSets.named("test").map { it.output.classesDirs })
                        classpath += files(sourceSets.named("test").map { it.output })
                        configure(
                            testArtifacts.get(),
                            annotationsJvmRuntimeClasspath.get(),
                            annotationsJsRuntimeClasspath.get(),
                        )
                    }
                }
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

        sourceSets.named("test") { addTestGenDir() }

        dependencies {
            "compileOnly"(kotlin("compiler"))

            "testFixturesApi"(kotlin("stdlib"))
            "testFixturesApi"(kotlin("test-junit5"))
            "testFixturesApi"(kotlin("compiler-internal-test-framework"))
            "testFixturesApi"(kotlin("compiler"))
            "testFixturesRuntimeOnly"("junit:junit:4.13.2") // TODO don't force junit version

            annotationsRuntimeClasspath(pluginAnnotations)
        }

        // Deferred so consuming build scripts get a chance to call
        // `pluginDevKit.addRuntimeDependency.set(false)` before we decide whether to add it.
        afterEvaluate {
            if (pluginDevKit.addRuntimeDependency.get()) {
                dependencies {
                    "implementation"("$DEV_KIT_GROUP:compiler-plugin-runtime")
                    "testFixturesApi"(testFixtures("$DEV_KIT_GROUP:compiler-plugin-runtime"))
                }
            }
        }

        animalsniffer.sourceSets =
            listOf(sourceSets.named("main").get(), sourceSets.named("test").get())
        animalsniffer.excludeJars("kotlin-compiler*")
        animalsniffer.ignore("java.*", "javax.*", "org.omg.*", "org.w3c.dom.*")

        buildConfig.useKotlinOutput { internalVisibility = true }
        buildConfig.packageName(group.toString())
        buildConfig.buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")

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
