# Multiversioning

The DevKit models every supported Kotlin version as one KMP target. Your code in `commonMain` compiles against every
registered version, and each version runs the tests with its own compiler. Where the compiler API differs between
versions, you write one `expect` declaration in `commonMain` and one `actual` declaration on each side of the
difference.

## Declare the supported versions

`cliVersions` registers the CLI versions, `ideaVersions` registers the IDE versions, and `testAgainst` registers one
exact version:

```kotlin
pluginDevKit {
    cliVersions(min = "2.3.0")
    ideaVersions(min = "243")
    testAgainst("2.5.20-dev-3434")
}
```

- `cliVersions` compares base versions, so a bound of `2.4.20` also includes `2.4.20-Beta1`. An unset `max` gives an
  open-ended range. `betaAndRc` picks the prereleases to add to the stable versions: `NONE`, `LATEST` (the default,
  prereleases of the newest base version only), or `ALL`.
- `ideaVersions` takes platform build numbers, spelled only as precisely as needed: `253` is the whole 2025.3 series,
  and `253.29346.138` is one build of it. RC and EAP builds come only with `includeRc` or `includeEap`. The bounds also
  become the `since`/`until` build in the plugin manifest, which determines whether the IDE loads the plugin or not.
- `testAgainst` treats a `-dev-` version as an IDE version unless you pass `forIde = false`.
- `useLatestBootstrap()` adds the newest `dev` bootstrap build, so CI finds a breaking change before the next release.
- The known Kotlin and IDE versions are part of the DevKit. A newly released version appears in your range after you
  update the DevKit.

Every version becomes a Gradle target. Its name is `k` plus the version, lowercased, with the dots removed and `-`
replaced by `_`: `2.4.20-dev-7885` becomes `k2420_dev_7885`.

## Compile against a new version

Each version has a test task, `<targetName>Test`, that compiles the version and runs its tests:

| Command                                | What it does                               |
|----------------------------------------|--------------------------------------------|
| `./gradlew defaultTest`                | runs the tests of the `defaultTestVersion` |
| `./gradlew defaultTest --tests 'Foo*'` | runs the same tests, filtered by name      |
| `./gradlew k2420_dev_7885Test`         | runs the tests of one version              |
| `./gradlew allTests`                   | runs the tests of every registered version |

`check` also runs `allTests`. When no `defaultTestVersion` is set, `defaultTest` falls back to the build's own Kotlin
version. Use `defaultTest` while you develop, and run `allTests` before you push.

To check a new version:

1. Widen the range by one version.
2. Run the test task of the new version.
3. If the version compiles and its tests pass, it doesn't need anything else. Move on to the next version.

When the version doesn't compile, continue with [Write version-specific code](#write-version-specific-code). When the
tests fail because of a dump file, continue with [Versioned dump files](#versioned-dump-files).

## Write version-specific code

When a version fails to compile, put the changed API behind an `expect`/`actual` pair, split at the version that
introduced the change.

1. Find the version that introduced the change. When your code uses a new API, that is the lowest version that compiles.
   When your code uses an old API, that is the lowest version that fails to compile.

2. Register the split in the `versionHierarchy` block:

    ```kotlin
    pluginDevKit {
        versionHierarchy {
            split("2.4.0")
        }
    }
    ```

   `split("2.4.0")` registers the version groups `pre240` and `post240`, named after the version with the dots removed.
   Each group becomes a source set in every source set tree: `pre240Main` and `post240Main`, `pre240TestFixtures` and
   `post240TestFixtures`, `pre240Test` and `post240Test`. Each source set reads its sources from
   `src/<sourceSetName>/kotlin`.

3. Write the `expect` in `commonMain`, and one `actual` on each side. This example renames an API at `2.4.0`:

    ```kotlin
    // src/commonMain/kotlin/render/DeclarationRender.kt
    public expect fun renderDeclaration(declaration: FirDeclaration): String

    // src/pre240Main/kotlin/render/DeclarationRender.kt
    // before 2.4.0
    public actual fun renderDeclaration(declaration: FirDeclaration): String = declaration.printText()

    // src/post240Main/kotlin/render/DeclarationRender.kt
    // from 2.4.0
    public actual fun renderDeclaration(declaration: FirDeclaration): String = declaration.render()
    ```

4. Put only the declarations that touch the changed API in the versioned source sets. Code that every version compiles
   stays in `commonMain`.

Each side compiles against the compiler of its versions, so the old API resolves in `pre240Main` and the new one in
`post240Main`. The IDE gives each source set the highest version it supports. In `commonMain`, that is the highest
registered version.

`split` compares full versions: `2.4.0-dev-599` and `2.4.0-Beta1` sort below `2.4.0`, so `split("2.4.0")` puts them in
`pre240`. `splitDev` compares base versions, so `splitDev(2, 4, 0)` puts them on the `post` side. `splitDev(2, 4, 0)`
creates `pre24DevMain` and `post24DevMain`, and `splitDev(2, 3, 20)` creates `pre2320DevMain` and `post2320DevMain`.
When the change happened in a dev iteration, split at that exact dev version: `split("2.4.0-dev-599")`.

`groupVersions("name") { condition }` groups only the versions that match. `groupVersionsCli` does the same for CLI
targets only. Both serve as useful primitives if `split` isn't granular enough

Name each version group with the API that moved:

```kotlin
split("2.2.20-dev-1475", "SpecHelpersSourceFilesProvider")
```

## Find the change behind a compile error

When the API you use doesn't exist in an old version:

1. In the IDE, go to the definition of the API and note the file that declares it.
2. Open that file in the [JetBrains/kotlin repository](https://github.com/JetBrains/kotlin) and read its history. The
   commit that made the change usually states the reason, and its diff shows how the other call sites adapted.

## Version-specific test data

Test data that an old compiler can't run, for example a test of a new language feature, can move to the versioned test
source set. The resources of a test source set come from `src/<sourceSetName>/data`. Only the versions of the `post240`
group run the tests from `src/post240Test/data`.

Hand-written tests and fixtures work the same way: version-specific test code goes in `src/post240Test/kotlin`, and
fixtures in `src/post240TestFixtures/kotlin`.

Move a test into a versioned directory only when the older compilers can't run it.

## Versioned dump files

Each version runs the tests with its own compiler, so one test can give different output for each version. The DevKit
names each dump file after the version that owns it:

- The highest registered version owns the plain dump file: `foo.fir.txt`.
- Every other version reads the file of the nearest higher version that owns one. When its own output differs from that
  file, the version owns a file of its own: `foo.<classifier>.fir.txt`. The classifier is the target name without the
  `k` prefix, so `2.4.20-dev-7885` owns `foo.2420_dev_7885.fir.txt`.

The [classified dumps section](../spec.md#classified-dumps) of the spec records the full model.

In a build that registers `2.3.0` through `2.5.0`, a change around `2.4.0` gives the test exactly two files:

- `foo.fir.txt` holds the output of every version from `2.4.0` up. `2.4.20` reads this file.
- `foo.2321.fir.txt` holds the output of every version below `2.4.0`. `2.3.21` owns this file, and `2.3.20` reads it.

Run the tests until the dump files stop changing. A run writes missing dump files and deletes duplicate ones.

When the actual dump differs from an existing dump file, the test fails and reports a diff. Review the diff, then update
the file by hand. `-PupdateTestData` can be used to update many files at once.

When you raise the maximum supported version, expect new versioned files. The plain dump now belongs to the new highest
version, so the old highest version can need files of its own.
