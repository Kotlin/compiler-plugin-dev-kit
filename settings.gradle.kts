pluginManagement {
    includeBuild("plugins")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    kotlin("compiler.plugin.devkit")
    id("org.jetbrains.kotlinx.artifacts-validator-plugin") version "0.0.2"
}

dependencyResolutionManagement {
    includeBuild("version-resolution")
    versionCatalogs {
        // Gradle already imported this one from the conventional `gradle/libs.versions.toml`, so
        // there is no `from` here - a second one is an error.
        create("libs") {
            // A release build overrides the checked-in `-SNAPSHOT` version; see AGENTS.md.
            providers.environmentVariable("DEVKIT_PUBLISH_VERSION").orNull?.let {
                version("devkit-version", it)
            }
        }
    }
    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases/")
    }
}

include("compiler-plugin-runtime")

include("compiler-plugin-test")

include("compiler-plugin-test-generator")

include("gradle-plugin-runtime")

rootProject.name = "compiler-plugin-dev-kit"
