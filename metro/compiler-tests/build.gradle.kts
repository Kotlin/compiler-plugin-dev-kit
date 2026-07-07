// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitKotlinCompilerTestFixturesTarget
import org.jetbrains.kotlin.compiler.plugin.devkit.baseVersionName
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsCompilerAttribute
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.compiler.plugin.devkit)
  alias(libs.plugins.gradleTestRetry)
  java
}

project.plugins.apply(D8Plugin::class.java)

val versionAliases =
  rootProject.isolated.projectDirectory
    .dir("compiler-compat")
    .file("version-aliases.txt")
    .asFile
    .readLines()
    .filterNot { it.isBlank() || it.startsWith('#') }
    .map(::KotlinToolingVersion)

pluginDevKit {
  fun DevKitKotlinCompilerTestFixturesTarget.setupFixtures() {
    testFixtures {
      java.srcDirs(
        "src/generator${KotlinToolingVersion(version.toKotlinVersion()).baseVersionName}/kotlin"
      )
      resources.srcDirs(
        listOf(
          "src/generator${KotlinToolingVersion(version.toKotlinVersion()).baseVersionName}/resources"
        )
      )
    }
  }
  developForWithFixtures(KotlinToolingVersion("2.4.0")) { setupFixtures() }
  val k2320Target = developForWithFixtures(KotlinToolingVersion("2.3.20")) { setupFixtures() }

  // 2.4.0-dev-2124 predates the TestConfigurationBuilder rename (like 2.3.0/2.3.20/2.4.0), so
  // reuse generator2320's sources rather than a dedicated directory(generator240 doesn't fit:
  // its KotlinStandardLibrariesPathProvider impl assumes wasm stdlib APIs this dev build lacks.)
  developForWithFixtures(KotlinToolingVersion("2.4.0-dev-2124")) {
    testFixtures {
      java.srcDirs(k2320Target.testFixtures.map { it.java.sourceDirectories })
      resources.srcDirs(k2320Target.testFixtures.map { it.resources.srcDirs })
    }
  }
  val k2420Target =
    developForWithFixtures(KotlinToolingVersion("2.4.20-dev-6138")) { setupFixtures() }

  // Extend bare test coverage to any version-aliases.txt entry not already registered above, so
  // metro.testCompilerVersion always has a matching task to select.
  versionAliases
    .filterNot { alias -> testAgainst.any { it.version == alias } }
    .forEach {
      if (it.toKotlinVersion() == KotlinVersion(2, 4, 20))
        developForWithFixtures(it) {
          testFixtures {
            java.srcDirs(k2420Target.testFixtures.map { it.java.sourceDirectories })
            resources.srcDirs(k2420Target.testFixtures.map { it.resources.srcDirs })
          }
        }
      else testAgainst(it)
    }

  // Picks which single registered Kotlin version's test task actually runs when running test task
  defaultTestVersion =
    KotlinToolingVersion(
      providers.gradleProperty("metro.testCompilerVersion").getOrElse(libs.versions.kotlin.get())
    )
}

// Configure the compiler-version-related test properties per registered devkit test suite, so
// each `<version>Test` suite reports its own pinned version at runtime rather than a single
// global value.
pluginDevKit.testAgainst.configureEach {
  testSuite {
    targets.all {
      testTask {
        systemProperty("metro.test.compilerVersion", version.toString())
        systemProperty("metro.test.jvmTarget", libs.versions.jvmTarget.get())
        systemProperty("metro.test.buildCompilerVersion", libs.versions.kotlin.get())
        systemProperty(
          "metro.test.overrideCompilerVersion",
          true,
        )
      }
    }
  }
}

val metroRuntimeClasspath = configurations.create("metroRuntimeClasspath") { isTransitive = false }
val metroRuntimeKlibClasspath =
  configurations.create("metroRuntimeKlibClasspath") {
    isTransitive = false
    attributes {
      attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
      attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
      attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
      attribute(KotlinJsCompilerAttribute.jsCompilerAttribute, KotlinJsCompilerAttribute.ir)
    }
  }

