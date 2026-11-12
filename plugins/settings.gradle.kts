pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    includeBuild("../version-resolution")
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
            // A release build overrides the checked-in `-SNAPSHOT` version; see AGENTS.md.
            providers.environmentVariable("DEVKIT_PUBLISH_VERSION").orNull?.let {
                version("devkit-version", it)
            }
        }
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases/")
    }
}

include("settings")
