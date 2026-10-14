# Spec

## Definitions

<dl>
    <dt id="functional-test">Functional test</dt>
    <dd>a Gradle integration test using Gradle TestKit</dd>
    <dt id="gradle-support-plugin">Gradle support plugin</dt>
    <dd>A <code>KotlinCompilerPluginSupportPlugin</code></dd>
    <dt id="companion-library">Companion library</dt>
    <dd>A library that the compiler plugin *needs* available to the user's code, and thus its Gradle support plugin adds it. Such a library usually contains annotations, and may include types and utilities. Synonyms include
    <code>plugin-annotations</code>, <code>support-library</code>, <code>prelude</code></dd>
    <dt id="version">Version</dt>
    <dd>A Kotlin version (<code>KotlinToolingVersion</code>), including <code>-dev-</code> and <code>-ijXYZ-</code> versions</dd>
    <dt id="cli-version">CLI version</dt>
    <dd>A <a href="#version">version</a> available on Maven Central, <code>dev</code>, or <code>bootstrap</code> repos, thus being used on CLI to compile code</dd>
    <dt id="ide-version">IDE version</dt>
    <dd>A <a href="#version">version</a> available on <code>intellij-dependencies</code> repo, thus being used in IDEs for analysis (and occasionally to compile JVM code for <code>Evaluate Expression</code> and <code>Show Kotlin Bytecode</code>). They're always <code>-dev-</code> and <code>-ijXYZ-</code> versions</dd>
    <dd>By default, all <code>-dev-</code> versions are assumed to be IDE versions, but one can override this.</dd>
    <dd><code>-ijXYZ-</code> versions for the same <code>XYZ</code> are from the same big release version of the IDE (e.g. <code>263</code> corresponds to 2026.3)</dd>
    <dt id="branch-point">Branch point</dt>
    <dd>Each <code>-ijXYZ-</code> <a href="#ide-version">version</a> has some <code>-dev-</code> <a href="#ide-version">version</a> it branched from. This can be worked out through git ancestry; in practice, the branch point is the version in the closest <code>XYZ</code> EAP build</dd>
    <dt id="base-version">Base version</dt>
    <dd>A Kotlin version (<code>KotlinVersion</code>) without classifier, e.g. <code>1.9.0</code> or <code>2.4.21</code>. Each Kotlin <a href="#version">version</a> has a base version. Comparing a Kotlin <a href="#version">version</a> against a base version is done by converting the former to its base version</dd>
    <dt id="ide-stability">IDE stability</dt>
    <dd>Either Release, RC, or EAP, in IntelliJ terms. For Android Studio, Beta is considered RC, and Canary is considered EAP</dd>
    <dt id="ide-channel">IDE channel</dt>
    <dd>A combination of its stability and whether it's IntelliJ or Android Studio</dd>
    <dt id="sibling-dependency">Sibling dependency</dt>
    <dd>A dependency on a project in the same <code>group</code> and having the same <code>version</code>. This is useful in the context of composite builds</dd>
    <dt id="entry-point">Entry point</dt>
    <dd>A class that the Kotlin compiler instantiates in a plugin. Specifically, <code>CommandLineProcessor</code> and <code>CompilerPluginRegistrar</code></dd>
    <dt id="classified-dump">Classified dump</dt>
    <dd>A dump (golden data) file with "classifiers" that distinguish it from other similar dumps. For instance, <a href="#version">version</a> can be a classifier to distinguish dumps based on compiler version</dd>
</dl>

## DevKit Structure

