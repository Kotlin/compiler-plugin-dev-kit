# Spec

Use the DevKit to build Kotlin compiler plugins that support many Kotlin compiler versions from one source tree. It
publishes Gradle plugins and runtime/test libraries.

Compiler internals are unstable across versions, so the DevKit models a supported Kotlin version as a KMP target. The
same `commonMain` sources compile against every version's compiler, with `expect`/`actual` only where declarations
genuinely differ. A breaking change appears as a compile error, together with that version's deprecations and opt-ins.
One multi-release jar then holds every version, and the DevKit picks the matching one at Gradle build time and at
compiler and IDE runtime.

## Definitions

<dl>
    <dt id="functional-test">Functional test</dt>
    <dd>A Gradle integration test that uses <a href="https://docs.gradle.org/current/userguide/test_kit.html">Gradle TestKit</a>.</dd>
    <dt id="gradle-support-plugin">Gradle support plugin</dt>
    <dd>A <code>KotlinCompilerPluginSupportPlugin</code>.</dd>
    <dt id="companion-library">Companion library</dt>
    <dd>A library that the compiler plugin needs on the classpath of the user's code. The Gradle support plugin adds it. Such a library usually has annotations, and can also have types and utilities.</dd>
    <dt id="version">Version</dt>
    <dd>A Kotlin version (<code>KotlinToolingVersion</code>), including <code>-dev-</code> and <code>-ijXYZ-</code> versions.</dd>
    <dt id="cli-version">CLI version</dt>
    <dd>A <a href="#version">version</a> that is available on Maven Central or in the <code>dev</code> or <code>bootstrap</code> repository. Command-line builds use it to compile code.</dd>
    <dt id="ide-version">IDE version</dt>
    <dd>A <a href="#version">version</a> that is available in the <code>intellij-dependencies</code> repository. IDEs use it for analysis, and sometimes to compile JVM code for <code>Evaluate Expression</code> and <code>Show Kotlin Bytecode</code>. An IDE version is always a <code>-dev-</code> or an <code>-ijXYZ-</code> version.</dd>
    <dd>By default, every <code>-dev-</code> version is considered an IDE version by `testAgainst`. A build can override this by setting `forIde = false`.</dd>
    <dd>All <code>-ijXYZ-</code> versions with the same <code>XYZ</code> come from the same major IDE release. For example, <code>263</code> is 2026.3.</dd>
    <dt id="branch-point">Branch point</dt>
    <dd>The <code>-dev-</code> <a href="#ide-version">version</a> that an <code>-ijXYZ-</code> <a href="#ide-version">version</a> branched from. Git ancestry gives the exact branch point. In practice, the newest recorded <code>XYZ</code> build that maps to a plain <code>dev</code> version gives the branch point. <a href="../version-resolution/src/main/resources/META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/ide-mappings.txt"><code>ide-mappings.txt</code></a> records it. A release line whose recorded builds all map to <code>-ij</code> versions has no branch point.</dd>
    <dt id="base-version">Base version</dt>
    <dd>A Kotlin version (<code>KotlinVersion</code>) without a classifier, for example <code>1.9.0</code> or <code>2.4.21</code>. Each Kotlin <a href="#version">version</a> has a base version. To compare a <a href="#version">version</a> with a base version, convert the version to its base version.</dd>
    <dt id="target-name">Target name</dt>
    <dd>The Gradle target name of a <a href="#version">version</a>: <code>k</code> + the version, lowercased, with <code>.</code> removed and <code>-</code> replaced by <code>_</code>. For example, <code>2.4.20-dev-7885</code> → <code>k2420_dev_7885</code>.</dd>
    <dt id="ide-stability">IDE stability</dt>
    <dd>Release, RC, or EAP, in IntelliJ terms. For Android Studio, Beta counts as RC, and Canary counts as EAP.</dd>
    <dt id="ide-channel">IDE channel</dt>
    <dd>An <a href="#ide-stability">IDE stability</a> and a product (IntelliJ IDEA or Android Studio).</dd>
    <dt id="sibling-dependency">Sibling dependency</dt>
    <dd>A dependency on a project with the same <code>group</code> and the same <code>version</code>. Composite builds use it.</dd>
    <dt id="entry-point">Entry point</dt>
    <dd>A class of a plugin that the Kotlin compiler instantiates: <code>CommandLineProcessor</code> or <code>CompilerPluginRegistrar</code>.</dd>
    <dt id="classified-dump">Classified dump</dt>
    <dd>A dump (golden data) file with classifiers that tell it apart from similar dumps. For example, a <a href="#version">version</a> classifier gives one dump for each compiler version.</dd>
</dl>

## DevKit Structure

