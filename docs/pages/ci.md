# CI

## One job

```bash
./gradlew check
```

`check` runs the compiler plugin tests and the functional tests of every registered version. It also runs `check` in
each build that the settings script includes with
[`includeBuildWithChecks`](build-structure.md#settings-plugin).

## One job for each version

Each job registers one version with the `testAgainst` property:

```yaml title=".github/workflows/test.yml"
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        kotlin: [ 2.2.20, 2.3.0, 2.3.20 ]
    steps:
      # checkout, JDK, and Gradle setup
      - run: >
          ./gradlew check
          -Pkotlin.compiler.plugin.devkit.testAgainst=${{ matrix.kotlin }}
          -Pkotlin.compiler.plugin.devkit.classifiedDumpVersions=2.2.20,2.3.0,2.3.20
```

Rules:

- Register the versions only with properties. The versions of the `pluginDevKit { }` block add to the versions of the
  properties, so every job runs them too.
- Set `classifiedDumpVersions` to the full list of versions. Without it, each job reads the
  [dump files](testing.md#dump-files-of-different-versions) of the highest version.
- If the build applies the compiler plugin to its own companion libraries, each job must also register the Kotlin
  version of that build. Without that version or a lower one, the compiler of the libraries can't load the plugin.

## A job for new compiler builds

A scheduled job finds a breaking change in the compiler before a release has it:

```yaml title=".github/workflows/latest-compiler.yml"
on:
  schedule:
    - cron: '0 4 * * *'

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      # checkout, JDK, and Gradle setup
      - run: ./gradlew check -Pkotlin.compiler.plugin.devkit.useLatestDev=true
```

`useLatestBootstrap` does the same for the `bootstrap` repository.