- `version-resolution` for picking compatible Kotlin [version](#version), mapping IDE build numbers to
  `dev` [versions](#version), and parsing our jar format.
- Gradle plugins:
    - Settings plugin
    - `pluginDevKit("compiler-plugin")` for compiler plugin; uses `version-resolution`
    - `pluginDevKit("gradle-plugin")` for [Gradle support plugin](#gradle-support-plugin)
    - `pluginDevKit("functional-test-publishing")` for all projects
- Runtime libraries:
    - `compiler-plugin-runtime` for compiler plugin utilities and [entry point](#entry-point) classes; uses
      `version-resolution`
    - `compiler-plugin-test` for compiler plugin test utilities, incl [classified dumps](#classified-dump)
    - `gradle-plugin-runtime` for [Gradle support plugin](#gradle-support-plugin) utilities; has test fixtures for
      easier [functional testing](#functional-test)

## Expected build structure

A typical compiler plugin has the following anatomy:

- 1 compiler plugin
- 1 [Gradle support plugin](#gradle-support-plugin)
- Other build tool support plugins (Maven, Amper, etc)
- 0 or more [companion libraries](#companion-library)

The DevKit user has multiple supported options for their build structure.
The simplest is to have everything in the same build, and we do support that structure.
Sadly, KLIBs are not forwards-compatible (e.g. KMP library built using `2.4` compiler cannot be used by `2.3` consumers)
Setting KGP to an earlier version doesn't do the trick either because the compiler plugin itself needs to
compile against newer compiler versions.
This reduces the range of [versions](#version) the user can support. The maximum range possible (using experimental BTA
`kotlin.compilerVersion`) is 3 major versions (+2), vs 5 that are normally attainable (-3 +1).
Thus, we support (and recommend, through
the [compiler-plugin-template](https://github.com/Kotlin/compiler-plugin-template/tree/devkit)) a composite-build setup
that allows the user to use older KGP for [companion libraries](#companion-library), and newer KGP for everything else.
We also support "dog-fooding" the compiler plugin on its [companion libraries](#companion-library), by using a 3-build
setup that further separates the Gradle support plugin from everything else (Cyclic included builds aren't supported in
IP)

## Version resolution

- Ships with a mapping of IDE builds to [IDE versions](#ide-version) (#15). This is used to identify the correct version
  on AS, and for finding the [branch point](#branch-point) of IDE versions
- Has a simplified version of Idea's `BuildNumber` called `IdeBuildNumber`, which is sorted lexicographically based on
  the `Int` segments of the build number
- Exposes a function that, given a `currentVersion` and a list of [versions](#version), chooses an apt version as
  follows (mostly inherited from Metro):
    - If `currentVersion` is amongst the list of versions, we pick it. This is the most likely case
    - If `currentVersion` is an EAP or Release version:
        - Return `versions.filter { !it.isDev && it < currentVersion }.maxOrNull()`
    - If `currentVersion` is an [IDE version](#ide-version) of the form `-ijXYZ-`:
        - Try `versions.filter { it.classifier.startsWith("-ij<XYZ>") && it < currentVersion }.max()` (i.e. earlier
          versions from the same IDE release)
        - Else, if it has a known [branch point](#branch-point), rerun the algorithm using it
        - Else, try `versions.filter { it.baseVersion == currentVersion.baseVersion }.min()`
    - If `currentVersion` is a "dev" non-EAP version (i.e. `-dev` or `-ij`):
        - Try
          `versions.filter { it.isDev && it.baseVersion == currentVersion.baseVersion && it < currentVersion }.max()`
        - Else, try the release equivalent of `currentVersion.baseVersion`
        - Else, return `versions.filter { it.baseVersion < currentVersion.baseVersion }.maxOrNull()`
    - Realistically, this algorithm should be simplified, since in most cases we have the exact version or something
      very close to it, and if we don't, the user experience will be rather bad anyway
- Specifies the dependency embedding and multi-versioning jar format:
    - Expects plugin code to live under `META-INF/kotlin/plugin/<plugin-id>/versions/<version>`, and dependencies to
      live under `META-INF/kotlin/plugin/<plugin-id>/dependencies`
    - `<plugin-id>` is part of the path to distinguish different compiler plugins without inspecting individual jars.
      Currently, we inspect jars anyway, but this design allows for e.g. a custom `ClassLoader` that uses
      `getResourceAsStream`.
    - Every `<version>` under `versions` is assumed to be a valid [version](#version)
    - Every directory under `dependencies` is assumed to be an unpacked jar. We treat dependencies individually so they
      independently get version resolution (this is mildly confusing and can be simplified)
    - Exposes a function that takes a plugin ID, a Jar path, and the `currentVersion`, and produces a list of inner
      directories (i.e. classpath roots) as follows:
        - Verifies that the Jar has the `Kotlin-Compiler-Plugin-Multi-Release` attribute, returning null if not
        - Lists the `versions` found in `META-INF/kotlin/plugin/<plugin-id>/versions`, returning null if none are found
        - Resolves the apt [version](#version) using the version resolution routine from above
        - Recursively add the plugin's dependencies as classpath roots, using the dependencies' individual IDs.
        - Adds `META-INF/kotlin/plugin/<plugin-id>/versions/<version>` as a classpath root
        - In other words, this function reconstructs the plugin's classpath by picking the most apt version of it, and
          tacking on its dependencies.

## DevKit plugins

### Shared properties

- `testAgainst("2.x.y")` adds an explicit [version](#version) to compile for and test against
- `cliVersions(min, max, betaAndRc)` adds Release, Beta, and RC [CLI versions](#cli-version) between `min` and `max`
    - `min` and `max` are [base versions](#base-version)
    - Open-ended ranges are supported
    - DevKit plugins ship with a list of known non-dev [CLI versions](#cli-version) (not ideal, #15)
    - `BetaAndRc` is an enum with 3 values:
        - `NONE` adds `Release` [versions](#cli-version) in range
        - `LATEST` adds `Release` [versions](#cli-version) in range, and `Beta`, and `RC` [versions](#cli-version) for
          the latest known base version
            - This means `2.5.0-Beta1` existing causes only it to be added, even though `2.5.0` is not out yet
        - `ALL` adds `Release`, `Beta`, and `RC` [versions](#cli-version) in range
- `ideaVersions(min, max, includeRc, includeEap)` adds Release, RC, and EAP [IDE versions](#ide-version)
    - `min` is an `IdeBuildNumber`, while `max` is just an `Int`, matching `sinceBuild` and `untilBuild`
    - Open-ended ranges are supported
    - `version-resolution` ships with a mapping of IDE builds to [IDE versions](#ide-version) (#15)
    - DevKit plugins ship with a list of known IDE builds per [IDE channel](#ide-channel) (#15)
    - For an IDE "marketing version" (e.g. `2026.3.3`), only the latest build is used per [IDE channel](#ide-channel).
      In practice, this means only the latest EAP for a [version](#ide-version) is used (bad, #70).
    - The name is misleading since Android Studio versions are included as well. In practice, AS seems to always use a
      stable IDE as its basis
- `useLatestDev` dynamically queries the latest Kotlin [version](#cli-version) in `dev` repository and adds it as a
  [CLI version](#cli-version)
    - This is intended primarily for CI
- `defaultTestVersion("2.x.y")` chooses a [version](#version) that tests will run against for `default*Test` tasks
  (likely not useful, #71)
    - If the [version](#version) isn't covered by the above properties, it's added as if added by `testAgainst`.
- If none of the above properties add any [versions](#version), the `kotlinToolingVersion` provided by KGP is used as
  a [CLI version](#cli-version), so at least 1 [version](#version) is guaranteed (should use `kotlin.compilerVersion`
  #52)
- `pluginPackage("foo.bar")` determines the plugin ID, the package for Build Config generation, and the "classloader
  isolation id" (should be split, #55)
    - If not set, it defaults to the project's `group`
- `companionLibrary(notation)` adds a [companion library](#companion-library)
    - Compiler plugin test data have access to the library
    - [Gradle support plugin](#gradle-support-plugin) adds it as a dependency to its users
    - `notation` can be one of the following formats:
        - Project dependency starting with `:`
        - Included build path starting with `.` or `/` (the path is parsed using `java.nio.Path`, and its name is taken
          as a [sibling dependency](#sibling-dependency))
        - Normal dependency notation if it includes `:` somewhere
        - Otherwise, it's treated as a [sibling dependency](#sibling-dependency)
- `companionLibrariesKotlinVersion`, which is used to skip KMP tests for incompatible [versions](#version)
    - It defaults to KGP's `kotlinToolingVersion` (should be swapped to `kotlin.compilerVersion` #52), but changes to
      the minimum registered [version](#version) (i.e. does nothing) if an included
      build [companion library](#companion-library) is added (can be improved #51)
    - see [Expected build structure](#expected-build-structure)

### Settings plugin

- Adds `bootstrap`, `dev`, and `intellij-dependencies` repos to `pluginManagement` (bad, #69) and
  `dependencyResolutionManagement`
- Applies `functional-test-publishing` to every project, which applies `com.autonomousapps.testkit` (w/o
  `testKitSupportFor*` publication)
- Configures [shared properties](#shared-properties) between the other DevKit plugins (not ideal, #68)
- Provides simple utilities for composite build setup:
    - `createVersionCatalogFrom(pathToRoot, pathToVersionCatalog, name)` creates a `name` version catalog using the file
      at `pathToRoot/pathToVersionCatalog`
    - `includeBuildWithChecks` calls `includeBuild` and wires its `check` task to root project's `check`
    - `includeCompanionBuild` calls `includeBuildWithChecks` and configures the build as
      a [companion library](#companion-library)

### `pluginDevKit("gradle-plugin")`

- Applies `functional-test-publishing`, `buildconfig`, and `java-gradle-plugin`
- Ignores any [IDE versions](#ide-version)
- Adds [gradle-plugin-runtime](#gradle-plugin-runtime) and its test fixtures
- Registers a [functional test](#functional-test) task for each [version](#cli-version) (#54):
    - Registration logic is copied from testkit
    - We reuse `gradleUserHomeDir` to speed up tests, since a lot of the dependencies used may already be cached.
      This prevents the build-up of a separate Gradle cache per test task
- Registers a `allVersionedFunctionalTests` task, analogous to KGP's `allTests`, and wires it to `check` task
- Publishes any project-based [companion libraries](#companion-library) for [functional tests](#functional-test), and
  adds them onto the test runtime classpath so reflective calls succeed
- Other than the [shared properties](#shared-properties), it has:
    - `functionalTestProperties`, which are passed to [functional tests](#functional-test) to append to
      `gradle.properties`
    - `kmpTarget`, which is passed to multiplatform IC [functional tests](#functional-test) to change which KMP target
      they focus on
        - Can be a standard target name, `native_host` (meaning the supported native target on this OS), or `all`
        - By default, it sets `jvm`
    - `compilerPlugin`, which is passed to the `DevKitSupportPlugin`. It can be one of the following:
        - `LocalProject`, meaning its coordinates are worked out like a [sibling dependency](#sibling-dependency), and
          it'll be published for [functional tests](#functional-test)
        - `External`, meaning it has explicit coordinates
        - `Sibling`, meaning it's a [sibling dependency](#sibling-dependency)
- When presented with an included build path [companion library](#companion-library):
    - It registers it with `com.autonomousapps.testkit` so the included build is published
      for [functional tests](#functional-test) (can be improved #53)
- Generates (using `build-config`) a `PluginInfo` object containing the plugin ID, plugin coordinates,
  and [companion library](#companion-library) coordinates

### `pluginDevKit("compiler-plugin")`/`pluginDevKit("compiler-library")`

- Applies `buildconfig`, `node-gradle`, `idea`, `D8Plugin`, and `functional-test-publishing`
- Has `compiler-plugin` and `compiler-library` flavors; `compiler-library` is used for the compiler plugin runtime libs
- `compiler-plugin`-only features:
    - errors if `commandLineProcessor` and `componentRegistrar` aren't set
    - Generates [entry point](#entry-point) classes and the plugin ID using `build-config`
    - embeds dependencies into `META-INF/kotlin/plugin/<plugin-id>/dependencies` by unpacking each jar into its own
      subdirectory
    - embeds [entry point](#entry-point) runtime and [version resolution](#version-resolution) on normal classpath
        - Note: this means [entry point](#entry-point) classes may interfere with other plugins on the classpath.
          The [entry points](#entry-point) themselves are in a package based on the plugin's ID, so interference is not
          a concern. The [entry point](#entry-point) runtime are the only clash risk, i.e. plugins that use different
          DevKit versions may interfere. To mitigate this, once the DevKit is public, changes to `version-resolution`
          and entry-point logic *must* change the package of the affected classes. This effectively versions the
          classes, thus preventing any clashes.
- For every [version](#version), it registers a `DevKitTarget`:
    - This uses the `ExternalKotlinTargetApi`
    - A lot of the configuration surrounding this is copied from Kotlin JVM target and Android target
    - A `DevKitTarget` has 3 default compilations: `main`, `testFixtures`, `test`, with `testFixtures` mimicking
      `java-test-fixtures`.
    - It has its own `DevKitTestRun`, which is near-identical to `JvmTestRun`, except it sets `targetName` to
      the [version](#version) string
    - If we're using `compiler-plugin`, the published POM has no dependencies declared
    - The compilations have resources and Java support (TODO java support is pretty useless here; it can't even be
      shared AFAIK)
    - For IDE import, for each source set, we pick the highest version it supports as what the IDE sees. We also provide
      an override in the form of `preferredVersionForIdeImport` property, which only applies to source sets that support
      it. This means different source sets really do see different versions of the compiler, stdlib, test framework,
      etc.
    - The dependencies of each target are forced to follow the apt Kotlin version. Further, they're mapped to near-equivalent ones on [IDE versions](#ide-version)
- It offers a `versionHierarchy` DSL:
    - `common`, `ide`, and `cli` groups are registered automatically, covering all, [IDE](#ide-version),
      and [CLI](#cli-version) versions respectively.
    - `groupVersions("name") { version -> condition }` groups [versions](#version) based on the `condition`. The
      `version` provided is the [branch point](#branch-point), if available
    - `pre`, `post`, and `split` functions are provided that operate based on a [version](#version)
      or [base version](#base-version)
- It publishes everything in a single Jar by using a "surrogate target":
    - The surrogate target is an arbitrarily-chosen target, whose Jar and publications are then filled with the apt
      `META-INF/kotlin/plugin/<plugin-id>/versions/` versioned classes.
    - The manifest has `Kotlin-Compiler-Plugin-Multi-Release` attribute, as well as `Kotlin-Compiler-Plugin-Since-Build` and `Kotlin-Compiler-Plugin-Until-Build` for IDE support.
    - The [entry point classes](#entry-point) are generated into a separate `entry-point` compilation on the surrogate
      target.
- Generates tests through `generateTestsClass` into `generatedTestsDir`:
    - To benefit from test generator improvements, `generateTests` source set is where the generator must live, and it
      uses the latest EAP version of the generator
    - `internal-test-framework` is not published for [IDE versions](#ide-version), so instead `compiler-tests-for-ide`
      is used, which doesn't have JS test infra. Thus, `generateTestsIde` and `generateTestsCli` are separate
      compilations created on an arbitrary target of the correct version type (IDE or CLI), and associated with its
      test-fixtures compilation. `generateTestsCli` can thus
      see JS test infra (and classes defined for it in `cliTestFixtures`), while `generateTestsIde` can't.
    - Generated tests are marked as generated sources for IDEA
- [Companion libraries](#companion-library) are supported, as well as "conditional" libraries through
  `testDataLibraries` DSL:
    - has `common`, `jvm`, `js` `DependencyCollector`s
    - allows registering named library collections
    - all libraries are passed onto the compiler plugin test runtime, and accessible thru generated build config
      `TestDataLibraries`
- The libraries needed for `internal-test-framework` are provided as system properties. They always match the target [version](#version)
- An artifact transform is registered so test fixtures and tests see a version-specific jar of the compiler plugin (and similarly with any `compiler-library`)
- 