pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    includeBuild("../artifact-transform")
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases/")
    }
}

include("settings")