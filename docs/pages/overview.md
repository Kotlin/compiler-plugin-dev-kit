---
slug: /
---

# Get started

The Kotlin Compiler Plugin DevKit is a set of Gradle plugins and libraries for Kotlin compiler plugin development.
It builds, tests, and publishes one compiler plugin for many Kotlin compiler and IDE versions from one source tree.

:::warning
The DevKit is experimental. Send questions and feedback to the `#compiler` channel of the
[KotlinLang Slack](https://slack-chats.kotlinlang.org/), or
[file a GitHub issue](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/new).
:::

## What the DevKit does

- **Multiversioning**: You set a range of Kotlin and IDE versions. The DevKit finds the compiler versions in the
  range and compiles your code against each one.
- **Source compatibility**: A source-compatible change in the compiler, for example a new default parameter, doesn't
  break your plugin. Your users don't get a `ClassNotFoundException` or a `NoSuchMethodError`.
- **`expect` and `actual`**: Each compiler version is a Kotlin Multiplatform target. When a compiler API differs
  between versions, declare an `expect` in the shared code and an `actual` for each group of versions.
- **Tests**: JVM, JS, and Gradle functional tests run against each version. A dump file can differ between versions.
- **IDE support**: From IntelliJ IDEA 2026.3 on, your plugin works in IntelliJ-based IDEs with no registry option.

## Requirements

| Tool          | Version          |
|---------------|------------------|
| Kotlin        | 2.2.20 or higher |
| Gradle        | 9.2 or higher    |
| IntelliJ IDEA | 2025.2 or higher |

## Start a new plugin

Use the `devkit` branch of the
[compiler-plugin-template](https://github.com/Kotlin/compiler-plugin-template/tree/devkit). The template has a
compiler plugin, a Gradle support plugin, a companion library, and tests.

## Add the DevKit to an existing plugin

The [Migration guide](migration/0-migration-guide.md) has every step. A DevKit build has these two parts.

The settings script applies the settings plugin. The DevKit is in its EAP repository:

```kotlin title="settings.gradle.kts"
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
    }
}

plugins {
    kotlin("compiler.plugin.devkit") version "{{devkitVersion}}"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
    }
}
```

The compiler plugin project applies the DevKit plugin, and sets the versions and the entry points:

```kotlin title="compiler-plugin/build.gradle.kts"
plugins {
    kotlin("multiplatform")
    pluginDevKit("compiler-plugin")
}

pluginDevKit {
    cliVersions("2.2.20")
    ideaVersions("252")
    componentRegistrar = "com.example.MyComponentRegistrar"
    commandLineProcessor = "com.example.MyCommandLineProcessor"
}
```

## Develop with KEFS

Use [KEFS](https://github.com/Mr3zee/Kotlin-External-FIR-Support) during plugin development. KEFS hot-reloads the
plugin in the IDE and handles plugin exceptions. KEFS supports DevKit-based plugins from version 0.4.0 on.
