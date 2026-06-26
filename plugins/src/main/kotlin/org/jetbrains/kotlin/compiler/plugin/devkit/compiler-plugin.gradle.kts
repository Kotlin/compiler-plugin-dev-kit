package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.plugins.ide.internal.generator.generator.Generator
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import ru.vyarus.gradle.plugin.animalsniffer.signature.BuildSignatureTask

plugins {
    kotlin("jvm")
    `jvm-test-suite`
    id("com.github.gmazzo.buildconfig")
    id("ru.vyarus.animalsniffer")
    `java-test-fixtures`
    id("com.github.node-gradle.node")
    id("idea")
}

repositories {
    mavenCentral()
    maven("https://redirector.kotlinlang.org/maven/bootstrap")
    maven("https://redirector.kotlinlang.org/maven/dev/")
}

@OptIn(ExperimentalWasmDsl::class) project.plugins.apply(D8Plugin::class.java)

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

interface PluginConfigExtension {
    val pluginAnnotations: Property<Project>
    val jsRepl: Property<String>
    val generateTestsClass: Property<String>
    val testGenDirectory: DirectoryProperty
    val extraKotlinVersionsToTestAgainst: DomainObjectSet<KotlinToolingVersion>
}

val configExtension = project.extensions.create<PluginConfigExtension>("pluginConfig")
val pluginAnnotations = configExtension.pluginAnnotations
val jsRepl =
    configExtension.jsRepl.convention(javaClass.classLoader.getResource("repl.js")?.readText())
val generateTestsClass = configExtension.generateTestsClass.convention("GenerateTestsKt")
val testGenDirectory =
    configExtension.testGenDirectory.convention(layout.buildDirectory.dir("test-gen"))
val extraKotlinVersionsToTestAgainst = configExtension.extraKotlinVersionsToTestAgainst

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

tasks.test {
    configure(
        testArtifacts,
        annotationsJvmRuntimeClasspath.get(),
        annotationsJsRuntimeClasspath.get(),
    )
}

abstract class GenerateTests : JavaExec() {
    @InputFiles val testData: ConfigurableFileCollection = project.files()
}

fun GenerateTests.addTestSourceSet(set: SourceSet) {
    testData.from(set.resources.sourceDirectories)
    args(
        testGenDirectory.dir(set.name).get().asFile.absolutePath,
        set.resources.sourceDirectories.joinToString(",", transform = File::getAbsolutePath),
    )
}

val generateTests =
    tasks.register("generateTests", GenerateTests::class) {
        description = "Generate compiler plugin tests"
        addTestSourceSet(sourceSets.test.get())
        outputs.dir(testGenDirectory).withPropertyName("generatedTests")

        classpath = sourceSets.testFixtures.get().runtimeClasspath
        mainClass = generateTestsClass
        workingDir = rootDir
    }

tasks.compileTestKotlin { dependsOn(generateTests) }

val KotlinToolingVersion.versionString
    get() = toString()

// Uses same convention as Metro
// https://github.com/ZacSweers/metro/tree/7e38de5535d66ea5491e155711fd9830c564924a/compiler-compat#version-naming-convention
val KotlinToolingVersion.baseVersionName
    get() = versionString.replace(".", "").replace("-", "_").lowercase()
val KotlinToolingVersion.versionName
    get() = "k$baseVersionName"
val KotlinToolingVersion.capitalizedVersionName
    get() = "K$baseVersionName"
val KotlinToolingVersion.featureName
    get() = versionName.replace("_", "")

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

private fun SourceSet.forceKotlinVersion(version: KotlinToolingVersion) = configurations {
    // TODO which of these are actually needed?
    named(implementationConfigurationName) { resolutionStrategy.forceKotlinVersion(version) }
    named(compileOnlyConfigurationName) { resolutionStrategy.forceKotlinVersion(version) }
    named(compileClasspathConfigurationName) { resolutionStrategy.forceKotlinVersion(version) }
    named(runtimeClasspathConfigurationName) { resolutionStrategy.forceKotlinVersion(version) }
}

