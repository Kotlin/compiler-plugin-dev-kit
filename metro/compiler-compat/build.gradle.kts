import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.compiler.plugin.devkit)
  id("metro.publish")
}

metroArtifact {
  artifactId.set("compiler-compat")
  name.set("Metro Compiler Compat")
}

buildConfig {
  packageName("dev.zacsweers.metro.compiler.compat")
  kotlin {
    useKotlinOutput {
      internalVisibility = true
      topLevelConstants = true
    }
  }
  buildConfigField(
    "kotlin.collections.Map<String, String>",
    "BUILT_IN_COMPILER_VERSION_ALIASES",
    providers
      .fileContents(layout.projectDirectory.file("ide-mappings.txt"))
      .asText
      // Known tags-to-real version mappings for IDE builds.
      // Android Studio canary builds report a fake version like "2.3.255-dev-255".
      // The real version can be found by checking the IntelliJ tag for the studio build number:
      // https://github.com/JetBrains/intellij-community/blob/idea/<intellij-version>/.idea/libraries/kotlinc_kotlin_compiler_common.xml
      .map { text ->
        text
          .lineSequence()
          .filter { it.isNotBlank() && !it.startsWith("#") }
          .joinToString(prefix = "mapOf(\n", postfix = "\n)", separator = "\n,") { line ->
            val (from, to) = line.split('=', limit = 2)
            "  \"$from\" to \"$to\""
          }
      },
  )
}

dependencies {
  testImplementation(libs.truth)
}

// ignore tests provided by devkit
tasks.generateTests { enabled = false }

tasks.withType<Test> { failOnNoDiscoveredTests = false }

pluginDevKit {
  // TODO extract into file
  val versions =
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
  versions.forEachIndexed { i, version ->
    developFor(version) {
      main {
        for (lower in versions.subList(0, i)) dependOn(
          developFor.named(lower.toString()).flatMap { it.main }
        )
      }
    }
  }
}

fun SourceSet.dependOn(other: Provider<SourceSet>) {
  val otherClasses = other.map { it.output.classesDirs }
  dependencies {
    compileOnlyConfigurationName(files(otherClasses))
  }
  output.dir(otherClasses)
}
