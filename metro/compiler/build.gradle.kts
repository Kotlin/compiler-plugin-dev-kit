// Copyright (C) 2024 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import dev.drewhamilton.poko.gradle.PokoFirIdeMode
import dev.zacsweers.metro.gradle.RequiresIdeSupport

// Bootstrap: add the Metro compiler plugin JAR to the buildscript classpath from Maven Central.
// Buildscript resolution is NOT subject to project-level composite build dependency substitution,
// which avoids the circular task dependency (compileKotlin → shadowJar → compileKotlin) that
// occurs when Gradle substitutes dev.zacsweers.metro:compiler with project(:compiler).
buildscript {
  repositories { mavenCentral() }
  val bootstrapVersion =
    extra.properties["METRO_BOOTSTRAP_VERSION"]?.toString()
      ?: error("METRO_BOOTSTRAP_VERSION not set in gradle.properties")
  dependencies {
    classpath("dev.zacsweers.metro:compiler:$bootstrapVersion") { isTransitive = false }
  }
}

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.plugin.serialization)
  alias(libs.plugins.poko)
  id("org.jetbrains.kotlin.compiler.plugin.devkit.compiler-plugin")
  alias(libs.plugins.wire)
  alias(libs.plugins.shadow)
  id("metro.publish")
  // apply false to put metro on the classpath. Conditionally applied below.
  alias(libs.plugins.metro)
}

tasks.generateTests { enabled = false }

animalsniffer { ignore("org.*") }

metroArtifact {
  artifactId.set("compiler")
  name.set("Metro Compiler")
}

metro {
  @OptIn(RequiresIdeSupport::class) generateAssistedFactories.set(true)
  // We embed and shade the runtime in the compiler's shadow JAR
  automaticallyAddRuntimeDependencies.set(false)
}

poko {
  firIdeMode.set(PokoFirIdeMode.NONE)
}

// Extract the bootstrap compiler JAR from the buildscript classpath
val bootstrapVersion = extra.properties["METRO_BOOTSTRAP_VERSION"]?.toString()!!
val bootstrapJar =
  buildscript.configurations.getByName("classpath").files.single {
    it.name == "compiler-$bootstrapVersion.jar"
  }

configurations
  .matching { it.name.startsWith("kotlinCompilerPluginClasspath") }
  .configureEach {
    exclude(group = "dev.zacsweers.metro", module = "compiler")
    dependencies.add(project.dependencies.create(files(bootstrapJar)))
  }

buildConfig {
  kotlin { useKotlinOutput { topLevelConstants = true } }
  sourceSets.named("main") {
    buildConfigField(
      "String",
      "METRO_VERSION",
      providers.gradleProperty("VERSION_NAME").map { "\"$it\"" },
    )
    buildConfigField("String", "PLUGIN_ID", libs.versions.pluginId.map { "\"$it\"" })
    // Metadata version for compatibility checking. Increment when making breaking changes to
    // metro_metadata.proto
    buildConfigField("Int", "METADATA_VERSION", 1)
  }
  sourceSets.named("test") {
    buildConfigField("String", "JVM_TARGET", libs.versions.jvmTarget.map { "\"$it\"" })
  }
}

tasks.withType<Test> {
  maxParallelForks = Runtime.getRuntime().availableProcessors() * 2
  systemProperty("metro.buildDir", project.layout.buildDirectory.asFile.get().absolutePath)
  systemProperty("metro.diagnosticsRenderMode", "PLAIN")
}

val diagnosticsDocsFile = rootProject.layout.projectDirectory.file("docs/diagnostics.md")

// The compiler module's stdlib and kotlin-compiler are compileOnly (kotlinc provides them at
// runtime), so the doc generator needs them added back for plain JavaExec. kotlin-compiler is
// needed because MetroDiagnosticId entries reference their KtDiagnosticFactory transport.
val diagnosticsDocsRuntime =
  configurations.create("diagnosticsDocsRuntime") {
    isCanBeConsumed = false
  }

dependencies {
  diagnosticsDocsRuntime(libs.kotlin.stdlib)
  diagnosticsDocsRuntime(libs.kotlin.compiler)
}

val generateDiagnosticsDocs =
  tasks.register<JavaExec>("generateDiagnosticsDocs") {
    group = "documentation"
    description = "Generates docs/diagnostics.md from the MetroErrorCode registry."
    classpath = sourceSets.main.get().runtimeClasspath + diagnosticsDocsRuntime
    mainClass.set("dev.zacsweers.metro.compiler.diagnostics.DiagnosticsDocGenerator")
    args(diagnosticsDocsFile.asFile.absolutePath)
  }

