package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.plugins.ide.internal.generator.generator.Generator
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
  `java-test-fixtures`
  id("com.github.node-gradle.node")
  id("idea")
}

@OptIn(ExperimentalWasmDsl::class)
project.plugins.apply(D8Plugin::class.java)

val annotationsRuntimeClasspath by configurations.dependencyScope("annotationsRuntimeClasspath") {
  isTransitive = false
}
val annotationsJvmRuntimeClasspath by configurations.resolvable("annotationsJvmRuntimeClasspath") {
  extendsFrom(annotationsRuntimeClasspath)
}
val annotationsJsRuntimeClasspath by configurations.resolvable("annotationsJsRuntimeClasspath") {
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
val jsRepl = configExtension.jsRepl.convention(
  javaClass.classLoader.getResourceAsStream("repl.js")?.readAllBytes()?.toString(Charsets.UTF_8)
)
val generateTestsClass = configExtension.generateTestsClass.convention("GenerateTestsKt")
val testGenDirectory = configExtension.testGenDirectory.convention(layout.buildDirectory.dir("test-gen"))
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

val testArtifacts: Configuration by configurations.creating
dependencies { configureTestArtifacts { testArtifacts(it) } }

tasks.test { configure(testArtifacts, annotationsJvmRuntimeClasspath, annotationsJsRuntimeClasspath) }

val KotlinToolingVersion.versionString get() = toString()

extraKotlinVersionsToTestAgainst.all {
  val testArtifacts = configurations.create("testArtifacts$versionString")
  val testClasspath =
    configurations.create("testClasspath$versionString") { extendsFrom(configurations.testRuntimeClasspath.get()) }

  val annotationsJvmRuntimeClasspath by configurations.resolvable("annotationsJvmRuntimeClasspath$versionString") {
    extendsFrom(annotationsRuntimeClasspath)
  }
  val annotationsJsRuntimeClasspath by configurations.resolvable("annotationsJsRuntimeClasspath$versionString") {
    extendsFrom(annotationsRuntimeClasspath)
    attributes {
      attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
      attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
    }
  }

  dependencies {
    configureTestArtifacts(versionString) {
      testArtifacts(it)
      annotationsJvmRuntimeClasspath.resolutionStrategy.force(it)
      annotationsJsRuntimeClasspath.resolutionStrategy.force(it)
    }
    configureTestClasspath(versionString) {
      testClasspath(it)
      testClasspath.resolutionStrategy.force(it)
    }
  }
  tasks.register<Test>("test$versionString") {
    description = "Run tests for $versionString"
    group = "verification"
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = testClasspath.asFileTree + sourceSets.test.get().output
    configure(testArtifacts, annotationsJvmRuntimeClasspath, annotationsJsRuntimeClasspath)
  }
}

sourceSets {
  test { java.srcDir(testGenDirectory) }
}

idea {
  // This is needed until IDEA fixes IDEA-339729.
  module.generatedSourceDirs.add(testGenDirectory.asFile.get())
}

inline fun DependencyHandlerScope.configureTestClasspath(version: String? = null, testClasspath: (Any) -> Unit) {
  testClasspath(kotlin("stdlib", version))
  testClasspath(kotlin("test-junit5", version))
  testClasspath(kotlin("compiler-internal-test-framework", version))
  testClasspath(kotlin("compiler", version))
}

dependencies {
  compileOnly(kotlin("compiler"))

  configureTestClasspath { testFixturesApi(it) }
  testFixturesRuntimeOnly("junit:junit:4.13.2") // TODO don't force junit version

  annotationsRuntimeClasspath(pluginAnnotations)
}

fun DependencyHandlerScope.configureTestArtifacts(version: String? = null, testArtifacts: (Any) -> Unit) {
  // Dependencies required to run the internal test framework.
  testArtifacts(kotlin("stdlib", version))
  testArtifacts(kotlin("stdlib-jdk8", version))
  testArtifacts(kotlin("reflect", version))
  testArtifacts(kotlin("test", version))
  testArtifacts(kotlin("script-runtime", version))
  testArtifacts(kotlin("annotations-jvm", version))

  testArtifacts(kotlin("stdlib-js", version))
  testArtifacts(kotlin("test-js", version))
}

buildConfig {
  useKotlinOutput {
    internalVisibility = true
  }

  packageName(group.toString())
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")
}

val jsReplOutputFile = layout.buildDirectory.file("js-test-repl/repl.js")

abstract class JsReplGeneratorTask: GeneratorTask<String>() {
  @get:Input
  @get:Optional
  abstract val defaultRepl: Property<String>

  init {
    generator = object: Generator<String> {
      override fun read(inputFile: File?): String? = inputFile?.readText()
      override fun defaultInstance(): String? = defaultRepl.orNull
      override fun configure(`object`: String?) {}
      override fun write(`object`: String?, outputFile: File?) {
        if (`object` != null && outputFile != null) outputFile.writeText(`object`)
      }
    }
  }
}

val createJsRepl by tasks.registering(JsReplGeneratorTask::class) {
  description = "Creates a JS Repl for use by the testing framework"
  defaultRepl = jsRepl
  outputFile = jsReplOutputFile.get().asFile
}

@OptIn(ExperimentalWasmDsl::class)
fun Test.configure(testArtifacts: Configuration, annotationsJvmRuntimeClasspath: Configuration, annotationsJsRuntimeClasspath: Configuration) {
  dependsOn(annotationsJvmRuntimeClasspath)
  dependsOn(annotationsJsRuntimeClasspath)
  dependsOn(createJsRepl)

  useJUnitPlatform()
  workingDir = rootDir

  systemProperty("annotationsRuntime.jvm.classpath", annotationsJvmRuntimeClasspath.asPath)
  systemProperty("annotationsRuntime.js.classpath", annotationsJsRuntimeClasspath.asPath)

  // Properties required to run the internal test framework.
  setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
  setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
  setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
  setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
  setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
  setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")

  systemProperty("idea.ignore.disabled.plugins", "true")
  systemProperty("idea.home.path", rootDir)

  // Properties required to run JS tests from the internal test framework.
  val d8EnvSpec = project.the<D8EnvSpec>()
  with(d8EnvSpec) { dependsOn(project.d8SetupTaskProvider) }

  setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-stdlib-js", "kotlin-stdlib-js")
  setLibraryProperty(testArtifacts, "org.jetbrains.kotlin.test.kotlin-test-js", "kotlin-test-js")

  systemProperty("javascript.engine.path.V8", d8EnvSpec.executable.get())
  systemProperty("javascript.engine.path.repl", "${jsReplOutputFile.get().asFile}")
  systemProperty("kotlin.js.test.root.out.dir", "${layout.buildDirectory.get().asFile}/js-test-output")
}

kotlin {
  compilerOptions {
    optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
  }
}

abstract class GenerateTests : JavaExec() {
  @InputFiles
  val testData: ConfigurableFileCollection = project.files()
}


val generateTests by tasks.registering(GenerateTests::class) {
  description = "Generate compiler plugin tests"
  testData.from(sourceSets.test.map { it.resources.sourceDirectories })
  outputs.dir(testGenDirectory)
    .withPropertyName("generatedTests")

  classpath = sourceSets.testFixtures.get().runtimeClasspath
  mainClass = generateTestsClass
  workingDir = rootDir
  args(
    testGenDirectory.get().asFile.absolutePath,
    sourceSets.test.get().resources.sourceDirectories.joinToString(",", transform = File::getAbsolutePath),
  )
}

tasks.compileTestKotlin {
  dependsOn(generateTests)
}

fun Test.setLibraryProperty(testArtifacts: Configuration, propName: String, jarName: String) {
  val path = testArtifacts.files
    .find { """$jarName-\d.*""".toRegex().matches(it.name) }
    ?.absolutePath
    ?: return
  systemProperty(propName, path)
}
