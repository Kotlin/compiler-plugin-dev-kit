# Spec

## Definitions

<dl>
    <dt id="functional-test">Functional test</dt>
    <dd>A Gradle integration test that uses Gradle TestKit.</dd>
    <dt id="gradle-support-plugin">Gradle support plugin</dt>
    <dd>A <code>KotlinCompilerPluginSupportPlugin</code>.</dd>
    <dt id="companion-library">Companion library</dt>
    <dd>A library that the compiler plugin needs on the classpath of the user's code. The Gradle support plugin adds it. Such a library usually contains annotations, and can also contain types and utilities. Other names:
    <code>plugin-annotations</code>, <code>support-library</code>, <code>prelude</code>.</dd>
    <dt id="version">Version</dt>
    <dd>A Kotlin version (<code>KotlinToolingVersion</code>), including <code>-dev-</code> and <code>-ijXYZ-</code> versions.</dd>
    <dt id="cli-version">CLI version</dt>
    <dd>A <a href="#version">version</a> that is available on Maven Central or in the <code>dev</code> or <code>bootstrap</code> repository. Command-line builds use it to compile code.</dd>
    <dt id="ide-version">IDE version</dt>
    <dd>A <a href="#version">version</a> that is available in the <code>intellij-dependencies</code> repository. IDEs use it for analysis, and sometimes to compile JVM code for <code>Evaluate Expression</code> and <code>Show Kotlin Bytecode</code>. An IDE version is always a <code>-dev-</code> or an <code>-ijXYZ-</code> version.</dd>
    <dd>By default, every <code>-dev-</code> version is an IDE version. A build can override this.</dd>
    <dd>All <code>-ijXYZ-</code> versions with the same <code>XYZ</code> come from the same major IDE release. For example, <code>263</code> is 2026.3.</dd>
    <dt id="branch-point">Branch point</dt>
    <dd>The <code>-dev-</code> <a href="#ide-version">version</a> that an <code>-ijXYZ-</code> <a href="#ide-version">version</a> branched from. Git ancestry gives the exact branch point. In practice, the branch point is the version of the closest <code>XYZ</code> EAP build.</dd>
    <dt id="base-version">Base version</dt>
    <dd>A Kotlin version (<code>KotlinVersion</code>) without a classifier, for example <code>1.9.0</code> or <code>2.4.21</code>. Each Kotlin <a href="#version">version</a> has a base version. To compare a <a href="#version">version</a> with a base version, convert the version to its base version.</dd>
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