val runtimeTracingClasspath = configurations.create("runtimeTracingClasspath")
val anvilRuntimeClasspath = configurations.create("anvilRuntimeClasspath") { isTransitive = false }
val kiAnvilRuntimeClasspath =
  configurations.create("kiAnvilRuntimeClasspath") { isTransitive = false }
// include transitive in this case to grab compose and circuit runtimes
val circuitRuntimeClasspath =
  configurations.create("circuitRuntimeClasspath") {
    attributes {
      // Force JVM variants
      // TODO in future non-jvm tests we need others
      attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
  }
val circuitRuntimeKlibClasspath =
  configurations.create("circuitRuntimeKlibClasspath") {
    exclude(group = "org.jetbrains.kotlin")
    attributes {
      attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
      attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
      attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
      attribute(KotlinJsCompilerAttribute.jsCompilerAttribute, KotlinJsCompilerAttribute.ir)
    }
  }

// include transitive in this case to grab jakarta and javax
val daggerRuntimeClasspath = configurations.create("daggerRuntimeClasspath") {}
val daggerInteropClasspath =
  configurations.create("daggerInteropClasspath") { isTransitive = false }
val hiltCoreClasspath = configurations.create("hiltCoreClasspath") { isTransitive = false }
// include transitive in this case to grab jakarta and javax
val guiceClasspath = configurations.create("guiceClasspath") {}
val javaxInteropClasspath = configurations.create("javaxInteropClasspath") { isTransitive = false }
val jakartaInteropClasspath =
  configurations.create("jakartaInteropClasspath") { isTransitive = false }
val jsKlibClasspath =
  configurations.create("jsKlibClasspath") {
    isTransitive = false
    attributes { attribute(KotlinPlatformType.attribute, KotlinPlatformType.js) }
  }
val wasmKlibClasspath =
  configurations.create("wasmKlibClasspath") {
    isTransitive = false
    attributes {
      attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java), "wasm")
    }
  }

