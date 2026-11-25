# Multiversioning guide

The DevKit makes one Kotlin Multiplatform target for each [registered version](../versions.md). The sources in
`commonMain` compile against the compiler of every version. A breaking change in a compiler version is then a compile
error in the target of that version. The target also reports the deprecations and the opt-in requirements of that
version.

Most code needs no change for a new compiler version. When a compiler API differs between versions, move the code
that uses the API behind an `expect` declaration. Then write one `actual` for each group of versions.

## Group the versions

The `versionHierarchy { }` block makes groups of versions. Each group gets a source set that only the versions of the
group compile.

```kotlin title="build.gradle.kts"
pluginDevKit {
    versionHierarchy {
        splitDev(2, 3, 20)
    }
}
```

This block makes the groups `pre2320Dev` and `post2320Dev`, with the source sets `pre2320DevMain` and
`post2320DevMain`.

| Function                            | Group                                                    | Default name                |
|-------------------------------------|----------------------------------------------------------|-----------------------------|
| `pre("2.5.0-dev-5651")`             | The versions before the version.                         | `pre250Dev5651`             |
| `post("2.5.0-dev-5651")`            | The version and the versions after it.                   | `post250Dev5651`            |
| `split("2.5.0-dev-5651")`           | Both groups of `pre` and `post`.                         | The two names above.        |
| `preDev(2, 3, 20)`                  | The versions with a base version before `2.3.20`.        | `pre2320Dev`                |
| `postDev(2, 3, 20)`                 | The versions with the base version `2.3.20` or higher.   | `post2320Dev`               |
| `splitDev(2, 3, 20)`                | Both groups of `preDev` and `postDev`.                   | The two names above.        |
| `groupVersions("name") { version -> condition }` | The versions that match the condition.      | `name`                      |

`pre`, `post`, and `split` compare full versions. `preDev`, `postDev`, and `splitDev` compare base versions, so the
split is at the first `dev` build of the base version. For example, with `2.5.0`, `2.5.20-dev-1234`, `2.5.20-Beta1`,
and `2.5.20` registered:

- `pre("2.5.20")` has `2.5.0`, `2.5.20-dev-1234`, and `2.5.20-Beta1`.
- `preDev(2, 5, 20)` has only `2.5.0`.

The DevKit also makes these groups:

- `common`: every version.
- `cli`: the CLI versions.
- `ide`: the IDE versions.
- `js`: the CLI versions from `2.3.0` on. Only test fixtures and tests have this group.

For an `-ijXYZ-` IDE version, a condition gets the `dev` version that the IDE version branched from, if the DevKit
has that version.

## Write the `expect` and the `actual`

Declare the `expect` in `commonMain`:

```kotlin title="src/commonMain/kotlin/CompatContext.kt"
expect val isTopLevelGenerationSupportedInKlib: Boolean
```

Write an `actual` in the source set of each group:

```kotlin title="src/pre2320DevMain/kotlin/CompatContext.pre2320Dev.kt"
actual val isTopLevelGenerationSupportedInKlib: Boolean
    get() = false
```

```kotlin title="src/post2320DevMain/kotlin/CompatContext.post2320Dev.kt"
actual val isTopLevelGenerationSupportedInKlib: Boolean
    get() = true
```

The groups also apply to test fixtures and tests. The source sets are `<group>Main`, `<group>TestFixtures`, and
`<group>Test`.

## Compiler APIs that the DevKit already covers

The DevKit has compatibility declarations for some compiler APIs that differ between versions. Check
[`DevKitCompat.kt`](https://github.com/Kotlin/compiler-plugin-dev-kit/blob/main/compiler-plugin-runtime/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompat.kt)
before you write an `expect`.

The DevKit also declares some renamed compiler declarations under their other name, in the package of the compiler.
Code that uses such a declaration compiles for every version with no change.

## Versions in the IDE

The IDE shows each shared source set with the compiler of the highest version that the source set supports. To use
another version, set `preferredVersionForSharedSourceSetsInIde` in `pluginDevKit { }`. This property doesn't change
the compilation.

## Version selection at run time

The published jar of the plugin has the classes of every version. When the compiler or the IDE loads the plugin, the
DevKit loads the classes of the current Kotlin version.

If the jar doesn't have the current version, the DevKit selects the closest compatible version. Usually that is the
nearest lower version.
