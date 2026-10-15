# Initial setup

## Kotlin version

The DevKit needs Kotlin 2.3.20 or higher. Upgrade to 2.3.20 and make sure that your plugin works. Then continue with the
migration.
You don't need a version higher than 2.3.20 for the migration.

## Settings (`settings.gradle.kts`)

The DevKit is experimental. Add its EAP repository to the plugin repositories:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        ...
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
    }
}
```

Apply the settings plugin. Replace `<devkit-version>` with the latest version from the
[Releases page](https://github.com/Kotlin/compiler-plugin-dev-kit/releases/latest):

```kotlin
plugins {
    kotlin("compiler.plugin.devkit") version "<devkit-version>"
}
```

To resolve the DevKit dependencies, also add the repository to the dependency repositories:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        ...
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
    }
}
```

If you added the `dev`, `bootstrap`, or `intellij-dependencies` repositories, remove them. The settings plugin adds them.

[comment]: # (TODO #55)

Find your plugin ID. In the pre-DevKit template, the `build-config` setup passes the plugin ID explicitly. Set the plugin
ID in the settings:

```kotlin
pluginDevKit { pluginPackage("my.plugin.id") }
```

The DevKit uses this value as the plugin ID and as the package of the classes that it generates. If you don't set it,
the DevKit uses the project group.

A companion library is a library that your compiler plugin always expects to be present. The Gradle plugin adds it to
the user's project, and the DevKit adds it to the tests. Add your companion libraries:

```kotlin
pluginDevKit {
    // project path, for a project in the same build
    companionLibrary(":my-plugin-annotations")
    // artifact name, for a library with the same group and version (for example, in an included build)
    companionLibrary("my-plugin-annotations")
    // dependency notation
    companionLibrary("com.example:some-library:1.0.0")
}
```

In the pre-DevKit template, the companion library is `:plugin-annotations`.

Don't configure version support ranges in this step.
Make sure that your project still builds and runs.

Go on to [Gradle support plugin](2-gradle-support-plugin.md)
