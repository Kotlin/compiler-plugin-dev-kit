// Copyright (C) 2024 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
pluginManagement {
  includeBuild("build-logic")
  includeBuild("../plugins")
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
    maven("https://redirector.kotlinlang.org/maven/bootstrap")
    maven("https://redirector.kotlinlang.org/maven/dev/")
    // Publications used by IJ
    // https://kotlinlang.slack.com/archives/C7L3JB43G/p1757001642402909
    maven("https://redirector.kotlinlang.org/maven/intellij-dependencies/")
  }
  plugins { id("com.gradle.develocity") version "4.4.3" }
}

dependencyResolutionManagement {
  includeBuild("..")
  repositories {
    mavenCentral()
    google()
    maven("https://redirector.kotlinlang.org/maven/bootstrap")
    maven("https://redirector.kotlinlang.org/maven/dev/")
    // Publications used by IJ
    // https://kotlinlang.slack.com/archives/C7L3JB43G/p1757001642402909
    maven("https://redirector.kotlinlang.org/maven/intellij-dependencies/")
  }
}

plugins { id("com.gradle.develocity") }

rootProject.name = "metro"

include(
  ":compiler",
  ":compiler-compat",
  ":compiler-tests",
  ":gradle-plugin",
  ":interop-dagger",
  ":interop-javax",
  ":interop-jakarta",
  ":interop-guice",
  ":metro-trace",
  ":metro-common",
  ":metrox-android",
  ":metrox-viewmodel",
  ":metrox-viewmodel-compose",
  ":runtime",
)

val VERSION_NAME: String by extra.properties

develocity {
  buildScan {
    termsOfUseUrl = "https://gradle.com/terms-of-service"
    termsOfUseAgree = "yes"

    tag(if (System.getenv("CI").isNullOrBlank()) "Local" else "CI")
    tag(VERSION_NAME)

    obfuscation {
      username { "Redacted" }
      hostname { "Redacted" }
      ipAddresses { addresses -> addresses.map { "0.0.0.0" } }
    }
  }
}

enableFeaturePreview("NO_IMPLICIT_LOOKUP_IN_PARENT_PROJECTS")
