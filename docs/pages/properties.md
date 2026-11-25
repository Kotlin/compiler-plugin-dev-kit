# Properties reference

## Gradle properties

Each property has the prefix `kotlin.compiler.plugin.devkit.`. Set it in `gradle.properties` or with `-P`.

| Property                                        | Value                                              | DSL                         |
|-------------------------------------------------|----------------------------------------------------|-----------------------------|
| `testAgainst`                                   | A list of versions.                                | `testAgainst(...)`          |
| `minCliVersion`, `maxCliVersion`                | A Kotlin version.                                  | `cliVersions(...)`          |
| `includeBetaAndRc`                              | `none`, `latest` (default), or `all`.              | `cliVersions(...)`          |
| `minIdeaVersion`                                | An IDE build number, for example `252`.            | `ideaVersions(...)`         |
| `maxIdeaVersion`                                | A major IDE build, for example `263`.              | `ideaVersions(...)`         |
| `includeIdeaRc`, `includeIdeaEap`               | `true` or `false` (default).                       | `ideaVersions(...)`         |
| `useLatestDev`, `useLatestBootstrap`            | `true` or `false` (default).                       | `useLatestDev()`, `useLatestBootstrap()` |
| `defaultTestVersion`                            | A version.                                         | `defaultTestVersion(...)`   |
| `classifiedDumpVersions`                        | A list of versions. The default is every registered version. | `classifiedDumpVersions` |
| `pluginPackage`                                 | A package. The default is the `group` of the project. | `pluginPackage`          |
| `companionLibraries`                            | A list of project paths, artifact names, or coordinates. | `companionLibrary(...)` |
| `compilerPlugin`                                | A project path, an artifact name, or coordinates.  | `compilerPlugin(...)`       |
| `gradle.test.kmpTarget`                         | `jvm` (default), `js`, `wasmJs`, `native_host`, or `all`. | None.                |

A list separates its entries with commas or whitespace.

Pages: [Supported versions](versions.md#register-versions),
[dump files](testing.md#dump-files-of-different-versions), [plugin ID](compiler-plugin.md#plugin-id),
[companion libraries](compiler-plugin.md#companion-libraries), [Gradle support plugin](gradle-plugin.md).

## `pluginDevKit { }` in a compiler plugin or a compiler library

| Property                                   | Default                           | Sets                                                    |
|--------------------------------------------|-----------------------------------|---------------------------------------------------------|
| `componentRegistrar`                       | None. A compiler plugin needs it. | The [entry point](compiler-plugin.md#entry-points) for extensions. |
| `commandLineProcessor`                     | None. A compiler plugin needs it. | The [entry point](compiler-plugin.md#entry-points) for options. |
| `versionHierarchy { }`                     | No groups of your own.            | The [version groups](multiversioning/0-multiversioning.md#group-the-versions). |
| `preferredVersionForSharedSourceSetsInIde` | The highest version.              | The [version in the IDE](multiversioning/0-multiversioning.md#versions-in-the-ide). |
| `generateTestsEnabled`                     | `true` for a compiler plugin, `false` for a compiler library. | Test generation.            |
| `generateTestsClass`                       | `<pluginPackage>.GenerateTestsKt` | The [test generator](testing.md#test-generator).        |
| `generatedTestsDir`                        | `build/test-gen`                  | The directory of the generated tests.                   |
| `testDataLibraries { }`                    | No libraries.                     | The [libraries in test data](testing.md#libraries-in-test-data). |
| `ideaSinceBuild`, `ideaUntilBuild`         | The bounds of `ideaVersions`.     | The [declared range of IDE builds](ide.md#supported-ide-builds). |
| `addRuntimeDependency`                     | `true`                            | Adds the DevKit runtime, test, and test generator libraries. |

## `pluginDevKit { }` in a Gradle support plugin

| Property                   | Default      | Sets                                                                       |
|----------------------------|--------------|----------------------------------------------------------------------------|
| `functionalTestProperties` | No lines.    | Lines of the `gradle.properties` of every [functional test](gradle-plugin.md#functional-tests) build. |
| `addRuntimeDependency`     | `true`       | Adds the DevKit `gradle-plugin-runtime` library.                           |

## `pluginDevKit { }` in both

| Property                          | Default                                                               | Sets                                                |
|-----------------------------------|-----------------------------------------------------------------------|-----------------------------------------------------|
| `companionLibrariesKotlinVersion` | The highest compiler version in the klibs of the companion libraries. | The version below which the DevKit skips [JS tests](testing.md#skipped-js-tests) and [multiplatform functional tests](gradle-plugin.md#multiplatform-tests). |

## `pluginDevKit { }` in a settings script

| Function                          | Does                                                                           |
|-----------------------------------|--------------------------------------------------------------------------------|
| `includeBuildWithChecks(path)`    | Includes a build. The `check` of the root project also runs its `check`.       |
| `createVersionCatalogFrom(path)`  | Makes the `libs` version catalog from the catalog file of the build at `path`. |
