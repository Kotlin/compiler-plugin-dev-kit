# Build structure

A compiler plugin project has these parts:

- One compiler plugin.
- One Gradle support plugin. It is a `KotlinCompilerPluginSupportPlugin` that applies the compiler plugin to the
  build of a user.
- Support plugins for other build tools, for example Maven or Amper.
- Zero or more companion libraries. A companion library is a library that the compiler plugin needs on the classpath
  of the user's code. Such a library usually has annotations.

## Supported structures

| Structure       | Layout                                                                                          | Kotlin range     |
|-----------------|-------------------------------------------------------------------------------------------------|------------------|
| Single build    | One build has every part.                                                                       | 3 major versions |
| Composite build | The companion libraries are in an included build with an older Kotlin Gradle plugin.            | 5 major versions |
| Three builds    | The companion libraries and the Gradle support plugin each have a build.                        | 5 major versions |

Use the composite build. The
[compiler-plugin-template](https://github.com/Kotlin/compiler-plugin-template/tree/devkit) has this structure.

Use three builds only to apply the compiler plugin to its own companion libraries.

## Why one build supports fewer versions

KLIBs aren't forward compatible. For example, a Kotlin 2.3 project can't use a multiplatform library that the 2.4
compiler built. So the companion libraries need the oldest Kotlin version that you support, and the compiler plugin
needs a newer one.

Examples:

- A single build with the Kotlin Gradle plugin 2.4.0 and the experimental `kotlin.compilerVersion = "2.5.0"` supports
  Kotlin 2.4.0 to 2.6.0.
- A composite build with the Kotlin Gradle plugin 2.7.0 for the compiler plugin and 2.4.0 for the companion libraries
  supports Kotlin 2.4.0 to 2.8.0.

## Settings plugin

Apply the settings plugin in the settings script of each build:

```kotlin title="settings.gradle.kts"
plugins {
    kotlin("compiler.plugin.devkit") version "{{devkitVersion}}"
}
```

The settings plugin adds the Kotlin `bootstrap`, `dev`, and `intellij-dependencies` repositories to
`dependencyResolutionManagement`. Don't add them yourself.

The `pluginDevKit { }` block of the settings script has two functions for a composite build:

- `includeBuildWithChecks(path)`: includes the build at `path`. The `check` task of the root project then also runs
  the `check` task of the included build.
- `createVersionCatalogFrom(pathToRoot)`: makes the `libs` version catalog from `gradle/libs.versions.toml` of the
  build at `pathToRoot`. Use it in an included build to share the catalog of the main build.

```kotlin title="settings.gradle.kts"
pluginDevKit {
    includeBuildWithChecks("gradle-plugin")
    includeBuildWithChecks("plugin-annotations")
}
```
