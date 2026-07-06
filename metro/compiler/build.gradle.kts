// Copyright (C) 2024 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.drewhamilton.poko.gradle.PokoFirIdeMode
import dev.zacsweers.metro.gradle.RequiresIdeSupport
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

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
  alias(libs.plugins.shadow) apply false
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
  generateAtSync = true
  packageName("dev.zacsweers.metro.compiler")
  kotlin {
    useKotlinOutput {
      internalVisibility = true
      topLevelConstants = true
    }
  }
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

/**
 * Kotlin native requires the compiler plugin to embed its dependencies. (See
 * https://youtrack.jetbrains.com/issue/KT-53477)
 *
 * In order to do this, we replace the default jar task with a shadowJar task that embeds the
 * dependencies from the "embedded" configuration.
 */
val embedded = configurations.dependencyScope("embedded")

val embeddedClasspath = configurations.resolvable("embeddedClasspath") { extendsFrom(embedded) }

configurations.named("compileOnly").configure { extendsFrom(embedded) }

configurations.named("testImplementation").configure { extendsFrom(embedded) }

tasks.jar.configure { enabled = false }

val shadowJar =
  tasks.register("shadowJar", ShadowJar::class.java) {
    from(java.sourceSets.main.map { it.output })
    configurations.add(embeddedClasspath)

    // TODO these are relocated, do we need to/can we exclude these?
    //  exclude("META-INF/wire-runtime.kotlin_module")
    //  exclude("META-INF/okio.kotlin_module")
    dependencies {
      exclude(dependency("org.jetbrains:.*"))
      exclude(dependency("org.intellij:.*"))
      exclude(dependency("org.jetbrains.kotlin:.*"))
      exclude(dependency("dev.drewhamilton.poko:.*"))
    }

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()

    // Multiple embedded deps ship a JPMS `module-info.class` (incl. multi-release variants under
    // META-INF/versions/N/). They describe the unshaded namespace, so they're meaningless after
    // relocation.
    exclude("module-info.class")
    exclude("META-INF/versions/*/module-info.class")

    relocate("androidx.collection", "dev.zacsweers.metro.compiler.shaded.androidx.collection")
    relocate("androidx.tracing", "dev.zacsweers.metro.compiler.shaded.androidx.tracing")
    relocate("com.squareup.wire", "dev.zacsweers.metro.compiler.shaded.com.squareup.wire")
    relocate("com.squareup.okio", "dev.zacsweers.metro.compiler.shaded.com.squareup.okio")
    relocate("com.jakewharton.picnic", "dev.zacsweers.metro.compiler.shaded.com.jakewharton.picnic")
    relocate(
      "com.jakewharton.crossword",
      "dev.zacsweers.metro.compiler.shaded.com.jakewharton.crossword",
    )
    relocate(
      "kotlinx.serialization",
      "dev.zacsweers.metro.compiler.shaded.kotlinx.serialization",
    )
    relocate(
      "com.github.ajalt.mordant",
      "dev.zacsweers.metro.compiler.shaded.com.github.ajalt.mordant",
    )
    relocate("okio", "dev.zacsweers.metro.compiler.shaded.okio")
    // Relocate the metro runtime while excluding the compiler's own package
    relocate("dev.zacsweers.metro", "dev.zacsweers.metro.compiler.shaded.metro") {
      exclude("dev.zacsweers.metro.compiler.**")
      // Metro's annotation lookup still uses string-built runtime package names. Relocate classes,
      // but keep those string constants pointed at the public runtime package so the shaded
      // compiler
      // can run without an unshaded metro-common copy on the same classpath.
      skipStringConstants = true
    }
  }

/**
 * The wire and poko plugin add their dependencies automatically. This is not needed because we can
 * either ignore or embed them so we remove them.
 *
 * Note: this is done in `afterEvaluate` to run after wire:
 * https://github.com/square/wire/blob/34931324f09c5827a624c056e1040dc8d01cbcd9/wire-gradle-plugin/src/main/kotlin/com/squareup/wire/gradle/WirePlugin.kt#L75
 *
 * Same for poko:
 * https://github.com/drewhamilton/Poko/blob/7bde5b23cc65a95a894e0ba0fb305704c49382f0/poko-gradle-plugin/src/main/kotlin/dev/drewhamilton/poko/gradle/PokoGradlePlugin.kt#L19
 */