dependencies {
  testFixturesApi("org.jetbrains.kotlin:kotlin-compose-compiler-plugin")

  testFixturesApi(project(":compiler"))
  testFixturesApi(project(":compiler-compat"))
  testFixturesCompileOnlyApi(project(":metro-common"))

  testFixturesRuntimeOnly(libs.ksp.symbolProcessing) // TODO might be unnecessary
  testFixturesApi(libs.ksp.symbolProcessing.aaEmbeddable)
  testFixturesApi(libs.ksp.symbolProcessing.commonDeps)
  testFixturesApi(libs.ksp.symbolProcessing.api)
  testFixturesApi(libs.dagger.compiler)
  testFixturesApi(libs.hilt.compiler)
  testFixturesApi(libs.hilt.core)

  metroRuntimeClasspath(project(":runtime"))
  metroRuntimeKlibClasspath(project(path = ":runtime", configuration = "jsRuntimeElements"))
  runtimeTracingClasspath(project(":metro-trace"))

  daggerInteropClasspath(project(":interop-dagger"))

  guiceClasspath(project(":interop-guice"))
  guiceClasspath(libs.guice)

  javaxInteropClasspath(project(":interop-javax"))
  jakartaInteropClasspath(project(":interop-jakarta"))

  anvilRuntimeClasspath(libs.anvil.annotations)
  anvilRuntimeClasspath(libs.anvil.annotations.optional)

  daggerRuntimeClasspath(libs.dagger.runtime)

  hiltCoreClasspath(libs.hilt.core)

  kiAnvilRuntimeClasspath(libs.kotlinInject.anvil.runtime)
  kiAnvilRuntimeClasspath(libs.kotlinInject.runtime)

  circuitRuntimeClasspath(libs.circuit.runtime.presenter)
  circuitRuntimeClasspath(libs.circuit.runtime.ui)
  circuitRuntimeClasspath(libs.circuit.codegenAnnotations)
  circuitRuntimeKlibClasspath(libs.circuit.runtime.presenter)
  circuitRuntimeKlibClasspath(libs.circuit.runtime.ui)
  circuitRuntimeKlibClasspath(libs.circuit.codegenAnnotations)
  circuitRuntimeKlibClasspath(libs.compose.ui)
  circuitRuntimeKlibClasspath(libs.kotlinInject.anvil.runtime.optional)
  circuitRuntimeKlibClasspath(libs.kotlinInject.runtime)
  circuitRuntimeKlibClasspath(libs.kotlinx.browser)

  jsKlibClasspath("org.jetbrains.kotlin:kotlin-stdlib-js")
  jsKlibClasspath("org.jetbrains.kotlin:kotlin-test-js")
  wasmKlibClasspath("org.jetbrains.kotlin:kotlin-stdlib-wasm-js")
  wasmKlibClasspath("org.jetbrains.kotlin:kotlin-stdlib-wasm-wasi")
  wasmKlibClasspath("org.jetbrains.kotlin:kotlin-test-wasm-js")
  wasmKlibClasspath("org.jetbrains.kotlin:kotlin-test-wasm-wasi")

  // Anvil KSP processors, only needs to be on the classpath at runtime since they're loaded via
  // ServiceLoader
  testFixturesRuntimeOnly(libs.anvil.kspCompiler)

  // Dependencies required to run the internal test framework.
  // Use the test compiler version because 2.3.20+ uses new APIs from here
  testRuntimeOnly("org.jetbrains.kotlin:kotlin-reflect")
  testRuntimeOnly(libs.kotlin.scriptRuntime)
  testRuntimeOnly(libs.kotlin.annotationsJvm)
}

tasks.named("generateTests") {
  enabled = false
}

val generateTests =
  tasks.register<JavaExec>("generateTests2") {
    inputs
      .dir(layout.projectDirectory.dir("src/test/data"))
      .withPropertyName("testData")
      .withPathSensitivity(PathSensitivity.RELATIVE)

    outputs.dir(layout.projectDirectory.dir("src/test/java"))

    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.zacsweers.metro.compiler.GenerateTestsKt")
    workingDir = rootDir

    // Larger heap size
    minHeapSize = "128m"
    maxHeapSize = "1g"

    // Larger stack size
    jvmArgs("-Xss1m")
  }

val largeTestMode = providers.gradleProperty("metro.enableLargeTests").isPresent
val excludeJsBoxTests = providers.gradleProperty("metro.excludeJsBoxTests").isPresent
val jsBoxTestRetries =
  providers.gradleProperty("metro.jsBoxTestRetries").map(String::toInt).orElse(0)

