# IDE support

The IDE loads your plugin in a project that applies your Gradle support plugin.

| IDE                       | Setup                                                                             |
|---------------------------|-----------------------------------------------------------------------------------|
| IntelliJ IDEA 2026.3 on   | None.                                                                             |
| IntelliJ IDEA before that | Set the registry key `kotlin.k2.only.bundled.compiler.plugins.enabled` to `false`. |

## Supported IDE builds

```kotlin title="build.gradle.kts"
pluginDevKit {
    ideaVersions("252", max = 263, includeRc = true, includeEap = true)
}
```

`ideaVersions` does two things:

- It registers the compiler of each IntelliJ IDEA and Android Studio build in the range as an
  [IDE version](versions.md#ide-versions).
- It writes the range to the manifest of the plugin jar:

```text title="META-INF/MANIFEST.MF"
Kotlin-Compiler-Plugin-Since-Build: 252
Kotlin-Compiler-Plugin-Until-Build: 263.*
```

To declare a range without a change to the registered versions:

```kotlin title="build.gradle.kts"
import org.jetbrains.kotlin.compiler.plugin.devkit.IdeBuildNumber

pluginDevKit {
    ideaSinceBuild = IdeBuildNumber("252")
    ideaUntilBuild = 263
}
```

## What an IDE version gets

- The JVM compiler plugin tests. It gets no JS tests and no functional tests.
- The `ide` [version group](multiversioning/0-multiversioning.md#group-the-versions), with the `ideMain` source set.

In an IDE build that you didn't register, the DevKit
[selects the closest version](multiversioning/0-multiversioning.md#version-selection-at-run-time).

## Develop the plugin

- [KEFS](https://github.com/Mr3zee/Kotlin-External-FIR-Support) hot-reloads the plugin in the IDE.
- The [Kotlin Compiler DevKit](https://github.com/JetBrains/kotlin-compiler-devkit) IDE plugin runs the tests of a
  test data file from the editor.
- [`preferredVersionForSharedSourceSetsInIde`](multiversioning/0-multiversioning.md#versions-in-the-ide) sets the
  compiler version that the IDE shows for the shared source sets.