- [`version-resolution`](../version-resolution/): picks a compatible Kotlin [version](#version), maps IDE build numbers
  to
  [IDE versions](#ide-version), and parses the DevKit jar format.
- Gradle plugins:
    - [Settings plugin](#settings-plugin)
    - [`pluginDevKit("compiler-plugin")`](#plugindevkitcompiler-pluginplugindevkitcompiler-library): for the compiler
      plugin. Uses [`version-resolution`](#version-resolution).
    - [`pluginDevKit("gradle-plugin")`](#plugindevkitgradle-plugin): for
      the [Gradle support plugin](#gradle-support-plugin).
    - [
      `pluginDevKit("functional-test-publishing")`](../plugins/settings/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitFunctionalTestPublishingPlugin.kt):
      for all projects.
- [Runtime libraries](#runtime-libraries):
    - [`compiler-plugin-runtime`](#compiler-plugin-runtime): compiler plugin utilities and [entry point](#entry-point)
      classes. Uses `version-resolution`.
    - [`compiler-plugin-test`](#compiler-plugin-test): compiler plugin test utilities, including
      [classified dumps](#classified-dump).
    - [`gradle-plugin-runtime`](#gradle-plugin-runtime): [Gradle support plugin](#gradle-support-plugin) utilities. Its
      test fixtures help with [functional tests](#functional-test).

All DevKit artifacts share the group `org.jetbrains.kotlin.compiler.plugin.devkit`. The id of a Gradle plugin is
`<group>.<variant>`, for example `org.jetbrains.kotlin.compiler.plugin.devkit.compiler-plugin`. The settings plugin's id
is the group itself, so a settings script applies it as `kotlin("compiler.plugin.devkit")`. The settings plugin project
also has the `pluginDevKit(<variant>)` accessors, in the `org.gradle.kotlin.dsl` package. So build scripts can write
`pluginDevKit("compiler-plugin")` inside `plugins { }` and `pluginDevKit("version-resolution")` as a dependency
notation.

## Expected build structure

A typical compiler plugin has these parts:

- One compiler plugin
- One [Gradle support plugin](#gradle-support-plugin)
- Support plugins for other build tools, for example Maven or Amper
- Zero or more [companion libraries](#companion-library)

The DevKit supports three build structures:

1. A single build. This is the simplest structure, but it supports the smallest range of
   [versions](#version).
2. A composite build. The [companion libraries](#companion-library) use an older KGP, and everything else uses a newer
   KGP. This is the recommended structure, and
   the [compiler-plugin-template](https://github.com/Kotlin/compiler-plugin-template/tree/devkit) uses it.
3. Three builds. Use this structure to apply the compiler plugin to its own
   [companion libraries](#companion-library). The Gradle support plugin gets a separate build, because Isolated Projects
   doesn't support cyclic included builds.

One build limits the range of [versions](#version) for these reasons:

- KLIBs aren't forward compatible. For example, a `2.3` consumer can't use a KMP library that the `2.4` compiler built.
- An older KGP doesn't help, because the compiler plugin must compile against newer compiler versions.
- So the maximum range is 3 major versions (+2), with the experimental BTA `kotlin.compilerVersion`. Other structures
  can support 5 major versions (-3 +1).
    - For example, using KGP `2.4.0`, one can set `kotlin.compilerVersion = "2.5.0"`, and thus support `2.4.0-2.6.0`.
      For the composite build structure, using KGP `2.7.0` for compiler plugin, and `2.4.0` for companion libraries, one
      can support `2.4.0-2.8.0`

## DevKit plugins

### Shared properties

Each setting is available both as a DSL function of `pluginDevKit { }` and as a Gradle property, in
[`Properties.kt`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/Properties.kt). Both forms add
their [versions](#version) together, and the DevKit registers no version twice. A list property separates entries with
commas or whitespace. A boolean property counts as true only when its value is exactly `true`.

| DSL                                             | Gradle property                                                       |
|-------------------------------------------------|-----------------------------------------------------------------------|
| `testAgainst("2.x.y")`                          | `testAgainst`                                                         |
| `cliVersions(min, max, betaAndRc)`              | `minCliVersion`, `maxCliVersion`, `includeBetaAndRc`                  |
| `ideaVersions(min, max, includeRc, includeEap)` | `minIdeaVersion`, `maxIdeaVersion`, `includeIdeaRc`, `includeIdeaEap` |
| `useLatestDev()`, `useLatestBootstrap()`        | `useLatestDev`, `useLatestBootstrap`                                  |
| `defaultTestVersion("2.x.y")`                   | `defaultTestVersion`                                                  |
| `pluginPackage = "foo.bar"`                     | `pluginPackage`                                                       |
| `companionLibrary(...)`                         | `companionLibraries`                                                  |

- [
  `testAgainst("2.x.y")`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitPropertiesDsl.kt)
  adds a [version](#version) to compile for and test against.
- [
  `cliVersions(min, max, betaAndRc)`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitPropertiesDsl.kt)
  adds Release, Beta, and RC [CLI versions](#cli-version) from `min` to `max`.
    - `min` and `max` are [base versions](#base-version). The range compares by base version, so a bound of `2.4.20`
      also takes in `2.4.20-Beta1`.
    - Open-ended ranges are supported.
    - The DevKit plugins have a built-in list of known non-dev [CLI versions](#cli-version) of [
      `KnownVersions`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/KnownVersions.kt)
      ([#15](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/15)
      tracks dynamic version resolution).
    - The default for `betaAndRc` is `BetaAndRc.LATEST`
    - [`betaAndRc`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitPropertiesDsl.kt) is
      a [`BetaAndRc`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/BetaAndRc.kt), an enum with
      3 values:
        - `NONE`: Release [versions](#cli-version) in the range.
        - `LATEST`: Release [versions](#cli-version) in the range, and the Beta and RC [versions](#cli-version) of the
          latest base version in the range. The latest base version is the next release, so it can have no Release
          version yet. For example, if `2.5.0-Beta1` exists, `LATEST` adds it, and no Beta or RC of an earlier base
          version.
        - `ALL`: Release, Beta, and RC [versions](#cli-version) in the range.
- [
  `ideaVersions(min, max, includeRc, includeEap)`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitPropertiesDsl.kt)
  adds Release, RC, and EAP [IDE versions](#ide-version).
    - `min` is an `IdeBuildNumber`, and `max` is an `Int`. This matches `sinceBuild` and `untilBuild`.
    - Open-ended ranges are supported
    - `includeRc` and `includeEap` default to `false`.
    - It uses the IDE build mapping of [Version resolution](#version-resolution)
      ([#15](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/15)). The DevKit skips a build that
      `ide-mappings.txt` doesn't map to a version.
    - The DevKit plugins have a built-in list of known IDE builds in
      [
      `ide-versions.txt`](../plugins/src/main/resources/META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/ide-versions.txt),
      grouped by product and [IDE channel](#ide-channel)
      ([#15](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/15)). The DevKit registers only the newest
      recorded build of each release line, so the number of targets stays reasonable.
    - For each IDE marketing version, for example `2026.3.3`, only the latest build of each [IDE channel](#ide-channel)
      is used. In practice, only the latest EAP of a [version](#ide-version) is used (known
      problem, [#70](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/70)).
    - The bounds also set the manifest attributes `Kotlin-Compiler-Plugin-Since-Build` and
      `Kotlin-Compiler-Plugin-Until-Build` of the
      [surrogate jar](#plugindevkitcompiler-pluginplugindevkitcompiler-library). `Until-Build` is `<max>.*`, so the jar
      supports every build of that series.
    - The function also adds Android Studio builds. Android Studio uses a stable IntelliJ IDEA release as its base.
- [`useLatestDev`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitPropertiesDsl.kt)/[
  `useLatestBootstrap`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitPropertiesDsl.kt)
  get the latest Kotlin [version](#cli-version) from the `dev`/`bootstrap` repository and add it as
  a [CLI version](#cli-version). They help automated CI checks find breaking changes.
- [
  `defaultTestVersion("2.x.y")`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitPropertiesDsl.kt)
  sets the [version](#version) that the `default*Test` tasks run against (possibly not
  useful, [#71](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/71)).
    - If the other properties don't add this [version](#version), `defaultTestVersion` adds it like `testAgainst` does.
    - If no default is set, the task falls back to the build's own Kotlin version if the build registers it. Otherwise
      it falls back to an arbitrary registered version.
- If none of the properties above add a [version](#version), the DevKit adds the `kotlinToolingVersion` of KGP as a
  [CLI version](#cli-version). So the build always has at least 1 [version](#version).
- [
  `pluginPackage = "foo.bar"`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitPropertiesDsl.kt)
  sets the plugin ID, the package for buildconfig generation, and the classloader isolation ID (needs a
  split, [#55](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/55)).
    - The default is the `group` of the project.
- [
  `companionLibrary(notation)`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitPropertiesDsl.kt)
  adds a [companion library](#companion-library).
    - Compiler plugin test data can use the library.
    - The [Gradle support plugin](#gradle-support-plugin) adds it as a dependency of its users.
    - The DevKit reads `notation` as the first format in this list that matches:
        1. A project dependency, if it starts with `:`.
        2. A normal dependency notation, if it has `:`.
        3. A [sibling dependency](#sibling-dependency).
- [
  `companionLibrariesKotlinVersion`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompilerPlugin.kt)
  skips KMP tests for incompatible [versions](#version). The reason is in
  [Expected build structure](#expected-build-structure).
    - The default is the highest `compiler_version` the libraries' klib manifests record, so it also covers
      external [companion libraries](#companion-library).
    - If no library has a klib, the default is the lowest registered [version](#version), so the DevKit skips no tests.

### Settings plugin

- Adds the `bootstrap`, `dev`, and `intellij-dependencies` repositories to `dependencyResolutionManagement`.
- Applies [
  `functional-test-publishing`](../plugins/settings/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitFunctionalTestPublishingPlugin.kt)
  to every project. That plugin applies `com.autonomousapps.testkit`, without the `testKitSupportFor*` publication.
- Has utilities for a composite build setup:
    - [
      `createVersionCatalogFrom(pathToRoot, pathToVersionCatalog, name)`](../plugins/settings/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitSettingsPlugin.kt)
      makes a `name` version catalog from the file at `pathToRoot/pathToVersionCatalog`.
    - [
      `includeBuildWithChecks`](../plugins/settings/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitSettingsPlugin.kt)
      calls `includeBuild`, and makes the `check` task of the root project depend on the `check` task of the included
      build.

### `pluginDevKit("gradle-plugin")`

- Applies `functional-test-publishing`, `buildconfig`, and `java-gradle-plugin`.
- Ignores all [IDE versions](#ide-version).
- Adds [`gradle-plugin-runtime`](#gradle-plugin-runtime) and its test fixtures.
- Registers a [functional test](#functional-test) task, `<versionName>FunctionalTest`, for each
  [CLI version](#cli-version) ([#54](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/54)):
    - The registration logic is a copy of the testkit logic.
    - The build compiles the testkit `functionalTest` source set a single time. Every task runs it against its own
      version.
    - All tasks use the `gradleUserHomeDir` of the build, so the tests use dependencies that are already in its cache.
      No test task makes a separate Gradle cache.
- Registers an `allVersionedFunctionalTests` task, like the `allTests` task of KGP. The `check` task depends on it.
- Registers a lifecycle `defaultFunctionalTest` task, which depends on the functional test task of the
  `defaultTestVersion`. The testkit's `functionalTest` task also becomes a lifecycle task, running
  `defaultFunctionalTest`.
- Publishes all project [companion libraries](#companion-library) for [functional tests](#functional-test), and adds
  them to the test runtime classpath, so reflective calls succeed.
    - If a [companion library](#companion-library) comes from a project in an included build, this plugin's
      `installForFunctionalTest` task depends on that project's `installForFunctionalTest` task.
    - The [functional test](#functional-test) gets the `functionalTestRepo` of that build through the
      `com.autonomousapps.plugin-under-test.repos-included` property.
      The [gradle plugin test runtime](#gradle-plugin-runtime) reads the property, and adds the repository to the test
      build.
- Generates a `PluginInfo` object with `buildconfig`. The object has the plugin ID, the plugin coordinates, and
  the [companion library](#companion-library) coordinates.
- Has these properties beyond the [shared properties](#shared-properties):
    - [
      `functionalTestProperties`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitGradlePlugin.kt):
      lines that [functional tests](#functional-test) add to `gradle.properties`.
    - [`kmpTarget`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitGradlePlugin.kt): the
      KMP target that multiplatform IC [functional tests](#functional-test) test (should just be an env
      var [#85](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/85)). The value is one of these:
        - A standard target name.
        - `native_host`: the native target that the current OS supports.
        - `all`.
    - [`compilerPlugin`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitGradlePlugin.kt):
      the compiler plugin that
      [
      `DevKitSupportPlugin`](../gradle-plugin-runtime/src/main/java/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitSupportPlugin.kt)
      applies. The Gradle property is `kotlin.compiler.plugin.devkit.compilerPlugin`. The value is one of these:
        - `LocalProject`: a project with the coordinates of a [sibling dependency](#sibling-dependency). It is published
          for [functional tests](#functional-test). A value that starts with `:` is a project path.
        - `External`: explicit coordinates. A value of the form `<group>:<artifact>:<version>` is `External`.
        - `Sibling`: a [sibling dependency](#sibling-dependency). Any other value is a `Sibling`.
        - Convenience functions set `LocalProject` with `compilerPlugin(project(...))`, and `Sibling` or `External` with
          `compilerPlugin("...")`

### `pluginDevKit("compiler-plugin")`/`pluginDevKit("compiler-library")`

- Applies the Kotlin `multiplatform` plugin, `buildconfig`, `node-gradle`, `idea`, `D8Plugin`, and
  `functional-test-publishing`.
- Adds the `ExperimentalCompilerApi` and `UnsafeDuringIrConstructionAPI` opt-ins to every compilation.
- Has two variants: `compiler-plugin` for a compiler plugin, and `compiler-library` for the compiler plugin runtime
  libraries.
- Features of `compiler-plugin` only:
    - Fails if `commandLineProcessor` or `componentRegistrar` isn't set.
    - Generates the [entry point](#entry-point) classes and the plugin ID with `buildconfig`. See
      [In the compiler: plugin loading](#in-the-compiler-plugin-loading).
    - Embeds the dependencies into `META-INF/kotlin/plugin/<plugin-id>/dependencies`. It unpacks each jar into its own
      subdirectory.
    - Embeds the [entry point](#entry-point) runtime and [version resolution](#version-resolution) on the normal
      classpath. Thus, these classes can clash with other plugins on the classpath:
        - The [entry point](#entry-point) classes don't clash, because their package comes from the plugin ID.
        - The [entry point](#entry-point) runtime classes can clash between plugins that use different DevKit versions.
        - To prevent this, the package acts as a version of the classes. After the DevKit is public, each change to
          `version-resolution` or to the entry point logic *must* change the package of the changed classes.
- Registers a [`DevKitTarget`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitTarget.kt)
  for each [version](#version):
    - The target name is the [target name](#target-name) of the [version](#version), for example `k2420_dev_7885`.
    - `DevKitTarget` uses the `ExternalKotlinTargetApi`.
    - Most of its configuration is a copy of the Kotlin JVM target and the Android target configuration.
    - A `DevKitTarget` has 3 default compilations: `main`, `testFixtures`, and `test`. `testFixtures` works like
      `java-test-fixtures`.
    - It has its own [
      `DevKitTestRun`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitTarget.kt). It is
      almost the same as `JvmTestRun`, but it sets `targetName` to the [version](#version) string.
    - The test run registers a `<targetName>Test` task, and passes it to KGP's `KotlinTestsRegistry`. So `allTests`
      aggregates the test tasks, and `check` runs them.
    - A lifecycle `defaultTest` task runs the test task of the `defaultTestVersion`.
    - With `compiler-plugin`, the published POM declares no dependencies. With `compiler-library`, the published POM
      declares the dependencies of the `main` compilation of the surrogate target.
    - The compilations have resources and Java support (TODO: Java support has little use here, and possibly the
      versions can't share Java sources).
    - For IDE import, each source set gets the highest version that it supports. The
      `preferredVersionForSharedSourceSetsInIde` property overrides this for the source sets that support that version.
      So different source sets see different versions of the compiler, the stdlib, and the test framework.
    - The DevKit forces the dependencies of each target to the matching Kotlin version. It forces only the
      `org.jetbrains.kotlin` modules that
      [
      `kotlin-modules.txt`](../plugins/src/main/resources/META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/kotlin-modules.txt)
      lists, the modules of the `dev` and `intellij-dependencies` repositories
        - A [CLI version](#cli-version) keeps the module names and forces their versions.
        - An [IDE version](#ide-version) also maps each module to the name that it goes by in `intellij-dependencies`.
        - The mapping `UNRELATED` records a module with a versioning scheme unrelated to Kotlin, so the DevKit doesn't
          force it. For example, the temporary Analysis API standalone artifacts
        - The mappings `UNSUPPORTED` and `DEAD` record that no reasonable IDE equivalent exists. For
          an [IDE version](#ide-version), the DevKit fails the build on them.
- Has a [
  `versionHierarchy`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompilerPlugin.kt)
  DSL:
    - The `common`, `ide`, and `cli` groups are registered automatically.
    - [
      `groupVersions("name") { version -> condition }`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitHierarchyDsl.kt)
      groups the [versions](#version) that match `condition`. If the
      [branch point](#branch-point) is known, `version` is the branch point.
    - [`pre`/`post`/
      `split`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitHierarchyDsl.kt) group the
      versions before, from, and around a [version](#version). [`preDev`/`postDev`/
      `splitDev`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitHierarchyDsl.kt) do the
      same for a [base version](#base-version).
        - If we have `2.5.20-Beta1`, `2.5.20-dev-1234`, `2.5.0`, and `2.5.20` registered
            - `pre(“2.5.20”)` matches `2.5.20-Beta1`, `2.5.20-dev-1234`, and `2.5.0` together
            - `preDev(2, 5, 20)` matches `2.5.0` alone
    - `versionHierarchy { }` applies the groups to the `main`, `testFixtures`, and `test` source set trees. So versions
      share test fixtures and tests exactly like main code.
- Publishes everything in one jar with a "surrogate target":
    - The surrogate target is the first registered target. Its jar and its publications get the classes of all versions
      in `META-INF/kotlin/plugin/<plugin-id>/versions/`.
    - The manifest has the `Kotlin-Compiler-Plugin-Multi-Release` attribute. For IDE support, it also has
      `Kotlin-Compiler-Plugin-Since-Build` and `Kotlin-Compiler-Plugin-Until-Build`.
    - The DevKit generates the [entry point classes](#entry-point) into a separate `entry-point` compilation of the
      surrogate target.
- Generates tests into [
  `generatedTestsDir`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompilerPlugin.kt),
  default `build/test-gen`:
    - The generator is the class that [
      `generateTestsClass`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompilerPlugin.kt)
      names, in the `generateTests` source set. The default is `<pluginPackage>.GenerateTestsKt`.
    - The [
      `generateTestsEnabled`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompilerPlugin.kt)
      property turns generation off. Default is true for `compiler-plugin`, and false for `compiler-library`.
    - Both generator compilations use the newest known Kotlin version, so the generator never compiles against
      `compiler-tests-for-ide`, which has no test generator.
    - `internal-test-framework` isn't published for [IDE versions](#ide-version). The DevKit maps it to
      `compiler-tests-for-ide`, which has no JS test infrastructure. So the generator has two compilations:
        - `generateTestsCli`: the DevKit adds it on the first CLI target, and associates it with the `testFixtures`
          compilation of that target. It sees the JS test infrastructure, and the classes for it in `cliTestFixtures`.
        - `generateTestsIde`: the DevKit adds it on the first IDE target, and associates it with the `testFixtures`
          compilation of that target. It doesn't see the JS test infrastructure.
    - The DevKit marks generated tests as generated sources for IDEA, so IDEA ignores them (workaround for
      [IDEA-339729](https://youtrack.jetbrains.com/issue/IDEA-339729)).
- The resources of every test source set come from `src/<sourceSetName>/data`.
- Supports [companion libraries](#companion-library), and "conditional" libraries through the [
  `testDataLibraries`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompilerPlugin.kt)
  DSL:
    - It has the `common`, `jvm`, and `js` `DependencyCollector`s.
    - It can register named library collections.
    - The DevKit passes all libraries to the compiler plugin test runtime. Tests get them through the generated
      buildconfig class `TestDataLibraries`.
- Passes the libraries that `internal-test-framework` needs as system properties. They always match the
  target [version](#version). For JS tests, it also passes the `d8` executable and a generated `repl.js`.
- Registers an [artifact transform](#in-gradle-artifact-transform), so each compilation sees a version-specific jar of
  the compiler plugin and of each `compiler-library`.

## Runtime libraries

### `compiler-plugin-runtime`

- Has the interfaces for compiler plugin entry points:
    - [
      `DevKitComponentRegistrar`](../compiler-plugin-runtime/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitComponentRegistrar.kt)
      (mimicking `CompilerPluginRegistrar`): has `registerExtensions` only.
    - [
      `DevKitCLP`](../compiler-plugin-runtime/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCLP.kt)
      (mimicking `CommandLineProcessor`): has `processOption` and `pluginOptions` only.
- Each implementation must have a public no-arg constructor, because the [entry point](#entry-point) instantiates it
  with reflection (add static check [#86](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/86)).
- The interfaces have no `pluginId` and no `supportsK2`. The entry point sets `pluginId` to the plugin ID, and
  `supportsK2` to `true`.
- [
  `DevKitCompat.kt`](../compiler-plugin-runtime/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompat.kt)
  has compatibility functions for compiler APIs that differ between [versions](#version), for example
  `KtFakeSourceElementKind.PluginGenerated`.
- The `entryPoint` compilation has [
  `DevKitCompilerPluginRegistrar`](../compiler-plugin-runtime/src/entryPoint/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompilerPluginRegistrar.kt), [
  `DevKitCommandLineProcessor`](../compiler-plugin-runtime/src/entryPoint/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCommandLineProcessor.kt),
  and
  [
  `VersionedPluginClassLoading`](../compiler-plugin-runtime/src/entryPoint/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/VersionedPluginClassLoading.kt).
  It isn't versioned, because it runs before the version is known.
  [In the compiler: plugin loading](#in-the-compiler-plugin-loading) describes these classes.

### `compiler-plugin-test`

The [`compiler-plugin`](#plugindevkitcompiler-pluginplugindevkitcompiler-library) and [
`compiler-library`](#plugindevkitcompiler-pluginplugindevkitcompiler-library) plugins add it as an `api` dependency of
`commonTestFixtures`. They also add `junit-bom` there: `kotlin-test-junit5` pins JUnit Jupiter 5.10.1, but the
`kotlin-compiler-internal-test-framework` of Kotlin `2.4.0` and later needs 5.12.

#### Test bases

The test bases are in the package
[
`org.jetbrains.kotlin.compiler.plugin.devkit.runners`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners):

- [
  `DevKitTest`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/DevKitTest.kt)
  is the base of every other DevKit test base:
    - Its constructors take configuration lambdas as a `vararg`, alongside other arguments like `targetBackend` or an
      `AbstractKotlinCompilerTest` to wrap
    - Every test gets the [version classifier](#classified-dumps), [
      `firIdenticalCompat()`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitTestCompat.kt),
      [
      `configureDefaultTestDataLibraries()`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/services/TestDataLibrariesProvider.kt),
      and [
      `DevKitTestConfigurator`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/services/DevKitTestConfigurator.kt).
    - It reads the standard library paths from the system properties that the `<target>Test` task sets.
    - `configure(builder)` is final. A subclass adds configuration through the constructor or the protected `configure`
      functions.
- The `Backported*` bases are copies of `internal-test-framework` bases, changed to compile against every
  [version](#version) that they support. They use the DevKit dump handlers.
  [The migration guide has a table](migration/4-compiler-plugin-tests.md#test-bases) mapping each
  `internal-test-framework` base to its copy.
- The `DevKit*Test` runners add default directives to a base. By default, they all use the LightTree parser.
    - These defaults match
      the [compiler-plugin-template](https://github.com/Kotlin/compiler-plugin-template/tree/devkit).

| Runner                                                                                                                                                    | Base                                                                                                                                                                                        | Default directives                                                    |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| [`DevKitJvmBoxTest`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/DevKitJvmBoxTest.kt)               | [`BackportedFirBlackBoxCodegenTestSpecBase`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/BackportedFirBlackBoxCodegenTestSpecBase.kt) | `DUMP_IR`, `FIR_DUMP`, `FULL_JDK`, `IGNORE_DEXING`                    |
| [`DevKitJvmDiagnosticTest`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/DevKitJvmDiagnosticTest.kt) | [`BackportedFirPhasedDiagnosticTest`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/BackportedFirPhasedDiagnosticTest.kt)               | `FIR_DUMP`, `DISABLE_GENERATED_FIR_TAGS`, `FULL_JDK`, `IGNORE_DEXING` |
| [`DevKitJsBoxTest`](../compiler-plugin-test/src/cliMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/DevKitJsBoxTest.kt)                    | [`MultiVersionJsTest`](../compiler-plugin-test/src/cliMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/DevKitJsBoxTest.kt)                                                   | `DUMP_IR`, `FIR_DUMP`                                                 |
| [`DevKitJsDiagnosticTest`](../compiler-plugin-test/src/cliMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/DevKitJsDiagnosticTest.kt)      | [`BackportedJsDiagnosticWithBackendTestBase`](../compiler-plugin-test/src/cliMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/BackportedJsDiagnosticTest.kt)                 | `FIR_DUMP`, `DISABLE_GENERATED_FIR_TAGS`                              |

#### Services

The services are in the package
[
`org.jetbrains.kotlin.compiler.plugin.devkit.services`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/services):

- [
  `configurePlugin(registrar)`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/services/ExtensionRegistrarConfigurator.kt)
  registers the extensions of a `DevKitComponentRegistrar`.
- [
  `configureTestDataLibrary(library) { module -> condition }`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/services/TestDataLibrariesProvider.kt)
  adds a `TestDataLibrary` to the compile and runtime classpaths of the modules that match.
- [
  `DevKitTestConfigurator`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/services/DevKitTestConfigurator.kt)
  skips JS tests if [
  `TEST_COMPILER_VERSION`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/services/DevKitTestConfigurator.kt)
  is lower than [
  `BUILD_COMPILER_VERSION`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/services/DevKitTestConfigurator.kt),
  because KLIBs aren't forward compatible. `TEST_COMPILER_VERSION` is the version of the test task.
  `BUILD_COMPILER_VERSION` is
  [
  `companionLibrariesKotlinVersion`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompilerPlugin.kt).

#### Version compatibility

- [
  `DevKitTestCompat.kt`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitTestCompat.kt)
  has compatibility declarations for test framework APIs that differ between [versions](#version).
- Typealiases in `org.jetbrains.kotlin.test.builders` give every version both
  [
  `NonGroupingPhaseTestConfigurationBuilder`](../compiler-plugin-test/src/pre24DevMain/kotlin/org/jetbrains/kotlin/test/builders/NonGroupingPhaseTestConfigurationBuilder.kt)
  and
  [
  `NonGroupingStageTestConfigurationBuilder`](../compiler-plugin-test/src/pre2420DevMain/kotlin/org/jetbrains/kotlin/test/builders/NonGroupingStageTestConfigurationBuilder.kt).
    - They exist for the IDE: `expect/actual` with `TestConfigurationBuilder` confuses the IDE, because the IDE
      immediately expands a `typealias` to a different class in each version. With these type aliases, the expanded type
      is then re-expanded into the proper `TestConfigurationBuilder` for this version. This doesn't affect CLI
      compilation.

#### CLI-only code

[`src/cliMain`](../compiler-plugin-test/src/cliMain) compiles only for [CLI versions](#cli-version). It has:

- The test generator API. [
  `generateDevKitTestsWithJUnit5 { }`](../compiler-plugin-test/src/cliMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitTestGenerator.kt)
  calls `testGroup` for each test data directory:
    - The `generateTests*` task gives the directories in the system property `kotlin.compiler.plugin.devkit.roots`. Its
      value is pairs of an output directory and a comma-separated list of data directories, all separated by `;`.
    - The generated suites need different FQNs, because otherwise the Kotlin Compiler DevKit IDE plugin can't tell them
      apart. Each suite is in the package `<package of the test base>.<target name>{_<index>}`. The `<target name>` is
      the [target name](#target-name) of the version that the suite tests. The generator adds `_<index>` for each data
      directory after the first.
- The JS runners: [
  `BackportedJsTest`](../compiler-plugin-test/src/cliMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/BackportedJsTest.kt),
  [
  `MultiVersionJsTest`](../compiler-plugin-test/src/cliMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/DevKitJsBoxTest.kt),
  [
  `BackportedJsDiagnosticWithBackendTestBase`](../compiler-plugin-test/src/cliMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/BackportedJsDiagnosticTest.kt),
  [
  `DevKitJsBoxTest`](../compiler-plugin-test/src/cliMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/DevKitJsBoxTest.kt),
  and [
  `DevKitJsDiagnosticTest`](../compiler-plugin-test/src/cliMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/DevKitJsDiagnosticTest.kt).

IDE versions get no JS tests. The IDE uses only the JVM backend (for `Evaluate Expression`/`Show Kotlin Bytecode`), and
Analysis API tests cover JS diagnostics better than compiler tests.

#### Classified dumps

A classified dump has classifiers in its file name, between the test name and the dump extension. For the test data file
`foo.kt` and the extension `.fir.txt`, `foo.fir.txt` is the unclassified dump, and `foo.2420_dev_7885.fir.txt` is the
dump for `2.4.20-dev-7885`. A [design discussion](https://chatgpt.com/share/e/6a6cafc0-1a78-832f-9156-7afe458021de)
justifies the ambiguity and fixed-classifier rules.

The model:

- [
  `TestDumpRoot`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/TestDumpDirectives.kt):
  one dimension of classifiers, for example `version`. `calculateClassifiers()` lists its classifiers.
- [
  `TestDumpClassifier`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/TestDumpDirectives.kt):
  one value of a root. Its `extension` is its part of the file name. Its `compatibleWith` is the classifier to fall back
  to, or `null` to fall back to no classifier of this root. The `compatibleWith` chain must have no cycles. So the
  classifiers of each root form a tree.
- [
  `SingleTestDumpClassifier`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/TestDumpDirectives.kt):
  a root with one classifier.
- [
  `TestDumpRoot.fixedClassifiers(directives)`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/TestDumpDirectives.kt):
  limits a root to some of its classifiers. A classifier collapses along its `compatibleWith` chain to the first fixed
  classifier, and the test always needs the file of that classifier. If the chain has no fixed classifier, the file name
  gets no classifier of this root.
    - Usually a directive lists the fixed classifiers.
- A file name has at most one classifier for each root, sorted by the `uniqueName` of the root. The sorting affects only
  the file name, not the fallback.

The directives of [
`TestDumpDirectives`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/TestDumpDirectives.kt):

- `DUMP_CLASSIFIER`: the classifiers of a test. Only the test configuration can set it, not a test data file. It must
  have the whole `compatibleWith` chain of each classifier.
    - Every classifier in the chain needs a test runner that writes its file directly. This includes the last classifier
      of the chain. Then no fallback file is out of date.
- `DUMP_AS_DIFF`: write each classified dump as a unified diff against the unclassified dump. The dump extension
  `.fir.txt` becomes `.fir.patch`.

The DevKit dump handlers, [
`BackportedFirDumpHandler`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/handlers/BackportedFirDumpHandler.kt)
and [
`BackportedIrTextDumpHandler`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/handlers/BackportedIrTextDumpHandler.kt),
check their dumps with
[
`assertEqualsToDump`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/TestDumpDirectives.kt).
`assertEqualsToDump` uses two files:

- The fully classified file has all classifiers of the test.
- The fallback file is the existing file with the most specific classifiers that the classifiers of the test fall back
  to. If no such file exists, the fallback file is the unclassified dump.
    - If the fallback is ambiguous, only the fully classified file counts. For example, a test with the classifiers `a`,
      `b`, and `c`, one for each root, and with both `foo.a.fir.txt` and `foo.b.c.fir.txt` present, uses neither. The
      most specific classifier combination is `a.b.c`, which matches no existing file.

The first rule that matches applies:

1. If the dump is empty, the fully classified file must not exist.
2. If the dump is the same as the fallback file, and no classifier is fixed, the fully classified file must not exist.
3. Otherwise, the dump must be the same as the fully classified file.

The [version classifier](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/VersionClassifier.kt):

- [
  `classifiedDumpVersions`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompilerPlugin.kt)
  sets the [versions](#version) that get a classifier. The Gradle property
  `kotlin.compiler.plugin.devkit.classifiedDumpVersions` sets it, and the default is every registered
  [version](#version).
    - Use it on CI, where each shard registers a single version, so no test runs twice. With this property, the version
      tree has a classifier for each version in the list, so each shard writes its own dump file.
- It has one classifier for each version of `classifiedDumpVersions` except the highest. The `<target>Test` task gives
  these versions in the system property `kotlin.compiler.plugin.devkit.versions`.
- The name of a classifier is its [target name](#target-name) without the `k` prefix.
- A classifier falls back to the classifier of the next higher version. The highest version writes directly to the
  unclassified dump.
- [
  `DevKitTest`](../compiler-plugin-test/src/commonMain/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/runners/DevKitTest.kt)
  sets `DUMP_CLASSIFIER` to the chain of the version of the current compiler. Every version runs its own tests, so every
  classifier in the chain has a test runner that writes its file directly.

So the highest listed version uses the unclassified dump. A lower version uses the dump of the nearest higher version
that has its own file. It needs its own file only if its dump is different. If a build lists a new highest version, the
previous highest version can need its own files.

The classifier chain covers only the versions of `classifiedDumpVersions`. So a dump file for a version that is no
longer listed doesn't fail the test ([#64](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/64)).

`-PupdateTestData` sets the system property `kotlin.test.update.test.data`, so the test framework writes each dump that
doesn't match.

### `gradle-plugin-runtime`

A JVM library for the [Gradle support plugin](#gradle-support-plugin). `pluginDevKit("gradle-plugin")` adds it and its
test fixtures.

[
`DevKitSupportPlugin(info)`](../gradle-plugin-runtime/src/main/java/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitSupportPlugin.kt)
is an abstract `KotlinCompilerPluginSupportPlugin`. A Gradle support plugin extends it, and gives it the generated
`PluginInfo.PLUGIN_INFO`, a `DevKitPluginInfo(id, artifact, companionLibraryCoordinates)`:

- `isApplicable` returns `true` for every compilation.
- `getCompilerPluginId` and `getPluginArtifact` return `info.id` and `info.artifact`.
- `applyToCompilation` is final. It adds each companion library as an `implementation` dependency of the default source
  set of the compilation, only if `Project.shouldAddRuntimeDependency()`.
    - The plugin options come from the overload `Project.applyToCompilation(kotlinCompilation)`, which defaults to an
      empty list

The test fixtures, in the package
[
`org.jetbrains.kotlin.compiler.plugin.devkit.test`](../gradle-plugin-runtime/src/testFixtures/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/test),
help with [functional tests](#functional-test). Each `<versionName>FunctionalTest` task gives them these system
properties:

| System property                                        | Value                             | Read by                                                                                                                                                           |
|--------------------------------------------------------|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `kotlin.compiler.plugin.devkit.test.kotlin-version`    | The version of the task           | [`TEST_COMPILER_VERSION`](../gradle-plugin-runtime/src/testFixtures/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/test/TestSupport.kt)                       |
| `kotlin.compiler.plugin.devkit.build.kotlin-version`   | `companionLibrariesKotlinVersion` | [`BUILD_COMPILER_VERSION`](../gradle-plugin-runtime/src/testFixtures/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/test/TestSupport.kt)                      |
| `kotlin.compiler.plugin.devkit.test.gradle-properties` | `functionalTestProperties`        | [`AbstractDevKitGradleProject`](../gradle-plugin-runtime/src/testFixtures/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/test/AbstractDevKitGradleProject.kt) |
| `kotlin.compiler.plugin.devkit.gradle.test.kmpTarget`  | `kmpTarget`                       | [`KmpTarget.selectedTargets()`](../gradle-plugin-runtime/src/testFixtures/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/test/KmpTarget.kt)                   |

The main fixtures:

- [
  `AbstractDevKitGradleProject`](../gradle-plugin-runtime/src/testFixtures/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/test/AbstractDevKitGradleProject.kt):
  the base class of a generated TestKit build that applies the plugin under test. A subclass sets `pluginUnderTest`. The
  build has one module or several:
    - Its `gradle.properties` has DevKit defaults, for example a read-only configuration cache, and then
      `functionalTestProperties`.
    - Its settings add the `bootstrap`, `dev`, `intellij-dependencies`, and DevKit EAP repositories.
    - The fixture skips a multiplatform build if `TEST_COMPILER_VERSION` is lower than `BUILD_COMPILER_VERSION`, because
      KLIBs aren't forward compatible.
- [
  `AbstractIncrementalCompilationTest`](../gradle-plugin-runtime/src/testFixtures/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/test/AbstractIncrementalCompilationTest.kt):
  a JUnit 4 base class for incremental compilation tests of a KMP build, for one `KmpTarget`. By default, it skips
  versions lower than `2.3.21`, the lowest version with working KMP incremental compilation. It skips non-JVM targets if
  `TEST_COMPILER_VERSION` is lower than `BUILD_COMPILER_VERSION`.
- [
  `KotlinPlugins.jvm()`](../gradle-plugin-runtime/src/testFixtures/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/test/KotlinPlugins.kt)
  and [
  `KotlinPlugins.multiplatform()`](../gradle-plugin-runtime/src/testFixtures/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/test/KotlinPlugins.kt):
  the Kotlin plugin of the generated build. The default version is `TEST_COMPILER_VERSION`.
- [
  `assertOutputContainsOnDifferentKotlinVersions(map)`](../gradle-plugin-runtime/src/testFixtures/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/test/SourceSupport.kt):
  finds the expected text for `TEST_COMPILER_VERSION` in the build output. If the map has no entry for that version, it
  uses the entry of the nearest lower base version.

## Versions

The jar of a compiler plugin or of a `compiler-library` holds the classes of every [version](#version). The DevKit
selects a version at Gradle build time and at compiler and IDE runtime. Both paths use
[Version resolution](#version-resolution).

### Version resolution

`version-resolution` has these parts:

- [
  `ide-mappings.txt`](../version-resolution/src/main/resources/META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/ide-mappings.txt)
  maps IDE builds to [IDE versions](#ide-version) ([#15](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/15)).
  The DevKit uses it to find the correct version on Android Studio, and to find the [branch point](#branch-point) of an
  IDE version.
- [
  `IdeBuildNumber`](../version-resolution/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/CompilerVersionAliases.kt),
  a simplified version of the IntelliJ `BuildNumber`. It sorts build numbers lexicographically by their `Int` segments.
- A function [
  `VersionResolution.resolveVersion`](../version-resolution/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/VersionResolution.kt)
  that chooses a compatible version from a list of [versions](#version), for a `currentVersion`. The first rule that
  matches applies:
    1. If `currentVersion` is in the list, return it. This is the most frequent case.
    2. If `currentVersion` is an EAP or a Release version, return
       `versions.filter { !it.isDev && it <= currentVersion }.maxOrNull()`.
    3. If `currentVersion` is an [IDE version](#ide-version) of the form `-ijXYZ-`, try these steps in order:
         1. `versions.filter { it.idePlatform == currentVersion.idePlatform && it <= currentVersion }.max()`, that is,
            an earlier version from the same IDE release.
         2. Run the algorithm again with the [branch point](#branch-point) of `XYZ`, over the [versions](#version)
            that aren't `-ij` versions. An `-ij` [version](#version) still in the list is ahead of `currentVersion` or
            from another IDE release, so the rerun must not take it.
         3. `versions.filter { it.hasSameBaseVersionAs(currentVersion) }.min()`.
    4. If `currentVersion` is a `-dev-` version, or an `-ij` version that rule 3 didn't resolve, try these steps in
       order:
         1. `versions.filter { it.isDev && it.hasSameBaseVersionAs(currentVersion) && it <= currentVersion }.max()`.
         2. `versions.filter { if (it.isDev) it <= currentVersion else it.baseVersion <= currentVersion.baseVersion }`
            `.maxOrNull()`. The Release version of the base version of `currentVersion` wins this step when the list has
            it.

  Usually the list holds the exact version or a close one (maybe
  simplify [#82](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/82)). When it doesn't, the experience is
  already bad. When no rule matches, `resolveVersion` returns null.

### The multi-release jar

The jar holds the plugin code for each version and the embedded dependencies:

- Plugin code is in `META-INF/kotlin/plugin/<plugin-id>/versions/<version>`. Dependencies are in
  `META-INF/kotlin/plugin/<plugin-id>/dependencies`.
- `<plugin-id>` in the path tells compiler plugins apart without an inspection of each jar. The DevKit inspects the jars
  anyway, but the path lets a custom `ClassLoader` find a plugin with `getResourceAsStream`.
- Each `<version>` under `versions` must be a valid [version](#version).
- Each directory under `dependencies` must be an unpacked jar. The DevKit resolves the version of each dependency
  separately.

[
`addOverlayAndDependencies`](../version-resolution/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/VersionResolution.kt)
rebuilds the classpath of a plugin from its resolved version and its dependencies. It takes a plugin ID, a jar path, and
the `currentVersion`. It adds the classpath roots to a list, and returns the resolved version, or null when the jar has
none:

1. If the jar doesn't have the `Kotlin-Compiler-Plugin-Multi-Release` attribute, return null.
2. List the versions in `META-INF/kotlin/plugin/<plugin-id>/versions`. If the directory is empty or missing, return
   null.
3. Resolve the [version](#version) with the version resolution function above.
4. Add the classpath roots of each dependency, recursively, with no ID: each dependency uses the only directory under
   its own `META-INF/kotlin/plugin`. If a dependency has no version to select, the whole dependency directory is one
   classpath root.
5. Add `META-INF/kotlin/plugin/<plugin-id>/versions/<version>`.
6. Add the jar root.

### In Gradle: artifact transform

[
`MultiReleasePluginTransform`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/MultiReleasePluginTransform.kt)
selects the version for the compilations of each `DevKitTarget`. It is an artifact transform on the attribute
`org.jetbrains.kotlin.compiler.version` ([
`KotlinCompilerVersion`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/KotlinCompilerVersion.kt)):

- Every artifact type has the value `noVersion`.
- The compile and runtime classpaths of each compilation request the version of its target. These compilations are
  exceptions:
    - The `generateTests*` compilations request the newest known [CLI version](#cli-version).
    - The `entryPoint` compilation requests no version, so it gets the original artifacts.
- The DevKit registers one transform from `noVersion` to each requested version.

Two more cases get the original artifacts:

- `includedDependencies` and `includedEntryPoint` of `compiler-plugin` request `noVersion`. Thus the plugin jar embeds
  its dependencies with all their versions, and the compiler selects one at runtime
  (investigate [#77](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/77)).
- For a project dependency, the IDE import requests `noVersion`, because IDEA can't identify the project from a
  transformed artifact.

The transform:

1. Runs the `addOverlayAndDependencies` function with no plugin ID, so it uses the only directory under
   `META-INF/kotlin/plugin`.
2. Returns the artifact unchanged if the function returns no version. So the transform changes only multi-version jars,
   and every other dependency passes through.
3. Copies the classpath roots into one artifact, in classpath order, ignoring `META-INF/kotlin/plugin`. If two roots
   have a file with the same path, the first root wins, as in a class loader.
4. Names the output `<input name>-mv-<version>`, where `<version>` is the resolved version.

The `apiElements`, `runtimeElements`, and `sourcesElements` variants of the non-surrogate targets (and their
`*Published` variants) have the extra attribute `org.jetbrains.kotlin.compiler.version.fake` ([
`FakeKotlinCompilerVersion`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/KotlinCompilerVersion.kt)).
Gradle prefers a variant without an extra attribute, so it selects the surrogate target (TODO disable them instead).

During IDE import, [
`IdeMultiVersionDependencyResolver`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/IdeMultiVersionDependencyResolver.kt)
uses the `-mv-<version>` suffix:

- If the file of a binary dependency ends with `-mv-<version>`, the resolver sets the `sourceSetName` of its coordinates
  to the target name of that version. Without it, IDEA makes one library for each GAV, and reuses one version of it in
  unrelated modules.
- If the resolved version isn't the requested version, the suffix doesn't match, and the resolver doesn't change the
  coordinates (known problem).

### In the compiler: plugin loading

For a `compiler-plugin`, `buildconfig` generates the internal object `<pluginPackage>.PluginInfo` in the `entryPoint`
compilation of the surrogate target:

- `PLUGIN_ID`: the plugin ID.
- `REGISTRAR_CLASS`: its initializer declares the local class `Plugin`. It extends
  `DevKitCompilerPluginRegistrar("<componentRegistrar>")` and sets `pluginId` to `PLUGIN_ID`.
- `PROCESSOR_CLASS`: the same for `DevKitCommandLineProcessor` and `commandLineProcessor`.

[`generateServices`](../plugins/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/DevKitCompilerPlugin.kt)
writes the `META-INF/services` files for `CompilerPluginRegistrar` and `CommandLineProcessor`. They name the local
classes by their binary names, `<pluginPackage>.PluginInfo$REGISTRAR_CLASS$1$Plugin` and
`<pluginPackage>.PluginInfo$PROCESSOR_CLASS$1$Plugin`. These names depend on the shape of the generated code. If the
shape changes, `generateServices` must change too.

On the first call to `registerExtensions`, `processOption`, or `pluginOptions`, the entry point loads the plugin:

1. The entry point calls [
   `VersionedPluginClassLoading.resolve`](../compiler-plugin-runtime/src/entryPoint/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/VersionedPluginClassLoading.kt)
   with its own class and the plugin ID.
2. `resolve` finds the current version (see below), and the URLs of the class loader of the entry point. For a class
   loader that isn't a `URLClassLoader`, it calls a public `getUrls()` method with reflection. The IntelliJ
   `UrlClassLoader` has one.
3. If the version or the URLs are unknown, `resolve` returns null, which skips loading the plugins.
4. Otherwise, `resolve` runs the `addOverlayAndDependencies` function on each URL, with the plugin ID. `resolve` ignores
   a jar that doesn't match that ID, so other plugins don't interfere with this one.
5. `resolve` makes a `URLClassLoader` over the result. Its parent is the **parent** of the class loader of the entry
   point. Thus, the plugin sees the compiler and its own classpath only. It doesn't see other plugins.
6. `resolve` caches the class loader for each plugin ID. `CommandLineProcessor` and `ComponentRegistrar` then exchange
   the same classes through `CompilerConfiguration`.
7. The entry point loads the `componentRegistrar` or `commandLineProcessor` class from that class loader, makes an
   instance, and calls it with reflection.

The class loader reads `jar:` URLs without the `URLConnection` cache, so it reads a rebuilt plugin jar again (hot
reload).

The current version:

- `resolve` gets it from `KotlinCompilerVersion.getVersion()`, then maps it with [
  `CompilerVersionAliases.map`](../version-resolution/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/CompilerVersionAliases.kt).
- `map` changes only the placeholder version of an Android Studio Canary build, `<major>.<minor>.255-dev-<n>`. For this
  version, `map` gets the IDE build number from `ApplicationInfo` with reflection. Then [
  `forIdeBuild`](../version-resolution/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/CompilerVersionAliases.kt)
  maps the build number through `ide-mappings.txt`.
- A build number without an entry gets the highest entry below it with the same baseline (the first number of the build
  number).
- Outside an IDE, or with no matching entry, the version doesn't change.

[
`ide-mappings.txt`](../version-resolution/src/main/resources/META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/ide-mappings.txt)
is at `META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/ide-mappings.txt`. Each line is
`<build number>=<version>`. [
`CompilerVersionAliases`](../version-resolution/src/main/kotlin/org/jetbrains/kotlin/compiler/plugin/devkit/CompilerVersionAliases.kt)
merges every copy that its class loader finds. For the same build number, a later copy on the classpath overrides an
earlier one. Plugins that use different DevKit versions share the
[entry point runtime classes](#plugindevkitcompiler-pluginplugindevkitcompiler-library), so the class that loads first
can come from an older DevKit. It still reads the mappings of every DevKit on the classpath.

## Data files

[`fetch-all.sh`](../fetch-all.sh) regenerates every checked-in data file, with one Python script for each file. It
aborts on the first failure: `fetch-all-kotlin-modules.py` exits with code `2` when a module mapping needs a human, and
`fetch-all-ide-kotlin-versions.py` exits with code `1` without an authenticated `gh`.

| File                                                                                                                                 | Script                                                                                       | Content                                                                                                                                     |
|--------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| [`kotlin-versions.txt`](../plugins/src/main/resources/META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/kotlin-versions.txt)      | [`fetch-all-kotlin-versions.py`](../plugins/fetch-all-kotlin-versions.py)                    | Every Kotlin release published to Maven Central, one release on each line.                                                                  |
| [`ide-versions.txt`](../plugins/src/main/resources/META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/ide-versions.txt)            | [`fetch-all-ide-versions.py`](../plugins/fetch-all-ide-versions.py)                          | The known IDE builds, keyed on the platform build, grouped by product and [IDE channel](#ide-channel).                                      |
| [`kotlin-modules.txt`](../plugins/src/main/resources/META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/kotlin-modules.txt)        | [`fetch-all-kotlin-modules.py`](../plugins/fetch-all-kotlin-modules.py)                      | The `org.jetbrains.kotlin` modules whose versions the DevKit forces, mapped to the name that the module goes by in `intellij-dependencies`. |
| [`ide-mappings.txt`](../version-resolution/src/main/resources/META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/ide-mappings.txt) | [`fetch-all-ide-kotlin-versions.py`](../version-resolution/fetch-all-ide-kotlin-versions.py) | The Kotlin version that each recorded IDE build bundles.                                                                                    |

- `kotlin-versions.txt` has no `dev` versions.
- `ide-versions.txt` uses the same key space as `ide-mappings.txt`. The file records the channel with the build. The
  DevKit plugins use the channel to register the IDE versions in range.
- `kotlin-modules.txt` is hand-curated. The script only finds modules, from `dev` and `intellij-dependencies`, and
  writes `foo=` for a module with no mapping. The run fails when a mapping is blank, until a human fills it in.
- `ide-mappings.txt` takes each IDE build's Kotlin version from the IDE's own library declaration. The script reads the
  newest `idea/*` tag at or below that build. The apt artifacts are available in `intellij-dependencies`.