- `version-resolution`: picks a compatible Kotlin [version](#version), maps IDE build numbers to
  [IDE versions](#ide-version), and parses the DevKit jar format.
- Gradle plugins:
    - [Settings plugin](#settings-plugin)
    - [`pluginDevKit("compiler-plugin")`](#plugindevkitcompiler-pluginplugindevkitcompiler-library): for the compiler
      plugin. Uses [`version-resolution`](#version-resolution).
    - [`pluginDevKit("gradle-plugin")`](#plugindevkitgradle-plugin): for
      the [Gradle support plugin](#gradle-support-plugin).
    - `pluginDevKit("functional-test-publishing")`: for all projects.
- Runtime libraries:
    - `compiler-plugin-runtime`: compiler plugin utilities and [entry point](#entry-point) classes. Uses
      `version-resolution`.
    - `compiler-plugin-test`: compiler plugin test utilities, including [classified dumps](#classified-dump).
    - `gradle-plugin-runtime`: [Gradle support plugin](#gradle-support-plugin) utilities. Its test fixtures help with
      [functional tests](#functional-test).

## Expected build structure

A typical compiler plugin has these parts:

- One compiler plugin
- One [Gradle support plugin](#gradle-support-plugin)
- Support plugins for other build tools, for example Maven or Amper
- Zero or more [companion libraries](#companion-library)

The DevKit supports three build structures:

1. One build for everything. This is the simplest structure, but it supports the smallest range of
   [versions](#version).
2. A composite build. The [companion libraries](#companion-library) use an older KGP, and everything else uses a newer
   KGP. This is the recommended structure, and
   the [compiler-plugin-template](https://github.com/Kotlin/compiler-plugin-template/tree/devkit) uses it.
3. Three builds. Use this structure to apply the compiler plugin to its own
   [companion libraries](#companion-library). The Gradle support plugin gets a separate build, because Isolated
   Projects doesn't support cyclic included builds.

One build limits the range of [versions](#version) for these reasons:

- KLIBs aren't forward compatible. For example, a `2.3` consumer can't use a KMP library that the `2.4` compiler built.
- An older KGP doesn't help, because the compiler plugin must compile against newer compiler versions.
- So the maximum range is 3 major versions (+2), with the experimental BTA `kotlin.compilerVersion`. Other structures
  can support 5 major versions (-3 +1).
  - For example, using KGP `2.4.0`, one can set `kotlin.compilerVersion = "2.5.0"`, and thus support `2.4.0-2.6.0`. for the composite build structure, using KGP `2.7.0` for compiler plugin, and `2.4.0` for companion libraries, one can support `2.4.0-2.8.0`

## Version resolution

`version-resolution` has these parts:

- A mapping of IDE builds to [IDE versions](#ide-version) (#15). The DevKit uses it to find the correct version on
  Android Studio, and to find the [branch point](#branch-point) of an IDE version.
- `IdeBuildNumber`, a simplified version of the IntelliJ `BuildNumber`. It sorts build numbers lexicographically by
  their `Int` segments.
- A function `VersionResolution.resolveVersion$org_jetbrains_kotlin_compiler_plugin_devkit_version_resolution` that
  chooses a compatible version from a list of [versions](#version), for a `currentVersion`. The first rule that matches
  applies:
    1. If `currentVersion` is in the list, return it. This is the most frequent case.
    2. If `currentVersion` is an EAP or a Release version, return
       `versions.filter { !it.isDev && it < currentVersion }.maxOrNull()`.
    3. If `currentVersion` is an [IDE version](#ide-version) of the form `-ijXYZ-`, try these steps in order:
         1. `versions.filter { it.classifier.startsWith("-ij<XYZ>") && it < currentVersion }.max()`, that is, an earlier
            version from the same IDE release.
         2. If `currentVersion` has a known [branch point](#branch-point), run the algorithm again with the branch
            point.
         3. `versions.filter { it.baseVersion == currentVersion.baseVersion }.min()`.
    4. If `currentVersion` is a `-dev-` version, or an `-ij` version that rule 3 didn't resolve, try these steps in
       order:
         1. `versions.filter { it.isDev && it.baseVersion == currentVersion.baseVersion && it < currentVersion }.max()`.
         2. The Release version of `currentVersion.baseVersion`.
         3. `versions.filter { it.baseVersion < currentVersion.baseVersion }.maxOrNull()`.

  The algorithm can be simpler. Usually, the list has the exact version or a close one. If it doesn't, the user
  experience is bad anyway.
- The jar format, which holds the plugin code for each version and the embedded dependencies:
    - Plugin code is in `META-INF/kotlin/plugin/<plugin-id>/versions/<version>`. Dependencies are in
      `META-INF/kotlin/plugin/<plugin-id>/dependencies`.
    - `<plugin-id>` in the path tells compiler plugins apart without an inspection of each jar. The DevKit inspects
      the jars anyway, but the path lets a custom `ClassLoader` find a plugin with `getResourceAsStream`.
    - Each `<version>` under `versions` must be a valid [version](#version).
    - Each directory under `dependencies` must be an unpacked jar. The DevKit resolves the version of each dependency
      separately. This is confusing and can be simpler.
- A function that rebuilds the classpath of a plugin from its resolved version and its dependencies. It takes a plugin
  ID, a jar path, and the `currentVersion`, and returns a list of directories in the jar (classpath roots):
    1. If the jar doesn't have the `Kotlin-Compiler-Plugin-Multi-Release` attribute, return null.
    2. List the versions in `META-INF/kotlin/plugin/<plugin-id>/versions`. If the directory is empty or missing, return
       null.
    3. Resolve the [version](#version) with the version resolution function above.
    4. Add the classpath roots of each dependency, recursively, with the ID of that dependency.
    5. Add `META-INF/kotlin/plugin/<plugin-id>/versions/<version>`.

## DevKit plugins

### Shared properties

- `testAgainst("2.x.y")` adds a [version](#version) to compile for and test against.
- `cliVersions(min, max, betaAndRc)` adds Release, Beta, and RC [CLI versions](#cli-version) from `min` to `max`.
    - `min` and `max` are [base versions](#base-version).
    - Open-ended ranges are supported.
    - The DevKit plugins contain a list of known non-dev [CLI versions](#cli-version) (known problem, #15).
    - `betaAndRc` is a `BetaAndRc`, an enum with 3 values:
        - `NONE`: Release [versions](#cli-version) in the range.
        - `LATEST`: Release [versions](#cli-version) in the range, and the Beta and RC [versions](#cli-version) of the
          latest base version in the range. The latest base version can have no Release yet. For example, if
          `2.5.0-Beta1` exists, `LATEST` adds it, and no Beta or RC of an earlier base version.
        - `ALL`: Release, Beta, and RC [versions](#cli-version) in the range.
- `ideaVersions(min, max, includeRc, includeEap)` adds Release, RC, and EAP [IDE versions](#ide-version).
    - `min` is an `IdeBuildNumber`, and `max` is an `Int`. This matches `sinceBuild` and `untilBuild`.
    - Open-ended ranges are supported.
    - It uses the IDE build mapping of [Version resolution](#version-resolution) (#15).
    - The DevKit plugins contain a list of known IDE builds for each [IDE channel](#ide-channel) (#15).
    - For each IDE marketing version, for example `2026.3.3`, only the latest build of each [IDE channel](#ide-channel)
      is used. In practice, only the latest EAP of a [version](#ide-version) is used (known problem, #70).
    - The name is misleading, because it also adds Android Studio versions. In practice, Android Studio uses a stable
      IntelliJ IDEA release as its base.
- `useLatestDev` gets the latest Kotlin [version](#cli-version) from the `dev` repository and adds it as a
  [CLI version](#cli-version). It is mainly for CI.
- `defaultTestVersion("2.x.y")` sets the [version](#version) that the `default*Test` tasks run against (possibly not
  useful, #71).
    - If the other properties don't add this [version](#version), `defaultTestVersion` adds it like `testAgainst` does.
- If none of the properties above add a [version](#version), the DevKit adds the `kotlinToolingVersion` of KGP as a
  [CLI version](#cli-version). So the build always has at least 1 [version](#version) (to change to
  `kotlin.compilerVersion`, #52).
- `pluginPackage("foo.bar")` sets the plugin ID, the package for buildconfig generation, and the classloader isolation
  ID (to be split, #55).
    - The default is the `group` of the project.
- `companionLibrary(notation)` adds a [companion library](#companion-library).
    - Compiler plugin test data can use the library.
    - The [Gradle support plugin](#gradle-support-plugin) adds it as a dependency of its users.
    - The DevKit reads `notation` as the first format in this list that matches:
        1. A project dependency, if it starts with `:`.
        2. An included build path, if it starts with `.` or `/`. The DevKit parses the path with `java.nio.Path`, and
           uses its name as a [sibling dependency](#sibling-dependency).
        3. A normal dependency notation, if it contains `:`.
        4. A [sibling dependency](#sibling-dependency).
- `companionLibrariesKotlinVersion` skips KMP tests for incompatible [versions](#version). The reason is in
  [Expected build structure](#expected-build-structure).
    - The default is the `kotlinToolingVersion` of KGP (to change to `kotlin.compilerVersion`, #52).
    - If the build adds an included build [companion library](#companion-library), the default changes to the lowest
      registered [version](#version), so the DevKit skips no tests (can be improved, #51).

### Settings plugin

- Adds the `bootstrap`, `dev`, and `intellij-dependencies` repositories to `pluginManagement` (known problem, #69) and
  to `dependencyResolutionManagement`.
- Applies `functional-test-publishing` to every project. That plugin applies `com.autonomousapps.testkit`, without the
  `testKitSupportFor*` publication.
- Configures the [shared properties](#shared-properties) for the other DevKit plugins (known problem, #68).
- Has utilities for a composite build setup:
    - `createVersionCatalogFrom(pathToRoot, pathToVersionCatalog, name)` makes a `name` version catalog from the file
      at `pathToRoot/pathToVersionCatalog`.
    - `includeBuildWithChecks` calls `includeBuild`, and makes the `check` task of the root project depend on the
      `check` task of the included build.
    - `includeCompanionBuild` calls `includeBuildWithChecks`, and configures the build as
      a [companion library](#companion-library).

### `pluginDevKit("gradle-plugin")`

- Applies `functional-test-publishing`, `buildconfig`, and `java-gradle-plugin`.
- Ignores all [IDE versions](#ide-version).
- Adds `gradle-plugin-runtime` and its test fixtures.
- Registers a [functional test](#functional-test) task for each [CLI version](#cli-version) (#54):
    - The registration logic is a copy of the testkit logic.
    - All tasks use the `gradleUserHomeDir` of the build, so the tests use dependencies that are already in its
      cache. No test task makes a separate Gradle cache.
- Registers an `allVersionedFunctionalTests` task, like the `allTests` task of KGP. The `check` task depends on it.
- Publishes all project [companion libraries](#companion-library) for [functional tests](#functional-test), and adds
  them to the test runtime classpath, so reflective calls succeed.
- For an included build [companion library](#companion-library), it registers the included build with
  `com.autonomousapps.testkit`, so the build is published for [functional tests](#functional-test) (can be improved,
  #53).
- Generates a `PluginInfo` object with `buildconfig`. The object contains the plugin ID, the plugin coordinates, and
  the [companion library](#companion-library) coordinates.
- Has these properties beyond the [shared properties](#shared-properties):
    - `functionalTestProperties`: lines that [functional tests](#functional-test) add to `gradle.properties`.
    - `kmpTarget`: the KMP target that multiplatform IC [functional tests](#functional-test) test. The default is
      `jvm`. The value is one of these:
        - A standard target name.
        - `native_host`: the native target that the current OS supports.
        - `all`.
    - `compilerPlugin`: the compiler plugin that the `DevKitSupportPlugin` applies. The value is one of these:
        - `LocalProject`: a project with the coordinates of a [sibling dependency](#sibling-dependency). It is
          published for [functional tests](#functional-test).
        - `External`: explicit coordinates.
        - `Sibling`: a [sibling dependency](#sibling-dependency).

### `pluginDevKit("compiler-plugin")`/`pluginDevKit("compiler-library")`

- Applies `buildconfig`, `node-gradle`, `idea`, `D8Plugin`, and `functional-test-publishing`.
- Has two variants: `compiler-plugin` for a compiler plugin, and `compiler-library` for the compiler plugin runtime
  libraries.
- Features of `compiler-plugin` only:
    - Fails if `commandLineProcessor` or `componentRegistrar` isn't set.
    - Generates the [entry point](#entry-point) classes and the plugin ID with `buildconfig`.
    - Embeds the dependencies into `META-INF/kotlin/plugin/<plugin-id>/dependencies`. It unpacks each jar into its own
      subdirectory.
    - Embeds the [entry point](#entry-point) runtime and [version resolution](#version-resolution) on the normal
      classpath. Thus, these classes can clash with other plugins on the classpath:
        - The [entry point](#entry-point) classes don't clash, because their package comes from the plugin ID.
        - The [entry point](#entry-point) runtime classes can clash between plugins that use different DevKit
          versions.
        - To prevent this, the package acts as a version of the classes. After the DevKit is public, each change to
          `version-resolution` or to the entry point logic *must* change the package of the changed classes.
- Registers a `DevKitTarget` for each [version](#version):
    - `DevKitTarget` uses the `ExternalKotlinTargetApi`.
    - Most of its configuration is a copy of the Kotlin JVM target and the Android target configuration.
    - A `DevKitTarget` has 3 default compilations: `main`, `testFixtures`, and `test`. `testFixtures` works like
      `java-test-fixtures`.
    - It has its own `DevKitTestRun`. It is almost the same as `JvmTestRun`, but it sets `targetName` to
      the [version](#version) string.
    - With `compiler-plugin`, the published POM declares no dependencies.
    - The compilations have resources and Java support (TODO: Java support has little use here, and Java sources
      possibly can't be shared).
    - For IDE import, each source set gets the highest version that it supports. The
      `preferredVersionForSharedSourceSetsInIde` property overrides this for the source sets that support that version.
      So different source sets see different versions of the compiler, the stdlib, and the test framework.
    - The DevKit forces the dependencies of each target to the matching Kotlin version. For
      [IDE versions](#ide-version), it also maps them to near-equivalent modules.
- Has a `versionHierarchy` DSL:
    - The `common`, `ide`, and `cli` groups are registered automatically. They contain all, [IDE](#ide-version),
      and [CLI](#cli-version) versions.
    - `groupVersions("name") { version -> condition }` groups the [versions](#version) that match `condition`. If the
      [branch point](#branch-point) is known, `version` is the branch point.
    - `pre`, `post`, and `split` group the versions before, from, and around a [version](#version). `preDev`,
      `postDev`, and `splitDev` do the same for a [base version](#base-version).
- Publishes everything in one jar with a "surrogate target":
    - The surrogate target is the first registered target. Its jar and its publications get the classes of all
      versions in `META-INF/kotlin/plugin/<plugin-id>/versions/`.
    - The manifest has the `Kotlin-Compiler-Plugin-Multi-Release` attribute. For IDE support, it also has
      `Kotlin-Compiler-Plugin-Since-Build` and `Kotlin-Compiler-Plugin-Until-Build`.
    - The DevKit generates the [entry point classes](#entry-point) into a separate `entry-point` compilation of the
      surrogate target.
- Generates tests with `generateTestsClass` into `generatedTestsDir`:
    - The test generator must be in the `generateTests` source set. This source set uses the newest known Kotlin
      version, to get the latest improvements to the test generator.
    - `internal-test-framework` isn't published for [IDE versions](#ide-version). IDE versions use
      `compiler-tests-for-ide`, which has no JS test infrastructure. So the generator has two compilations:
        - `generateTestsCli`: the DevKit creates it on the first CLI target, and associates it with the
          `testFixtures` compilation of that target. It sees the JS test infrastructure, and the classes for it in
          `cliTestFixtures`.
        - `generateTestsIde`: the DevKit creates it on the first IDE target, and associates it with the
          `testFixtures` compilation of that target. It doesn't see the JS test infrastructure.
    - The DevKit marks generated tests as generated sources for IDEA.
- Supports [companion libraries](#companion-library), and "conditional" libraries through the `testDataLibraries` DSL:
    - It has the `common`, `jvm`, and `js` `DependencyCollector`s.
    - It can register named library collections.
    - The DevKit passes all libraries to the compiler plugin test runtime. Tests get them through the generated
      buildconfig class `TestDataLibraries`.
- Passes the libraries that `internal-test-framework` needs as system properties. They always match the
  target [version](#version).
- Registers an artifact transform, so test fixtures and tests see a version-specific jar of the compiler plugin and of
  each `compiler-library`.
- 