// Based on Gradle test-fixtures sourceSet setup
// https://github.com/gradle/gradle/blob/2ad2f1e3a67ad8f2d07c532874e36d55a786c7e9/platforms/jvm/plugins-jvm-test-fixtures/src/main/java/org/gradle/api/plugins/JavaTestFixturesPlugin.java#L67
extraKotlinVersionsToTestAgainst.all {
    sourceSets.register(versionName) {
        forceKotlinVersion(this@all)
        project.java.registerFeature(featureName) { usingSourceSet(this@register) }
        dependencies {
            compileOnlyConfigurationName(kotlin("compiler"))
            implementationConfigurationName(project())
        }
    }

    val testArtifacts = testArtifacts.withKotlinVersion(this)

    val annotationsJvmRuntimeClasspath = annotationsJvmRuntimeClasspath.flatMap {
        it.withKotlinVersion(this)
    }
    val annotationsJsRuntimeClasspath = annotationsJsRuntimeClasspath.flatMap {
        it.withKotlinVersion(this) {
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
                attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
            }
        }
    }

    testing.suites.register<JvmTestSuite>("${versionName}Test") {
        dependencies {
            implementation(project().capabilities { requireFeature(featureName) })
            implementation(testFixtures(project()))
        }
        sources {
            addTestGenDir()
            forceKotlinVersion(this@all)
            generateTests { addTestSourceSet(this@sources) }
            tasks.named(compileJavaTaskName) { dependsOn(generateTests) }
        }

        targets.all {
            testTask {
                // Include common and version-specific tests
                testClassesDirs = sourceSets.test.get().output + testClassesDirs
                classpath = sourceSets.test.get().output + classpath
                configure(
                    testArtifacts.get(),
                    annotationsJvmRuntimeClasspath.get(),
                    annotationsJsRuntimeClasspath.get(),
                )
            }
        }

        tasks.check { dependsOn(this@register) }
    }
    generateSignature()
}

fun KotlinToolingVersion.generateSignature() {
    val signature = configurations.create("${versionName}Signature")
    dependencies {
        // TODO add all Kotlin lib signatures, not just compiler
        signature(kotlin("compiler", versionString))
        signature(kotlin("compiler-internal-test-framework", versionString))
    }
    val signatureGenTask =
        tasks.register<BuildSignatureTask>("${versionName}Signature") {
            signatures(configurations.signature)
            files(signature)
        }
    animalsniffer {
        signatures += files(signatureGenTask.map { it.outputFiles })
    }
}

sourceSets {
    test { addTestGenDir() }
}

fun SourceSet.addTestGenDir() =
    testGenDirectory.dir(name).let {
        java.srcDir(it)
        // This is needed until IDEA fixes IDEA-339729.
        idea.module.generatedSourceDirs.add(it.get().asFile)
    }

dependencies {
    compileOnly(kotlin("compiler"))
    val devKitGroup = "org.jetbrains.kotlin.compiler.plugin.devkit"
    if (group != devKitGroup) implementation("$devKitGroup:compiler-plugin-runtime")

    testFixturesApi(kotlin("stdlib"))
    testFixturesApi(kotlin("test-junit5"))
    testFixturesApi(kotlin("compiler-internal-test-framework"))
    testFixturesApi(kotlin("compiler"))
    if (group != devKitGroup) testFixturesApi(testFixtures("$devKitGroup:compiler-plugin-runtime"))
    testFixturesRuntimeOnly("junit:junit:4.13.2") // TODO don't force junit version

    annotationsRuntimeClasspath(pluginAnnotations)
}

kotlinToolingVersion.generateSignature()

animalsniffer {
    excludeJars("kotlin-compiler*")
    ignore("java.*", "javax.*", "org.omg.*", "org.w3c.dom.*")
    checkTestSources = true
}

buildConfig {
    useKotlinOutput {
        internalVisibility = true
    }

    packageName(group.toString())
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")
}

val jsReplOutputFile = layout.buildDirectory.file("js-test-repl/repl.js")

abstract class JsReplGeneratorTask : GeneratorTask<String>() {
    @get:Input @get:Optional abstract val defaultRepl: Property<String>

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

val createJsRepl =
    tasks.register("createJsRepl", JsReplGeneratorTask::class) {
        description = "Creates a JS Repl for use by the testing framework"
        defaultRepl = jsRepl
        outputFile = jsReplOutputFile.get().asFile
    }

@OptIn(ExperimentalWasmDsl::class)
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

    systemProperty("annotationsRuntime.jvm.classpath", annotationsJvmRuntimeClasspath.asPath)
    systemProperty("annotationsRuntime.js.classpath", annotationsJsRuntimeClasspath.asPath)

    // Properties required to run the internal test framework.
    setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
    setLibraryProperty(
        testArtifacts,
        "org.jetbrains.kotlin.test.kotlin-stdlib-jdk8",
        "kotlin-stdlib-jdk8",
    )
    setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
    setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
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
    setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-test-js", "kotlin-test-js")

    systemProperty("javascript.engine.path.V8", d8EnvSpec.executable.get())
    systemProperty("javascript.engine.path.repl", "${jsReplOutputFile.get().asFile}")
    systemProperty(
        "kotlin.js.test.root.out.dir",
        "${layout.buildDirectory.get().asFile}/js-test-output",
    )
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

fun Test.setLibraryProperty(testArtifacts: Configuration, propName: String, jarName: String) {
    val path =
        testArtifacts.files.find { """$jarName-\d.*""".toRegex().matches(it.name) }?.absolutePath
            ?: return
    systemProperty(propName, path)
}