tasks.withType<Test> {
  outputs.upToDateWhen { false }
  dependsOn(runtimeTracingClasspath)

  // Inspo from https://youtrack.jetbrains.com/issue/KT-83440
  minHeapSize = "512m"
  maxHeapSize = if (largeTestMode) "5g" else "2g"
  jvmArgs(
    "-ea",
    "-XX:+UseCodeCacheFlushing",
    "-XX:ReservedCodeCacheSize=256m",
    "-XX:MaxMetaspaceSize=${if (largeTestMode) "512m" else "1g"}",
    "-XX:CICompilerCount=2",
    "-Djna.nosys=true",
  )

  dependsOn(metroRuntimeClasspath)
  dependsOn(metroRuntimeKlibClasspath)
  dependsOn(daggerInteropClasspath)
  dependsOn(hiltCoreClasspath)
  dependsOn(guiceClasspath)
  dependsOn(javaxInteropClasspath)
  dependsOn(jakartaInteropClasspath)
  dependsOn(circuitRuntimeClasspath)
  dependsOn(circuitRuntimeKlibClasspath)
  dependsOn(jsKlibClasspath)
  dependsOn(wasmKlibClasspath)
  inputs
    .dir(layout.projectDirectory.dir("src/test/data"))
    .withPropertyName("testData")
    .withPathSensitivity(PathSensitivity.RELATIVE)

  workingDir = rootDir

  if (providers.gradleProperty("metro.debugCompilerTests").isPresent) {
    testLogging {
      showStandardStreams = true
      showStackTraces = true

      // Set options for log level LIFECYCLE
      events("started", "passed", "failed", "skipped")
      setExceptionFormat("short")

      // Setting this to 0 (the default is 2) will display the test executor that each test is
      // running on.
      displayGranularity = 0
    }

    val outputDir = isolated.rootProject.projectDirectory.dir("tmp").asFile.apply { mkdirs() }

    jvmArgs(
      "-XX:+HeapDumpOnOutOfMemoryError", // Produce a heap dump when an OOM occurs
      "-XX:+CrashOnOutOfMemoryError", // Produce a crash report when an OOM occurs
      "-XX:+UseGCOverheadLimit",
      "-XX:GCHeapFreeLimit=10",
      "-XX:GCTimeLimit=20",
      "-XX:HeapDumpPath=$outputDir",
      "-XX:ErrorFile=$outputDir",
    )
  }

  useJUnitPlatform()

  retry {
    maxRetries.set(jsBoxTestRetries)
    failOnPassedAfterRetry.set(false)
    failOnSkippedAfterRetry.set(true)
  }

  if (largeTestMode) {
    filter { includeTestsMatching("*StressTest*") }
  } else {
    filter { excludeTestsMatching("*StressTest*") }
  }
  if (excludeJsBoxTests) {
    filter {
      excludeTestsMatching("dev.zacsweers.metro.compiler.JsBoxTestGenerated*")
      excludeTestsMatching("dev.zacsweers.metro.compiler.JsFastInitBoxTestGenerated*")
      excludeTestsMatching("dev.zacsweers.metro.compiler.JsContributionProvidersBoxTestGenerated*")
    }
  }

  val testRuntimeClasspath = configurations.testRuntimeClasspath
  setLibraryProperty("kotlin-stdlib", "jar", testRuntimeClasspath)
  setLibraryProperty("kotlin-stdlib-jdk8", "jar", testRuntimeClasspath)
  setLibraryProperty("kotlin-stdlib-jdk8", "jar", testRuntimeClasspath, "kotlin.full.stdlib.path")
  setLibraryProperty("kotlin-reflect", "jar", testRuntimeClasspath)
  setLibraryProperty("kotlin-reflect", "jar", testRuntimeClasspath, "kotlin.reflect.jar.path")
  setLibraryProperty("kotlin-test", "jar", testRuntimeClasspath)
  setLibraryProperty("kotlin-script-runtime", "jar", testRuntimeClasspath)
  setLibraryProperty("kotlin-annotations-jvm", "jar", testRuntimeClasspath)
  setLibraryProperty(
    "kotlin-annotations-jvm",
    "jar",
    testRuntimeClasspath,
    "kotlin.mockJDK.annotations.path",
  )
  setLibraryProperty("kotlin-stdlib-js", "klib", jsKlibClasspath)
  setLibraryProperty("kotlin-test-js", "klib", jsKlibClasspath)
  setLibraryProperty("kotlin-stdlib-wasm-js", "klib", wasmKlibClasspath)
  setLibraryProperty("kotlin-stdlib-wasm-wasi", "klib", wasmKlibClasspath)
  setLibraryProperty("kotlin-test-wasm-js", "klib", wasmKlibClasspath)
  setLibraryProperty("kotlin-test-wasm-wasi", "klib", wasmKlibClasspath)
  setLibraryProperty("kotlin-common-stdlib", "jar", testRuntimeClasspath)
  setLibraryProperty("kotlin-stdlib-web", "jar", testRuntimeClasspath)

  val d8EnvSpec = project.the<D8EnvSpec>()
  dependsOn(d8EnvSpec.run { project.d8SetupTaskProvider })
  systemProperty("javascript.engine.path.V8", d8EnvSpec.executable.get())
  systemProperty("javascript.engine.path.repl", layout.projectDirectory.file("repl.js").asFile)
  systemProperty(
    "kotlin.js.test.root.out.dir",
    layout.buildDirectory.dir("js-test-output").get().asFile.absolutePath,
  )

  systemProperty("metro.shortLocations", "true")

  // Regenerate golden files in place: ./gradlew :compiler-tests:test -PupdateTestData=true
  if (providers.gradleProperty("updateTestData").isPresent) {
    systemProperty("kotlin.test.update.test.data", "true")
  }

  systemProperty("metroRuntime.classpath", metroRuntimeClasspath.asPath)
  systemProperty("metroRuntime.klibClasspath", metroRuntimeKlibClasspath.asPath)
  systemProperty("runtimeTracing.classpath", runtimeTracingClasspath.asPath)
  systemProperty("anvilRuntime.classpath", anvilRuntimeClasspath.asPath)
  systemProperty("kiAnvilRuntime.classpath", kiAnvilRuntimeClasspath.asPath)
  systemProperty("daggerRuntime.classpath", daggerRuntimeClasspath.asPath)
  systemProperty("daggerInterop.classpath", daggerInteropClasspath.asPath)
  systemProperty("hiltCore.classpath", hiltCoreClasspath.asPath)
  systemProperty("guice.classpath", guiceClasspath.asPath)
  systemProperty("javaxInterop.classpath", javaxInteropClasspath.asPath)
  systemProperty("jakartaInterop.classpath", jakartaInteropClasspath.asPath)
  systemProperty("circuit.classpath", circuitRuntimeClasspath.asPath)
  systemProperty("circuit.klibClasspath", circuitRuntimeKlibClasspath.asPath)
  jvmArgumentProviders.add(
    ClasspathSystemPropertyArgumentProvider("ksp.testRuntimeClasspath", testRuntimeClasspath)
  )

  // Properties required to run the internal test framework.
  systemProperty("idea.ignore.disabled.plugins", "true")
  systemProperty("idea.home.path", rootDir)
}

