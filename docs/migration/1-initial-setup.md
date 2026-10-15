# Initial setup

## Kotlin version

The DevKit needs a minimum of Kotlin 2.3.20, so please upgrade and ensure your plugin works aptly before proceeding with
the rest of the migration.
There's no reason to upgrade to a version higher than 2.3.20 during the setup.

## Settings (`settings.gradle.kts`)

Because the DevKit is still highly experimental, you'll need to add:

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

Apply the plugin (you can find the latest DevKit version in
the [Releases page](https://github.com/Kotlin/compiler-plugin-dev-kit/releases/latest)):

```kotlin
plugins {
    kotlin("compiler.plugin.devkit") version "<devkit-version>"
}
```

Also add:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        ...
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
    }
}
```

so you get the DevKit dependencies!

You can remove manually-added `dev`, `bootstrap`, and `intellij-dependencies` repositories because the settings plugin
adds them for you.

[comment]: # (TODO #55)

Find your plugin ID. If you're using the pre-DevKit template, you'll have `build-config` setup that explicitly passes
the plugin ID. Configure it in the settings directly:

```kotlin
pluginDevKit { pluginPackage("my.plugin.id") }
```

If you have "companion" libraries, i.e. libraries that your compiler plugin always expects to exist, and thus should be
added by the Gradle plugin and be available in tests, add them:

```kotlin
pluginDevKit {
    // project reference (if it's a project in the same build)
    companionLibrary(":my-plugin-annotations")
    // name (if it's under the same group and version, e.g. if it's in an included build)
    companionLibrary("my-plugin-annotations")
    // dependency notation
    companionLibrary("com.example:some-library:1.0.0")
}
```

In the pre-DevKit template, `:plugin-annotations` is the companion library you should add.

Don't configure any version support ranges for now.
Your project should still build and run successfully.

Go on to [Gradle Support Plugin](2-gradle-support-plugin.md)
