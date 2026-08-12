pluginManagement {
    includeBuild("plugins")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins { kotlin("compiler.plugin.devkit") }

dependencyResolutionManagement {
    includeBuild("version-resolution")
    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases/")
    }
}

include("compiler-plugin-runtime")
include("compiler-plugin-test")

include("gradle-plugin-runtime")

rootProject.name = "compiler-plugin-dev-kit"
