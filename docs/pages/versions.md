# Supported versions

The DevKit builds and tests your plugin for each Kotlin version that you register. A version is one of two kinds:

- **CLI version**: a version from Maven Central or from the Kotlin `dev` or `bootstrap` repository. Command-line
  builds compile with it.
- **IDE version**: the version of the compiler that an IDE build bundles. It is a `-dev-` or an `-ijXYZ-` version,
  for example `2.4.20-ij262-52`.

## Register versions

Register versions in the `pluginDevKit { }` block or with Gradle properties. The DevKit adds the versions of both
forms together. Each property has the prefix `kotlin.compiler.plugin.devkit.`.

| DSL                                             | Gradle properties                                                     | Adds                                             |
|-------------------------------------------------|-----------------------------------------------------------------------|--------------------------------------------------|
| `cliVersions(min, max, betaAndRc)`              | `minCliVersion`, `maxCliVersion`, `includeBetaAndRc`                  | The CLI versions of a range of Kotlin releases.  |
| `ideaVersions(min, max, includeRc, includeEap)` | `minIdeaVersion`, `maxIdeaVersion`, `includeIdeaRc`, `includeIdeaEap` | The IDE versions of a range of IDE builds.       |
| `testAgainst("2.x.y")`                          | `testAgainst`                                                         | One version.                                     |
| `useLatestDev()`                                | `useLatestDev`                                                        | The latest version of the `dev` repository.      |
| `useLatestBootstrap()`                          | `useLatestBootstrap`                                                  | The latest version of the `bootstrap` repository.|

A list property separates its entries with commas or whitespace. A boolean property is true only when its value is
exactly `true`.

```kotlin title="build.gradle.kts"
pluginDevKit {
    cliVersions("2.2.20", betaAndRc = BetaAndRc.ALL)
    ideaVersions("252", includeRc = true, includeEap = true)
    useLatestDev()
}
```

The same versions as Gradle properties:

```properties title="gradle.properties"
kotlin.compiler.plugin.devkit.minCliVersion=2.2.20
kotlin.compiler.plugin.devkit.includeBetaAndRc=all
kotlin.compiler.plugin.devkit.minIdeaVersion=252
kotlin.compiler.plugin.devkit.includeIdeaRc=true
kotlin.compiler.plugin.devkit.includeIdeaEap=true
kotlin.compiler.plugin.devkit.useLatestDev=true
```

If you register no version, the DevKit registers the Kotlin version of the build as a CLI version.

## CLI versions

`cliVersions` adds every Release version from `min` to `max`. Without `max`, the range has no upper bound.

The bounds compare by base version, so a bound of `2.4.20` also takes in `2.4.20-Beta1`.

`betaAndRc` selects the Beta and RC versions:

| Value              | Adds                                                              |
|--------------------|-------------------------------------------------------------------|
| `NONE`             | No Beta or RC version.                                            |
| `LATEST` (default) | The Beta and RC versions of the latest base version in the range. |
| `ALL`              | Every Beta and RC version in the range.                           |

## IDE versions

`ideaVersions` adds the IDE versions of the IntelliJ IDEA and Android Studio builds in the range:

- `min` is an IDE build number with as many parts as you need. `253` is the whole 2025.3 series, and `253.29346.138`
  is one build of it.
- `max` is the highest major build, for example `263`. Without `max`, the range has no upper bound.
- `includeRc` and `includeEap` add the RC and EAP builds. Both default to `false`.

The DevKit registers only the newest known build of each release line.

The bounds also set the range of IDE builds that the published plugin declares as supported. To set that range
without a change to the registered versions, set `ideaSinceBuild` and `ideaUntilBuild`.

## Dev and bootstrap versions

`useLatestDev()` and `useLatestBootstrap()` add the latest compiler build of each repository. Use them
[on CI](ci.md#a-job-for-new-compiler-builds) to find a breaking change in the compiler early.

`testAgainst` registers a `-dev-` version as an IDE version. An IDE version gets no JS tests and no functional tests.
To register a `-dev-` version as a CLI version, set `forIde`:

```kotlin title="build.gradle.kts"
pluginDevKit {
    testAgainst("2.5.20-dev-5899", forIde = false)
}
```

The `testAgainst` property has no such option.

## Known versions

The ranges use a list of Kotlin releases and IDE builds that is part of the DevKit. A newer DevKit has a newer list.
To add a version that the list doesn't have, use `testAgainst`.

## Target names

Each version is a Kotlin Multiplatform target. The target name is `k` and the lowercase version, with each `.`
removed and each `-` replaced by `_`. For example, the target of `2.4.20-dev-7885` is `k2420_dev_7885`.

The names of the tasks and the source sets of a version start with its target name, for example
`k2420_dev_7885Test` and `k2420_dev_7885Main`.

## Default test version

The `defaultTest` and `defaultFunctionalTest` tasks run the tests of one version. `defaultTestVersion("2.x.y")` or
the `defaultTestVersion` property sets that version, and registers it.

Without this setting, the tasks use the Kotlin version of the build if it is registered. Otherwise they use an
arbitrary registered version.
