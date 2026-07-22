// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitTestFixturesTarget
import org.jetbrains.kotlin.compiler.plugin.devkit.baseVersionName
import org.jetbrains.kotlin.compiler.plugin.devkit.setClasspathProperty
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.compiler.plugin.devkit)
  java
}

val versionAliases =
  rootProject.isolated.projectDirectory
    .dir("compiler-compat")
    .file("version-aliases.txt")
    .asFile
    .readLines()
    .filterNot { it.isBlank() || it.startsWith('#') }
    .map(::KotlinToolingVersion)

pluginDevKit {
  pluginPackage = "$group.compiler"
  fun DevKitTestFixturesTarget.setupFixtures() {
    testFixtures {
      kotlin.srcDirs(
        "src/generator${KotlinToolingVersion(version.toKotlinVersion()).baseVersionName}/kotlin"
      )
      resources.srcDirs(
        listOf(
          "src/generator${KotlinToolingVersion(version.toKotlinVersion()).baseVersionName}/resources"
        )
      )
    }
  }
  testAgainstWithFixtures("2.4.0") { setupFixtures() }
  val k2320Target = testAgainstWithFixtures("2.3.20") { setupFixtures() }

  testAgainstWithFixtures("2.4.0-dev-2124") {
    testFixtures {
      kotlin.srcDirs(k2320Target.testFixtures.map { it.kotlin.sourceDirectories })
      java.srcDirs(k2320Target.testFixtures.map { it.java.sourceDirectories })
      resources.srcDirs(k2320Target.testFixtures.map { it.resources.sourceDirectories })
    }
  }
  val k2420Target = testAgainstWithFixtures("2.4.20-dev-6138") { setupFixtures() }

  versionAliases.forEach {
    // TODO provide easy way to do this in DevKit DSL
    if (it.toKotlinVersion() == KotlinVersion(2, 4, 20))
      testAgainstWithFixtures(it) {
        if (this == k2420Target) return@testAgainstWithFixtures
        testFixtures {
          kotlin.srcDirs(k2420Target.testFixtures.map { it.kotlin.sourceDirectories })
          java.srcDirs(k2420Target.testFixtures.map { it.java.sourceDirectories })
          resources.srcDirs(k2420Target.testFixtures.map { it.resources.sourceDirectories })
        }
      }
    else testAgainst(it)
  }

  defaultTestVersion(
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
      }
    }
  }
}

pluginDevKit.testDataLibraries {
  common(project(":runtime"))
  register("runtimeTracing", isTransitive = true) { jvm(project(":metro-trace")) }
  register("anvil") {
    jvm(libs.anvil.annotations)
    jvm(libs.anvil.annotations.optional)
  }
  register("kiAnvil") {
    jvm(libs.kotlinInject.anvil.runtime)
    jvm(libs.kotlinInject.runtime)
  }
  // include transitive in this case to grab compose and circuit runtimes
  register("circuit", isTransitive = true) {
    common(libs.circuit.runtime.presenter)
    common(libs.circuit.runtime.ui)
    common(libs.circuit.codegenAnnotations)
    js(libs.compose.ui)
    js(libs.kotlinInject.anvil.runtime.optional)
    js(libs.kotlinInject.runtime)
    js(libs.kotlinx.browser)
  }
  // include transitive in this case to grab jakarta and javax
  register("dagger", isTransitive = true) { jvm(libs.dagger.runtime) }
  register("daggerInterop") { jvm(project(":interop-dagger")) }
  register("hiltCore") { jvm(libs.hilt.core) }
  // include transitive in this case to grab jakarta and javax
  register("guice", isTransitive = true) {
    jvm(project(":interop-guice"))
    jvm(libs.guice)
  }
  register("javaxInterop") { jvm(project(":interop-javax")) }
  register("jakartaInterop") { jvm(project(":interop-jakarta")) }
}

dependencies {
  testFixturesApi(kotlin("compose-compiler-plugin"))

  testFixturesApi(project(":compiler"))
  testFixturesApi(project(":compiler-compat"))
  testFixturesCompileOnlyApi(project(":metro-common"))

  testFixturesRuntimeOnly(libs.ksp.symbolProcessing)
  testFixturesApi(libs.ksp.symbolProcessing.aaEmbeddable)
  testFixturesApi(libs.ksp.symbolProcessing.commonDeps)
  testFixturesApi(libs.ksp.symbolProcessing.api)
  testFixturesApi(libs.dagger.compiler)
  testFixturesApi(libs.hilt.compiler)
  testFixturesApi(libs.hilt.core)
  // Anvil KSP processors, only needs to be on the classpath at runtime since they're loaded via
  // ServiceLoader
  testFixturesRuntimeOnly(libs.anvil.kspCompiler)
}

val largeTestMode = providers.gradleProperty("metro.enableLargeTests").isPresent
val excludeJsBoxTests = providers.gradleProperty("metro.excludeJsBoxTests").isPresent

tasks.withType<Test> {
  outputs.upToDateWhen { false }

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

  if (largeTestMode) {
    filter { includeTestsMatching("*StressTest*") }
  } else {
    filter { excludeTestsMatching("*StressTest*") }
  }
  if (excludeJsBoxTests) {
    filter {
      excludeTestsMatching("dev.zacsweers.metro.compiler.*JsBoxTestGenerated*")
      excludeTestsMatching("dev.zacsweers.metro.compiler.*JsFastInitBoxTestGenerated*")
      excludeTestsMatching("dev.zacsweers.metro.compiler.*JsContributionProvidersBoxTestGenerated*")
    }
  }

  systemProperty("metro.shortLocations", "true")

  // Regenerate golden files in place: ./gradlew :compiler-tests:test -PupdateTestData=true
  if (providers.gradleProperty("updateTestData").isPresent) {
    systemProperty("kotlin.test.update.test.data", "true")
  }

  setClasspathProperty("ksp.testRuntimeClasspath", configurations.testRuntimeClasspath)
}
