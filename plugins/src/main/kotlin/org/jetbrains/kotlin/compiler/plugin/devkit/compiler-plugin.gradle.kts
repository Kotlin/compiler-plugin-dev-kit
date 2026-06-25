package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.plugins.internal.JavaPluginHelper
import org.gradle.api.plugins.jvm.internal.DefaultJvmFeature
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal
import org.gradle.internal.component.external.model.ProjectDerivedCapability
import org.gradle.jvm.component.internal.DefaultJvmSoftwareComponent
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

repositories {
  mavenCentral()
  maven("https://redirector.kotlinlang.org/maven/bootstrap")
  maven("https://redirector.kotlinlang.org/maven/dev/")
}

@OptIn(ExperimentalWasmDsl::class)
project.plugins.apply(D8Plugin::class.java)

@Suppress("UnstableApiUsage")
val annotationsRuntimeClasspath by configurations.dependencyScope("annotationsRuntimeClasspath") {
  isTransitive = false
}
@Suppress("UnstableApiUsage")
val annotationsJvmRuntimeClasspath by configurations.resolvable("annotationsJvmRuntimeClasspath") {
  extendsFrom(annotationsRuntimeClasspath)
}
@Suppress("UnstableApiUsage")
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
val jsRepl = configExtension.jsRepl.convention(javaClass.classLoader.getResource("repl.js")?.readText())
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

tasks.test { configure(testArtifacts, annotationsJvmRuntimeClasspath, annotationsJsRuntimeClasspath) }

val KotlinToolingVersion.versionString get() = toString()

// Uses same convention as Metro
// https://github.com/ZacSweers/metro/tree/7e38de5535d66ea5491e155711fd9830c564924a/compiler-compat#version-naming-convention
val KotlinToolingVersion.baseVersionName get() = versionString.replace(".", "").replace("-", "_").lowercase()
val KotlinToolingVersion.versionName get() = "k$baseVersionName"
val KotlinToolingVersion.capitalizedVersionName get() = "K$baseVersionName"

fun ResolutionStrategy.forceKotlinVersion(version: KotlinToolingVersion) = eachDependency {
  if (requested.group == "org.jetbrains.kotlin") useVersion(version.versionString)
}

@Suppress("UnstableApiUsage")
fun Configuration.withKotlinVersion(
  version: KotlinToolingVersion,
  action: ResolvableConfiguration.() -> Unit = {}
) = configurations.resolvable("$name${version.capitalizedVersionName}") {
  extendsFrom(this@withKotlinVersion)
  resolutionStrategy.forceKotlinVersion(version)
  action()
}

// Based on Gradle test-fixtures sourceSet setup
// https://github.com/gradle/gradle/blob/2ad2f1e3a67ad8f2d07c532874e36d55a786c7e9/platforms/jvm/plugins-jvm-test-fixtures/src/main/java/org/gradle/api/plugins/JavaTestFixturesPlugin.java#L67
private fun makeKotlinVersionFeature(name: String, version: KotlinToolingVersion): JvmFeatureInternal {
  val projectInternal = project as ProjectInternal
  return DefaultJvmFeature(
    name,
    sourceSets.maybeCreate(name),
    setOf(ProjectDerivedCapability(projectInternal, name)),
    projectInternal,
    false,
    false
  ).apply {
    // override versions
    // TODO which of these are actually needed?
    implementationConfiguration.resolutionStrategy.forceKotlinVersion(version)
    compileOnlyConfiguration.resolutionStrategy.forceKotlinVersion(version)
    compileClasspathConfiguration.resolutionStrategy.forceKotlinVersion(version)
    runtimeClasspathConfiguration.resolutionStrategy.forceKotlinVersion(version)
    // add feature
    val component = JavaPluginHelper.getJavaComponent(project) as DefaultJvmSoftwareComponent
    component.features.add(this)
    component.addVariantsFromConfiguration(
      runtimeElementsConfiguration,
      JavaConfigurationVariantMapping("runtime", true, runtimeClasspathConfiguration)
    )
    // sourceSet depends on the project and the compiler
    dependencies {
      implementationConfiguration(dependencyFactory.create(project))
      compileOnlyConfiguration(kotlin("compiler"))
    }
  }
}

extraKotlinVersionsToTestAgainst.all {
  val mainFeature = makeKotlinVersionFeature(versionName, this)

  val testArtifacts by testArtifacts.withKotlinVersion(this)
  val testClasspath by configurations.testRuntimeClasspath.get().withKotlinVersion(this)

  val annotationsJvmRuntimeClasspath by annotationsJvmRuntimeClasspath.withKotlinVersion(this)
  val annotationsJsRuntimeClasspath by annotationsJsRuntimeClasspath.withKotlinVersion(this) {
    attributes {
      attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
      attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
    }
  }

  tasks.check.get().dependsOn(tasks.register<Test>("test$capitalizedVersionName") {
    description = "Run tests for $versionString"
    group = "verification"
    testClassesDirs = tasks.test.get().testClassesDirs
    // Include testRuntimeClasspath, test classes themselves, and version-specific code
    classpath = testClasspath.asFileTree + sourceSets.test.get().output + mainFeature.sourceSet.output
    configure(testArtifacts, annotationsJvmRuntimeClasspath, annotationsJsRuntimeClasspath)
  })
}

sourceSets {
  test { java.srcDir(testGenDirectory) }
}

idea {
  // This is needed until IDEA fixes IDEA-339729.
  module.generatedSourceDirs.add(testGenDirectory.asFile.get())
}

dependencies {
  compileOnly(kotlin("compiler"))

  testFixturesApi(kotlin("stdlib"))
  testFixturesApi(kotlin("test-junit5"))
  testFixturesApi(kotlin("compiler-internal-test-framework"))
  testFixturesApi(kotlin("compiler"))
  testFixturesRuntimeOnly("junit:junit:4.13.2") // TODO don't force junit version

  annotationsRuntimeClasspath(pluginAnnotations)
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