fun Test.setLibraryProperty(
  jarName: String,
  extension: String,
  configuration: Configuration,
  propName: String = "org.jetbrains.kotlin.test.$jarName",
) {
  val regex = "$jarName-\\d.*$extension".toRegex()
  val path = configuration.files.find { regex.matches(it.name) }?.absolutePath ?: return
  systemProperty(propName, path)
}

// Lazy overload: resolves `configuration` only when this task forks its test JVM, via
// jvmArgumentProviders.
fun Test.setLibraryProperty(
  jarName: String,
  extension: String,
  configuration: Provider<out FileCollection>,
  propName: String = "org.jetbrains.kotlin.test.$jarName",
) {
  jvmArgumentProviders.add(LibraryPathArgumentProvider(propName, jarName, extension, configuration))
}

private class LibraryPathArgumentProvider(
  @get:Internal val propName: String,
  @get:Internal val jarName: String,
  @get:Internal val extension: String,
  @get:Internal val configuration: Provider<out FileCollection>,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> {
    val regex = "$jarName-\\d.*$extension".toRegex()
    val path = configuration.get().files.find { regex.matches(it.name) }?.absolutePath
    return if (path != null) listOf("-D$propName=$path") else emptyList()
  }
}

private class ClasspathSystemPropertyArgumentProvider(
  @get:Internal val propName: String,
  @get:Internal val configuration: Provider<out FileCollection>,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> = listOf("-D$propName=${configuration.get().asPath}")
}
