# Spec

## Definitions

<dl>
    <dt id="functional-test">Functional test</dt>
    <dd>A Gradle integration test that uses <a href="https://docs.gradle.org/current/userguide/test_kit.html">Gradle TestKit</a>.</dd>
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
- [Runtime libraries](#runtime-libraries):
    - [`compiler-plugin-runtime`](#compiler-plugin-runtime): compiler plugin utilities and [entry point](#entry-point)
      classes. Uses `version-resolution`.
    - [`compiler-plugin-test`](#compiler-plugin-test): compiler plugin test utilities, including
      [classified dumps](#classified-dump).
    - [`gradle-plugin-runtime`](#gradle-plugin-runtime): [Gradle support plugin](#gradle-support-plugin) utilities. Its
      test fixtures help with [functional tests](#functional-test).

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

## Version resolution

`version-resolution` has these parts:

- A mapping of IDE builds to [IDE versions](#ide-version) (#15). The DevKit uses it to find the correct version on
  Android Studio, and to find the [branch point](#branch-point) of an IDE version.
- `IdeBuildNumber`, a simplified version of the IntelliJ `BuildNumber`. It sorts build numbers lexicographically by
  their `Int` segments.
- A function `VersionResolution.resolveVersion` that chooses a compatible version from a list of [versions](#version),
  for a `currentVersion`. The first rule that matches applies:
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
    - `<plugin-id>` in the path tells compiler plugins apart without an inspection of each jar. The DevKit inspects the
      jars anyway, but the path lets a custom `ClassLoader` find a plugin with `getResourceAsStream`.
    - Each `<version>` under `versions` must be a valid [version](#version).
    - Each directory under `dependencies` must be an unpacked jar. The DevKit resolves the version of each dependency
      separately. This is confusing and can be simpler.
- A function that rebuilds the classpath of a plugin from its resolved version and its dependencies. It takes a plugin
  ID, a jar path, and the `currentVersion`, and returns a list of directories in the jar (classpath roots):
    1. If the jar doesn't have the `Kotlin-Compiler-Plugin-Multi-Release` attribute, return null.
    2. List the versions in `META-INF/kotlin/plugin/<plugin-id>/versions`. If the directory is empty or missing, return
       null.
    3. Resolve the [version](#version) with the version resolution function above.
    4. Add the classpath roots of each dependency, recursively, with the ID of that dependency. If a dependency has no
       version to select, the whole dependency directory is one classpath root.
    5. Add `META-INF/kotlin/plugin/<plugin-id>/versions/<version>`.
    6. Add the jar root.

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
- `useLatestDev`/`useLatestBootstrap` gets the latest Kotlin [version](#cli-version) from the `dev`/`bootstrap`
  repository and adds it as a [CLI version](#cli-version). They are mainly for CI.
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
    - `createVersionCatalogFrom(pathToRoot, pathToVersionCatalog, name)` makes a `name` version catalog from the file at
      `pathToRoot/pathToVersionCatalog`.
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
    - All tasks use the `gradleUserHomeDir` of the build, so the tests use dependencies that are already in its cache.
      No test task makes a separate Gradle cache.
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
    - `kmpTarget`: the KMP target that multiplatform IC [functional tests](#functional-test) test. The default is `jvm`.
      The value is one of these:
        - A standard target name.
        - `native_host`: the native target that the current OS supports.
        - `all`.
    - `compilerPlugin`: the compiler plugin that the `DevKitSupportPlugin` applies. The value is one of these:
        - `LocalProject`: a project with the coordinates of a [sibling dependency](#sibling-dependency). It is published
          for [functional tests](#functional-test).
        - `External`: explicit coordinates.
        - `Sibling`: a [sibling dependency](#sibling-dependency).

### `pluginDevKit("compiler-plugin")`/`pluginDevKit("compiler-library")`

- Applies `buildconfig`, `node-gradle`, `idea`, `D8Plugin`, and `functional-test-publishing`.
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
    - `pre`, `post`, and `split` group the versions before, from, and around a [version](#version). `preDev`, `postDev`,
      and `splitDev` do the same for a [base version](#base-version).
- Publishes everything in one jar with a "surrogate target":
    - The surrogate target is the first registered target. Its jar and its publications get the classes of all versions
      in `META-INF/kotlin/plugin/<plugin-id>/versions/`.
    - The manifest has the `Kotlin-Compiler-Plugin-Multi-Release` attribute. For IDE support, it also has
      `Kotlin-Compiler-Plugin-Since-Build` and `Kotlin-Compiler-Plugin-Until-Build`.
    - The DevKit generates the [entry point classes](#entry-point) into a separate `entry-point` compilation of the
      surrogate target.
- Generates tests with `generateTestsClass` into `generatedTestsDir`:
    - The `generateTestsEnabled` property turns generation off. Default is true for `compiler-plugin`, and false for
      `compiler-library`.
    - The test generator must be in the `generateTests` source set. This source set uses the newest known Kotlin
      version, to get the latest improvements to the test generator.
    - `internal-test-framework` isn't published for [IDE versions](#ide-version). IDE versions use
      `compiler-tests-for-ide`, which has no JS test infrastructure. So the generator has two compilations:
        - `generateTestsCli`: the DevKit creates it on the first CLI target, and associates it with the `testFixtures`
          compilation of that target. It sees the JS test infrastructure, and the classes for it in `cliTestFixtures`.
        - `generateTestsIde`: the DevKit creates it on the first IDE target, and associates it with the `testFixtures`
          compilation of that target. It doesn't see the JS test infrastructure.
    - The DevKit marks generated tests as generated sources for IDEA.
- Supports [companion libraries](#companion-library), and "conditional" libraries through the `testDataLibraries` DSL:
    - It has the `common`, `jvm`, and `js` `DependencyCollector`s.
    - It can register named library collections.
    - The DevKit passes all libraries to the compiler plugin test runtime. Tests get them through the generated
      buildconfig class `TestDataLibraries`.
- Passes the libraries that `internal-test-framework` needs as system properties. They always match the
  target [version](#version).
- Registers an [artifact transform](#in-gradle-artifact-transform), so each compilation sees a version-specific jar of
  the compiler plugin and of each `compiler-library`.

> [!WARNING]
> The rest of this spec is LLM-generated; it has been lightly checked for accuracy.

## Runtime libraries

### `compiler-plugin-runtime`

- The `compiler-plugin` and `compiler-library` plugins add it as an `implementation` dependency of `commonMain` and an
  `api` dependency of `commonTestFixtures`. `addRuntimeDependency = false` removes it and
  [`compiler-plugin-test`](#compiler-plugin-test).
- A compiler plugin implements two interfaces, and names the implementations with `componentRegistrar` and
  `commandLineProcessor`:
    - `DevKitComponentRegistrar`: `ExtensionStorage.registerExtensions(configuration)`, as in `CompilerPluginRegistrar`.
    - `DevKitCLP`: `processOption` and `pluginOptions`, as in `CommandLineProcessor`.
- Each implementation must have a public no-arg constructor, because the [entry point](#entry-point) instantiates it
  with reflection.
- The interfaces have no `pluginId` and no `supportsK2`. The entry point sets `pluginId` to the plugin ID, and
  `supportsK2` to `true`.
- `DevKitCompat.kt` has compatibility functions for compiler APIs that differ between versions, for example
  `pluginGenerated`. The `actual`s are in the version groups of `versionHierarchy`.
- The `entryPoint` compilation has `DevKitCompilerPluginRegistrar`, `DevKitCommandLineProcessor`, and
  `VersionedPluginClassLoading`. It isn't versioned, because it runs before the version is known.
  [In the compiler: plugin loading](#in-the-compiler-plugin-loading) describes these classes.

### `compiler-plugin-test`

The `compiler-plugin` and `compiler-library` plugins add it as an `api` dependency of `commonTestFixtures`.

#### Test bases

The test bases are in the package `org.jetbrains.kotlin.compiler.plugin.devkit.runners`:

- `DevKitTest` is the base of every other DevKit test base:
    - Its constructor takes configuration lambdas first, as a `vararg`, then `targetBackend` as a named argument.
    - Its second constructor wraps an `AbstractKotlinCompilerTest`, for example an `internal-test-framework` runner. It
      takes the configuration and the target backend of that test.
    - Every test gets the [version classifier](#classified-dumps), `firIdenticalCompat()`, and `DevKitTestConfigurator`.
    - It reads the standard library paths from the system properties that the `<target>Test` task sets.
    - `configure(builder)` is final. A subclass adds configuration through the constructor or the protected `configure`
      functions.
- The `Backported*` bases are copies of `internal-test-framework` bases, changed to compile against every
  [version](#version) that they support. They use the DevKit dump handlers.
  [Test bases](migration/4-compiler-plugin-tests.md#test-bases) maps each `internal-test-framework` base to its copy.
- The `DevKit*Test` runners add default directives to a base. By default, they all use the LightTree parser.

| Runner                    | Base                                        | Default directives                                                    |
|---------------------------|---------------------------------------------|-----------------------------------------------------------------------|
| `DevKitJvmBoxTest`        | `BackportedFirBlackBoxCodegenTestSpecBase`  | `DUMP_IR`, `FIR_DUMP`, `FULL_JDK`, `IGNORE_DEXING`                    |
| `DevKitJvmDiagnosticTest` | `BackportedFirPhasedDiagnosticTest`         | `FIR_DUMP`, `DISABLE_GENERATED_FIR_TAGS`, `FULL_JDK`, `IGNORE_DEXING` |
| `DevKitJsBoxTest`         | `MultiVersionJsTest`                        | `DUMP_IR`, `FIR_DUMP`                                                 |
| `DevKitJsDiagnosticTest`  | `BackportedJsDiagnosticWithBackendTestBase` | `FIR_DUMP`, `DISABLE_GENERATED_FIR_TAGS`                              |

#### Services

The services are in the package `org.jetbrains.kotlin.compiler.plugin.devkit.services`:

- `configurePlugin(registrar)` registers the extensions of a `DevKitComponentRegistrar` in every test module. It also
  calls `configureDefaultTestDataLibraries()`.
- `configureDefaultTestDataLibraries()` adds the default libraries of `testDataLibraries` to every module. They include
  the [companion libraries](#companion-library).
- `configureTestDataLibrary(library) { module -> condition }` adds a `TestDataLibrary` to the compile and runtime
  classpaths of the modules that match. A JVM module gets its JVM classpath, and a JS module gets its JS classpath.
- `TestDataLibrary(jvmProperty, jsProperty)` reads the two classpaths from system properties. It fails if a property is
  missing. The generated `TestDataLibraries` object has one `TestDataLibrary` for each named collection of
  `testDataLibraries`.
- `DevKitTestConfigurator` skips JS tests if `TEST_COMPILER_VERSION` is lower than `BUILD_COMPILER_VERSION`, because
  KLIBs aren't forward compatible. `TEST_COMPILER_VERSION` is the version of the test task. `BUILD_COMPILER_VERSION` is
  `companionLibrariesKotlinVersion`.

#### Version compatibility

- `DevKitTestCompat.kt` has `expect` declarations for test framework APIs that differ between versions. The `actual`s
  are in the version groups of `versionHierarchy`.
- Typealiases in `org.jetbrains.kotlin.test.builders` give every version both `NonGroupingPhaseTestConfigurationBuilder`
  and `NonGroupingStageTestConfigurationBuilder`. It's there for IDE purposes (`expect/actual` with
  `TestConfigurationBuilder` confuse the IDE since the `typealias` is immediately expanded, so these extra aliases trick
  the IDE into understanding that they're all the same)

#### CLI-only code

`src/cliMain` compiles only for [CLI versions](#cli-version), because an IDE version compiles against
`kotlin-compiler-tests-for-ide`. That module has no test generator and no JS test infrastructure. `cliMain` has:

- The test generator API. `generateDevKitTestsWithJUnit5 { }` calls `testGroup` for each test data directory that
  `generateTestsCli` or `generateTestsIde` gives it:
    - The task gives the directories in the system property `kotlin.compiler.plugin.devkit.roots`. Its value is pairs of
      an output directory and a comma-separated list of data directories, all separated by `;`.
    - The generated suites go to the package `<package of the test base>.<target name>`. The target name is the name of
      the output directory. The data directories after the first get `_<index>` after the target name.
- The JS runners: `BackportedJsTest`, `MultiVersionJsTest`, `BackportedJsDiagnosticWithBackendTestBase`,
  `DevKitJsBoxTest`, and `DevKitJsDiagnosticTest`.

Code that the JS runners share with the JVM runners, for example `setupHandlersForDiagnosticTest`, must stay in
`commonMain`.

IDE versions get no JS tests. The IDE uses only the JVM backend (for `Evaluate Expression`), and Analysis API tests
cover JS diagnostics better than compiler tests. The JVM and JS runners have different names for this reason.

#### Classified dumps

A classified dump has classifiers in its file name, between the test name and the dump extension. For the test data file
`foo.kt` and the extension `.fir.txt`, `foo.fir.txt` is the unclassified dump, and `foo.2420_dev_7885.fir.txt` is the
dump for `2.4.20-dev-7885`.

The model:

- `TestDumpRoot`: one dimension of classifiers, for example `version`. `calculateClassifiers()` lists its classifiers.
- `TestDumpClassifier`: one value of a root. Its `extension` is its part of the file name. Its `compatibleWith` is the
  classifier to fall back to, or `null` to fall back to no classifier of this root. The `compatibleWith` chain must have
  no cycles.
- `SingleTestDumpClassifier`: a root with one classifier.
- `TestDumpRoot.fixedClassifiers(directives)`: limits a root to some of its classifiers. A classifier collapses along
  its `compatibleWith` chain to the first fixed classifier, and the test always needs the file of that classifier. If
  the chain has no fixed classifier, the file name gets no classifier of this root.
- A file name has at most one classifier for each root, sorted by the `uniqueName` of the root.

The directives of `TestDumpDirectives`:

- `DUMP_CLASSIFIER`: the classifiers of a test. Only the test configuration can set it, not a test data file. It must
  have the whole `compatibleWith` chain of each classifier.
- `DUMP_AS_DIFF`: write each classified dump as a unified diff against the unclassified dump. The dump extension
  `.fir.txt` becomes `.fir.patch`.

The DevKit dump handlers, `BackportedFirDumpHandler` and `BackportedIrTextDumpHandler`, check their dumps with
`assertEqualsToDump`. `assertEqualsToDump`
uses two files:

- The fully classified file has all classifiers of the test.
- The fallback file is the existing file with the most specific classifiers that the classifiers of the test fall back
  to. If no such file exists, the fallback file is the unclassified dump.

The first rule that matches applies:

1. If the dump is empty, the fully classified file must not exist.
2. If the dump is the same as the fallback file, and no classifier is fixed, the fully classified file must not exist.
3. Otherwise, the dump must be the same as the fully classified file.

The version classifier:

- The root is `version`. It has one classifier for each registered [version](#version) except the highest. The
  `<target>Test` task gives the registered versions in the system property `kotlin.compiler.plugin.devkit.versions`.
- The name of a classifier is the version without `.`, with `-` replaced by `_`, in lowercase, for example
  `2420_dev_7885`. Unlike a target name, it has no `k` prefix.
- A classifier falls back to the classifier of the next higher version. The classifier of the second highest version
  falls back to the unclassified dump.
- `DevKitTest` sets `DUMP_CLASSIFIER` to the chain of the version of the current compiler.

So the highest registered version uses the unclassified dump. A lower version uses the dump of the nearest higher
version that has its own file. It needs its own file only if its dump is different. If the build registers a new highest
version, the previous highest version can need its own files.

`-PupdateTestData` sets the system property `kotlin.test.update.test.data`, so the test framework writes each dump that
doesn't match. The task checks only that the Gradle property exists, so `-PupdateTestData=false` also enables it.

### `gradle-plugin-runtime`

A JVM library for the [Gradle support plugin](#gradle-support-plugin). `pluginDevKit("gradle-plugin")` adds it and its
test fixtures.

`DevKitSupportPlugin(info)` is an abstract `KotlinCompilerPluginSupportPlugin`. A Gradle support plugin extends it, and
gives it the generated `PluginInfo.PLUGIN_INFO`, a `DevKitPluginInfo(id, artifact, companionLibraryCoordinates)`:

- `isApplicable` returns `true` for every compilation.
- `getCompilerPluginId` and `getPluginArtifact` return `info.id` and `info.artifact`.
- `applyToCompilation` is final. It adds each companion library as an `implementation` dependency of the default source
  set of the compilation. If `Project.shouldAddRuntimeDependency()` returns `false`, it adds none.
- The plugin options come from the hook `Project.applyToCompilation(kotlinCompilation)`. By default, it gives no
  options.

The test fixtures, in the package `org.jetbrains.kotlin.compiler.plugin.devkit.test`, help with
[functional tests](#functional-test). Each `<versionName>FunctionalTest` task gives them these system properties:

| System property                                        | Value                             | Read by                       |
|--------------------------------------------------------|-----------------------------------|-------------------------------|
| `kotlin.compiler.plugin.devkit.test.kotlin-version`    | The version of the task           | `TEST_COMPILER_VERSION`       |
| `kotlin.compiler.plugin.devkit.build.kotlin-version`   | `companionLibrariesKotlinVersion` | `BUILD_COMPILER_VERSION`      |
| `kotlin.compiler.plugin.devkit.test.gradle-properties` | `functionalTestProperties`        | `AbstractDevKitGradleProject` |
| `kotlin.compiler.plugin.devkit.gradle.test.kmpTarget`  | `kmpTarget`                       | `KmpTarget.selectedTargets()` |

The main fixtures:

- `AbstractDevKitGradleProject`: the base class of a generated TestKit build that applies the plugin under test. A
  subclass sets `pluginUnderTest`. The build has one module or several:
    - Its `gradle.properties` has DevKit defaults, for example a read-only configuration cache, and then
      `functionalTestProperties`.
    - Its settings add the `bootstrap`, `dev`, `intellij-dependencies`, and DevKit EAP repositories.
    - The fixture skips a multiplatform build if `TEST_COMPILER_VERSION` is lower than `BUILD_COMPILER_VERSION`.
- `AbstractIncrementalCompilationTest`: a JUnit 4 base class for incremental compilation tests of a KMP build, for one
  `KmpTarget`. By default, it skips versions lower than `2.3.21`. It skips non-JVM targets if `TEST_COMPILER_VERSION` is
  lower than `BUILD_COMPILER_VERSION`.
- `KmpTarget`: `JVM`, `JS`, `WASM_JS`, and `NATIVE_HOST`, the native target of the current OS and architecture.
- `KotlinPlugins.jvm()` and `KotlinPlugins.multiplatform()`: the Kotlin plugin of the generated build. The default
  version is `TEST_COMPILER_VERSION`.
- `assertOutputContainsOnDifferentKotlinVersions(map)`: finds the text for `TEST_COMPILER_VERSION` in the build output.
  If the map has no entry for that version, it uses the entry of the nearest lower base version.

## Version selection

The jar of a compiler plugin or of a `compiler-library` holds the classes of every [version](#version). The DevKit
selects one version at two points. Both use the classpath function of [Version resolution](#version-resolution).

### In Gradle: artifact transform

`MultiReleasePluginTransform` selects the version for the compilations of each `DevKitTarget`. It is an artifact
transform on the attribute `org.jetbrains.kotlin.compiler.version` (`KotlinCompilerVersion`):

- Every artifact type has the value `noVersion`.
- The compile and runtime classpaths of each compilation request the version of its target. These compilations are
  exceptions:
    - The `generateTests*` compilations request the newest known Kotlin version.
    - The `entryPoint` compilation requests no version, so it gets the original artifacts.
- The DevKit registers one transform from `noVersion` to each requested version.

The transform:

1. Runs the classpath function with no plugin ID. The ID is the only directory under `META-INF/kotlin/plugin`.
2. Returns the artifact unchanged if the function returns no version. Thus the transform changes only multi-release
   jars, and every other dependency passes through.
3. Copies the classpath roots into one artifact, in classpath order. It skips `META-INF/kotlin/plugin`. If two roots
   have a file with the same path, the first root wins, as in a class loader.
4. Names the output `<input name>-mv-<version>`, where `<version>` is the resolved version.

Two more cases get the original artifacts:

- `includedDependencies` and `includedEntryPoint` of `compiler-plugin` request `noVersion`. Thus the plugin jar embeds
  its dependencies with all their versions, and the compiler selects one at runtime.
- For a local project dependency, the IDE import requests `noVersion`, because IDEA can't identify the project from a
  transformed artifact.

In a project dependency, the variants of every target match. The `apiElements`, `runtimeElements`, and `sourcesElements`
variants of the other targets (and their `*Published` variants) have the extra attribute
`org.jetbrains.kotlin.compiler.version.fake` (`FakeKotlinCompilerVersion`). Gradle prefers a variant without an extra
attribute, so it selects the surrogate target (can be improved: disable these variants instead).

During IDE import, `IdeMultiVersionDependencyResolver` uses the `-mv-<version>` suffix:

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

`generateServices` writes the `META-INF/services` files for `CompilerPluginRegistrar` and `CommandLineProcessor`. They
name the local classes by their binary names, `<pluginPackage>.PluginInfo$REGISTRAR_CLASS$1$Plugin` and
`<pluginPackage>.PluginInfo$PROCESSOR_CLASS$1$Plugin`. These names depend on the shape of the generated code. If the
shape changes, `generateServices` must change too.

On the first call to `registerExtensions`, `processOption`, or `pluginOptions`, the entry point loads the plugin:

1. The entry point calls `VersionedPluginClassLoading.resolve` with its own class and the plugin ID.
2. `resolve` finds the current version (see below), and the URLs of the class loader of the entry point. For a class
   loader that isn't a `URLClassLoader`, it calls a public `getUrls()` method with reflection. The IntelliJ
   `UrlClassLoader` has one.
3. If the version or the URLs are unknown, `resolve` returns the class loader of the entry point itself.
4. Otherwise, `resolve` runs the classpath function on each URL, with the plugin ID. A jar without that plugin ID adds
   nothing.
5. `resolve` makes a `URLClassLoader` over the result. Its parent is the **parent** of the class loader of the entry
   point. Thus the plugin sees the compiler and its own classpath only. It doesn't see other plugins, or their copies of
   the entry point runtime.
6. `resolve` caches the class loader for each plugin ID.
7. The entry point loads the `componentRegistrar` or `commandLineProcessor` class from that class loader, makes an
   instance, and calls it with reflection. The parameter types, for example `CompilerConfiguration`, come from the
   compiler, which both class loaders share.

The class loader reads `jar:` URLs without the `URLConnection` cache, so it reads a rebuilt plugin jar again (hot
reload).

The current version:

- `resolve` gets it from `KotlinCompilerVersion.getVersion()`, then maps it with `CompilerVersionAliases.map`.
- `map` changes only the placeholder version of an Android Studio Canary build, `<major>.<minor>.255-dev-<n>`. For this
  version, `map` gets the IDE build number from `ApplicationInfo` with reflection. Then `forIdeBuild` maps the build
  number through `ide-mappings.txt`.
- A build number without an entry gets the highest entry below it with the same baseline (the first number of the build
  number).
- Outside an IDE, or with no matching entry, the version doesn't change.
- In an IDE, `resolveVersion` also uses the IDE build number to find the [branch point](#branch-point) of an `-ij`
  version (rule 3, step 2). Outside an IDE, for example in the artifact transform, that step doesn't apply.

`ide-mappings.txt` is at `META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/ide-mappings.txt`. Each line is
`<build number>=<version>`. `CompilerVersionAliases` merges every copy that its class loader finds. For the same build
number, a later copy on the classpath overrides an earlier one. Plugins that use different DevKit versions share the
[entry point runtime classes](#plugindevkitcompiler-pluginplugindevkitcompiler-library), so the class that loads first
can come from an older DevKit. It still reads the mappings of every DevKit on the classpath.