project.afterEvaluate {
  configurations.named("api") {
    dependencies.removeIf { it is ExternalDependency && it.group == "com.squareup.wire" }
  }
  configurations.named("implementation") {
    dependencies.removeIf { it is ExternalDependency && it.group == "dev.drewhamilton.poko" }
  }
}

pluginDevKit {
  addRuntimeDependency = false

  listOf(
      "2.3.0",
      "2.3.20",
      "2.4.0-dev-2124",
      "2.4.0",
      "2.4.20-dev-3583",
      "2.4.20-dev-6138",
      "2.4.20-Beta1",
    )
    .map(::KotlinToolingVersion)
    .forEach { developFor(it) }
}

for (c in arrayOf("apiElements", "runtimeElements")) {
  configurations.named(c) { artifacts.removeIf { true } }
  artifacts.add(c, shadowJar)
}

// The steps above make this project's consumable variants expose the *shaded* shadowJar. The
// devkit's java-test-fixtures/jvm-test-suite wiring then resolves that shaded JAR for the project's
// own main classes on the test classpaths. That is wrong for tests in two ways:
//   1. Compilation sees relocated `androidx.*` types (e.g. the shaded ScatterMap) instead of the
//      plain ones the test sources import.
//   2. The shadowJar embeds unrelocated deps (e.g. an older kotlinx-coroutines) that shadow the
//      real versions at runtime, causing NoSuchMethodError.
// Swap the shaded JAR out for the raw, unshaded main output. All of the compiler's runtime deps are
// already present unshaded on the test classpath via `embedded`, which extends testImplementation.
//
// The per-Kotlin-version test suites the devkit registers (e.g. `k240Test`) have no sources of
// their
// own; they re-run the shared `test` output against a version-specific runtime classpath. That
// classpath carries the KotlinCompilerVersion attribute, so the shadowJar reaches it via the
// MultiReleasePluginTransform under a version-suffixed name (e.g.
// `compiler-1.4.0-SNAPSHOT-2.4.0.jar`);
// match both the base and transformed names. Because those suites capture their Test task classpath
// before a source-set reassignment would take effect, the shaded JAR is stripped from the Test
// tasks
// directly for runtime, and from the source sets for compilation.
//
// `archiveFileName` (a plain string derived from base name/version/classifier) is read rather than
// `archiveFile` (a task output), so it resolves at configuration time without the shadowJar task
// having run. Resolve it to a plain String so the filter predicates below only capture serializable
// state (a script-level helper would capture the build script and break the configuration cache).
val shadowJarBaseName = shadowJar.get().archiveFileName.get().removeSuffix(".jar")

sourceSets
  .matching { it.name == "test" || it.name == "testFixtures" || it.name.endsWith("Test") }
  .configureEach {
    val mainOutput = sourceSets.main.get().output
    // Local copy so the (serialized) filter spec captures a plain String, not the build script.
    val baseName = shadowJarBaseName
    compileClasspath =
      mainOutput +
        compileClasspath.filter {
          it.name != "$baseName.jar" && !it.name.startsWith("$baseName-")
        }
  }

tasks.withType<Test>().configureEach {
  val mainOutput = sourceSets.main.get().output
  // Local copy so the (serialized) filter spec captures a plain String, not the build script.
  val baseName = shadowJarBaseName
  classpath =
    mainOutput +
      classpath.filter { it.name != "$baseName.jar" && !it.name.startsWith("$baseName-") }
}

dependencies {
  compileOnly(libs.kotlin.compiler)
  compileOnly(libs.kotlin.stdlib)
  compileOnly(libs.poko.annotations)
  compileOnly(libs.androidx.collection)

  add(embedded.name, project(":metro-common"))
  add(embedded.name, project(":runtime"))
  add(embedded.name, libs.androidx.collection)
  add(embedded.name, libs.androidx.tracing.wire)
  add(embedded.name, libs.picnic)
  add(embedded.name, libs.mordant)
  add(embedded.name, libs.wire.runtime)
  add(embedded.name, libs.kotlinx.serialization.json)
  add(embedded.name, project(":compiler-compat"))

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
  testImplementation(libs.androidx.collection)
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