val checkDiagnosticsDocs =
  tasks.register<JavaExec>("checkDiagnosticsDocs") {
    group = "verification"
    description = "Verifies docs/diagnostics.md is up to date with the MetroErrorCode registry."
    classpath = sourceSets.main.get().runtimeClasspath + diagnosticsDocsRuntime
    mainClass.set("dev.zacsweers.metro.compiler.diagnostics.DiagnosticsDocGenerator")
    args(diagnosticsDocsFile.asFile.absolutePath, "--check")
  }

tasks.named("check") { dependsOn(checkDiagnosticsDocs) }

wire { kotlin { javaInterop = false } }

tasks.shadowJar {
  // TODO these are relocated, do we need to/can we exclude these?
  //  exclude("META-INF/wire-runtime.kotlin_module")
  //  exclude("META-INF/okio.kotlin_module")
  dependencies { exclude(dependency("dev.drewhamilton.poko:.*")) }

  // Relocate the metro runtime while excluding the compiler's own package
  relocate("dev.zacsweers.metro", "dev.zacsweers.metro.compiler.shaded.metro") {
    exclude("dev.zacsweers.metro.compiler.**")
    // Metro's annotation lookup still uses string-built runtime package names. Relocate classes,
    // but keep those string constants pointed at the public runtime package so the shaded compiler
    // can run without an unshaded metro-common copy on the same classpath.
    skipStringConstants = true
  }
  relocate("dev.zacsweers.metro", "dev.zacsweers.metro")
}

/**
 * The poko plugin adds their dependencies automatically. This is not needed because we can either
 * ignore or embed them, so we remove them.
 *
 * Note: this is done in `afterEvaluate` to run after poko:
 * https://github.com/drewhamilton/Poko/blob/7bde5b23cc65a95a894e0ba0fb305704c49382f0/poko-gradle-plugin/src/main/kotlin/dev/drewhamilton/poko/gradle/PokoGradlePlugin.kt#L19
 */
project.afterEvaluate {
  configurations.named("implementation") {
    dependencies.removeIf { it is ExternalDependency && it.group == "dev.drewhamilton.poko" }
  }
}

val versionAliases =
  rootProject.isolated.projectDirectory
    .dir("compiler-compat")
    .file("version-aliases.txt")
    .asFile
    .readLines()
    .filterNot { it.isBlank() || it.startsWith('#') }

pluginDevKit {
  pluginPackage = "$group.compiler"
  versionAliases.forEach { testAgainst(it) }
  defaultTestVersion(
    providers.gradleProperty("metro.testCompilerVersion").getOrElse(libs.versions.kotlin.get())
  )
}

dependencies {
  compileOnly(libs.kotlin.stdlib)
  compileOnly(libs.poko.annotations)

  implementation(project(":metro-common"))
  implementation(project(":runtime"))
  implementation(libs.androidx.collection)
  implementation(libs.androidx.tracing.wire)
  implementation(libs.picnic)
  implementation(libs.mordant)
  implementation(libs.kotlinx.serialization.json)
  implementation(project(":compiler-compat"))

  testCompileOnly(libs.poko.annotations)

  testImplementation(project(":interop-dagger"))
  testImplementation(libs.kotlin.reflect)
  testImplementation(libs.kotlin.stdlib)

  // Cover for https://github.com/tschuchortdev/kotlin-compile-testing/issues/274
  testImplementation(libs.kotlin.aptEmbeddable)
  testImplementation("dev.zacsweers.kctfork:core:0.13.0")
  testImplementation("dev.zacsweers.kctfork:ksp:0.13.0")
  pluginDevKit.testAgainst.configureEach {
    testSuite {
      sources {
        if (version.major == 2 && version.minor == 4) {
          implementationConfigurationName("dev.zacsweers.kctfork:core:0.13.0")
          implementationConfigurationName("dev.zacsweers.kctfork:ksp:0.13.0")
        } else {
          implementationConfigurationName(libs.kct)
          implementationConfigurationName(libs.kct.ksp)
        }
      }
    }
  }
  testImplementation(libs.okio)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.truth)
  testImplementation(libs.coroutines)
  testImplementation(libs.coroutines.test)
  testImplementation(libs.dagger.compiler)
  testImplementation(libs.dagger.runtime)
  testImplementation(libs.anvil.annotations)
}

// Metro's compiler tests are JUnit 4 based: `kotlin.test.Test` maps to JUnit 4 and the shared
// `MetroCompilerTest` base class relies on JUnit 4 `@Rule`s (e.g. TemporaryFolder). The devkit's
// test fixtures pull in `kotlin-test-junit5` and switch the test tasks to `useJUnitPlatform()`,
// which remaps `kotlin.test.Test` to JUnit 5 Jupiter and silently skips the JUnit 4 rules (tests
// then fail with "the temporary folder has not yet been created"). Keep these tests on JUnit 4.
configurations.configureEach {
  val lower = name.lowercase()
  if (lower.endsWith("testcompileclasspath") || lower.endsWith("testruntimeclasspath")) {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-test-junit5")
  }
}

tasks.withType<Test>().configureEach { useJUnit() }
