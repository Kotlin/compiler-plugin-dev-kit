pluginManagement {
    includeBuild("plugins")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins { kotlin("compiler.plugin.devkit") }

dependencyResolutionManagement {
    includeBuild("artifact-transform")
    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases/")
    }
}

include("compiler-plugin-runtime")

include("gradle-plugin-runtime")

rootProject.name = "compiler-plugin-dev-kit"
