# AGENTS.md

## What this is

A toolkit ("dev kit") for building **Kotlin compiler plugins that support many Kotlin compiler
versions from one source tree**. It is not a compiler plugin itself — it publishes Gradle plugins and
runtime/test-fixture libraries under `org.jetbrains.kotlin.compiler.plugin.devkit:*` at
`0.0.1-SNAPSHOT`, consumed by real plugins (e.g. Metro).

Kotlin compiler internals are unstable across versions, so the dev kit lets a plugin keep one shared
`src/main` plus small per-version overlay source sets, then compiles/tests/packages all of them and
picks the right one at compile time and at compiler runtime.

## The working tree moves between turns

The author edits this repo between prompts, including files you wrote yourself. Don't trust your memory
of a file from an earlier turn — re-read it before editing, and check `git status` / `git diff` when
picking work back up. An edit that fails to apply usually means the file moved on, not that you
misread it; look at the new contents before deciding the change is still wanted.

## Keeping this file current

Re-read this file at the **end** of a change, once the work is done and verified — not while planning.
If the change made anything here stale or left a new non-obvious convention undocumented (build layout,
task names, hardcoded versions, conventions), say so in your final response and propose the concrete
edit. Don't rewrite this file mid-task, and don't let a stale line stand silently.

Keep the bar for *adding* high. This file is for what a reader can't recover on their own: intent,
cross-build wiring, rules they'd unknowingly break. Leave out anything the toolchain surfaces by
itself — a compile error, `./gradlew tasks`, or a minute spent reading the source. Notes like that age
badly and bury the constraints that actually matter.

## Build layout: three separate Gradle builds

This is a composite build. Nothing is a plain subproject of everything else.

| Build | Contents | Wired in via |
| --- | --- | --- |
| root (`settings.gradle.kts`) | `compiler-plugin-runtime`, `gradle-plugin-runtime` | — |
| `plugins/` | the Gradle plugins; `plugins/settings/` holds the settings plugin | `includeBuild` in root `pluginManagement` |
| `artifact-transform/` | version parsing/resolution, shared by build time *and* compiler runtime | `includeBuild` in root `dependencyResolutionManagement` |

Because of `includeBuild`, edits in `plugins/` or `artifact-transform/` are picked up by the root
build with no publishing step. Consumers *outside* this repo need a publish.

The root build applies the settings plugin (`kotlin("compiler.plugin.devkit")`), which adds the
Kotlin bootstrap/dev/intellij-dependencies redirector repositories. Building versions off those
repos needs network.

### Published plugin IDs

- `org.jetbrains.kotlin.compiler.plugin.devkit.compiler-plugin` → `DevKitCompilerPlugin`
- `org.jetbrains.kotlin.compiler.plugin.devkit.gradle-plugin` → `DevKitGradlePlugin`
- `org.jetbrains.kotlin.compiler.plugin.devkit.functional-test-publishing` → `DevKitFunctionalTestPublishingPlugin`
- `org.jetbrains.kotlin.compiler.plugin.devkit` (settings plugin) → `DevKitSettingsPlugin`

## The central mechanism: multi-release compiler-plugin jars

1. **Naming.** A Kotlin version maps to a source-set name via `KotlinToolingVersion.versionName` in
   `plugins/src/main/kotlin/.../utils.kt`: `k` + version with `.` removed and `-` → `_`, lowercased.
   `2.4.20-dev-7885` → `k2420_dev_7885`. (Convention borrowed from Metro.)
2. **Layout.** Version overlays live in `<versionName>/src/main/kotlin` and
   `<versionName>/src/testFixtures/kotlin` at the module root — *not* under `src/`. They are
   `associateWith` the shared compilation and additionally source the shared dirs, so shared code is
   recompiled per version against that version's compiler.
3. **Packaging.** Each overlay's output is copied into `META-INF/kotlin/plugin/versions/<version>/`
   in the jar, and every `Jar` gets manifest attribute `Kotlin-Compiler-Plugin-Multi-Release: true`.
4. **Selection at Gradle build time.** `MultiReleasePluginTransform` is an artifact transform keyed
   on the `org.jetbrains.kotlin.compiler.version` attribute (`KotlinCompilerVersion`). It flattens
   the matching version directory into the jar root.
5. **Selection at compiler runtime.** `VersionedPluginClassLoading` builds a `URLClassLoader` that
   prepends the matching version directory, and `DevKitCompilerPluginRegistrar` /
   `DevKitCommandLineProcessor` reflectively delegate the real work into it. This is why plugin
   entry points subclass those and implement `DevKitComponentRegistrar` / `DevKitCLP`.
6. **Which version matches.** `VersionResolution.resolveVersion` picks the highest compatible
   version, special-casing the `dev` track so `2.3.20-dev-7791` doesn't match `2.3.20-Beta1`.
   `CompilerVersionAliases` maps fake IDE compiler versions (Android Studio canary reports
   `2.3.255-dev-255`) using `artifact-transform/ide-mappings.txt`, regenerated by
   `fetch-all-ide-kotlin-versions.py`. `CLI_ONLY` in that map means "skip FIR extensions in the IDE".

## Module reference

### `plugins/` — `DevKitCompilerPlugin` (`pluginDevKit { }`)

For the compiler-plugin module. Applies `kotlin("jvm")`, `jvm-test-suite`, `buildconfig`,
`animalsniffer`, `java-test-fixtures`, node, `idea`, `maven-publish`, D8.

- `testAgainst(version)` registers a `JvmTestSuite` named `<versionName>Test` (idempotent).
  `.withFixtures()` adds a `<versionName>TestFixtures` source set; `.withMain()` / `developFor(...)`
  adds a `<versionName>` main source set. `testAgainstWithFixtures` / `developForWithFixtures` are
  the combined shortcuts. The type parameters on `DevKitTestTargetT` exist so `target.main` /
  `target.testFixtures` are statically non-null only when actually registered.
- Shared source sets are `src/main`, `src/testFixtures`, `src/test`; **test resources are remapped to
  `src/test/data`** (test data, not classpath resources).
- Every source set forces all `org.jetbrains.kotlin` dependencies to its target version.
- `test` is **disabled** and just depends on `defaultTestTarget` (default: the build's own Kotlin
  version). `allVersionedTests` aggregates all of them and is wired into `check`.
- `generateTests` runs `generateTestsClass` (default `<pluginPackage>.GenerateTestsKt`) into
  `build/test-gen`, and every version's `compileJava` depends on it.
- Test tasks get the `org.jetbrains.kotlin.test.*` library system properties the Kotlin internal test
  framework needs, plus D8 and a generated `repl.js` for JS/Wasm tests.
- `testDataLibraries { }` / `testDataLibraries.register(name)` expose dependency collectors to test
  data via `<name>.jvm.classpath` / `<name>.js.classpath` system properties and a BuildConfig
  `TestDataLibraries` object.
- AnimalSniffer signatures are generated per target version, so using an API absent from an older
  compiler is caught without running that version's tests.
- If `com.gradleup.shadow` is applied, the main jar becomes `-unshaded` and `shadowJar` takes the
  empty classifier; `apiElements`/`runtimeElements` are repointed at the shadow jar.

### `plugins/` — `DevKitGradlePlugin` (`pluginDevKit { }`)

For a plugin's *Gradle* plugin module. Applies `kotlin("jvm")`, `buildconfig`,
`java-gradle-plugin`, and the functional-test publishing plugin (which layers
`com.autonomousapps.testkit`).

- `testAgainst(version)` creates a `<versionName>FunctionalTest` `Test` task; plain `functionalTest`
  is disabled and delegates to `defaultTestTarget`; `allVersionedFunctionalTests` feeds `check`.
- `compilerPlugin` (the compiler-plugin `Project`), `companionLibrary(...)` and
  `functionalTestProject(...)` drive a generated BuildConfig `PluginInfo` with `PLUGIN_ID`,
  `PLUGIN_ARTIFACT`, `EXTRA_LIBRARY_COORDINATES`, `PLUGIN_INFO`.
- Forwards `…devkit.gradle.test.kotlin-version` and the `kmpTarget` property to each test task.

### `compiler-plugin-runtime`

Runtime + test fixtures for the compiler plugin itself. `main` has the versioned-classloading entry
points. `testFixtures` has the test runners: `DevKitTest`, `DevKitJvmBoxTest`, `DevKitJsBoxTest`,
`DevKitJvmDiagnosticTest`, `DevKitJsDiagnosticTest`, `DevKitTestGenerator`, `configurePlugin(...)`,
`TestDataLibrary`.

`DevKitTestCompat` is a `ServiceLoader` SPI for per-version differences in the Kotlin test
framework; its impls live in the `k*/src/testFixtures` overlays with matching
`META-INF/services/...DevKitTestCompat` resources. This module dogfoods `DevKitCompilerPlugin` and
sets `addRuntimeDependency = false` (it can't depend on itself); `generateTests` is disabled.

### `gradle-plugin-runtime`

`DevKitSupportPlugin` — the `KotlinCompilerPluginSupportPlugin` base class a consumer's Gradle plugin
extends, fed by the generated `DevKitPluginInfo`. `testFixtures` has the TestKit functional-test
harness: `AbstractDevKitGradleProject`, `AbstractIncrementalCompilationTest`, `KmpTarget`,
`KotlinPlugins`, `SourceSupport`, `TestSupport`.

### `artifact-transform`

`KotlinToolingVersion` (vendored from Metro so it has **no** KGP dependency and works at compiler
runtime), `VersionResolution`, `CompilerVersionAliases`, and the generated
`BUILT_IN_COMPILER_VERSION_ALIASES` from `ide-mappings.txt`. Has the only conventional unit tests in
the repo.

## Commands

Gradle 9.6.1 via the wrapper; JDK 17+ (IDE is configured for `zulu-25`).

```bash
./gradlew build                      # or: check
./gradlew test                       # delegates to the default version target
./gradlew allVersionedTests          # every registered Kotlin version
./gradlew k2420_dev_7885Test         # one version; see `./gradlew tasks` for the list
./gradlew :artifact-transform:test   # plain unit tests
```

Publishing (mirrors the `.run/` configurations — each build must be published separately):

```bash
./gradlew :artifact-transform:publishToMavenLocal \
          :plugins:publishToMavenLocal \
          :plugins:settings:publishToMavenLocal \
          :compiler-plugin-runtime:publishMavenPublicationToMavenLocal \
          :gradle-plugin-runtime:publishToMavenLocal
```

Swap `publishToMavenLocal` for `publishAllPublicationsToDevKitSpaceRepository` to push to the
`devKitSpace` Space repo; that needs `devKitSpaceUsername` / `devKitSpacePassword` Gradle properties.

## Conventions

- **Formatting is ktfmt, `Kotlinlang` style**, configured for the IDE in `.idea/ktfmt.xml`: 4-space
  indent, 100-column limit, trailing commas. There is no Gradle format/lint task — match the
  surrounding code by hand.
- **`explicitApi()` is on in every module**, including the `testFixtures` compilations
  (`ExplicitApiMode.Strict`). New declarations need explicit visibility and explicit return types.
- **Comment only what the code can't say.** No comments restating what a line does, and no section
  headers over build-script blocks. A comment earns its place by explaining a workaround, a
  constraint, or a *why* that isn't recoverable from the code.
- **Gradle/Kotlin compatibility is set by `tapmoc`**, not by hand: every Gradle-side module calls
  `gradle(libs.versions.gradle.get())` along with `checkDependencies(Severity.ERROR)` and
  `checkKotlinStdlibs(Severity.ERROR)`, deriving `--release`, `languageVersion`/`apiVersion` and
  `coreLibrariesVersion` from the oldest supported Gradle (catalog `gradle`, currently 9.2.0 → Java 17,
  Kotlin 2.2). Don't re-add `jvmTarget` / `targetCompatibility`, and don't reach for stdlib or Gradle
  APIs newer than that. `checkDependencies` fails on a dependency whose class files or Kotlin metadata
  are too new; `checkKotlinStdlibs` is deliberately off in `plugins/`, where `buildconfig` wants a
  newer stdlib than Gradle 9.2 ships and that's harmless.
- `plugins/` compiles with `-Xcontext-parameters`, `-Xcontext-sensitive-resolution` and
  `-Xallow-contracts-on-more-functions`. `plugins/src/main/kotlin/.../utils.kt` defines
  `context(_: HasProject)` accessors — that is why plugin code writes bare `tasks`, `configurations`,
  `objects`, `layout` with no `project.` prefix. Keep using them.
- `plugins/` also applies `sam.with.receiver` and `assignment`, so Gradle `Action` lambdas get
  implicit receivers and `someProperty = value` works on `Property`.
- **Gradle APIs come from `compileOnlyApi(libs.gradle.public.api)`, never `gradleApi()`**, so the
  compile classpath is the oldest supported Gradle's rather than the building one's. The module
  publishes a single variant whose only capability is `gradle-public-api-internal`, so the
  `requireCapability` block is mandatory — it is not an opt-in to internals, though it is what makes
  `GradleInternal` / `TestFixturesSupport` importable in `plugins/` (which also needs
  `org.gradle.unsafe.suppress-gradle-api=true` to stop `java-gradle-plugin` re-adding `gradleApi()`).
  The artifact isn't on Maven Central; each build's repositories include
  `https://repo.gradle.org/gradle/libs-releases/`.
- **The Kotlin plugin is declared once per build**: versioned in the root build script (`apply false`
  in this repo's root, applied in `plugins/`) and unversioned as `kotlin("jvm")` in subprojects.
  Repeating the version in a subproject makes KGP load twice and warn.
- `gradle-plugin-runtime` drops the `kotlin-stdlib` that KGP adds to `testFixturesApi`: KGP pins it to
  its own version there, ignoring `coreLibrariesVersion`, which fails `checkDependencies`.
- **One version catalog for the whole repo**, `gradle/libs.versions.toml`. `plugins/` and
  `artifact-transform/` are separate builds, so the conventional lookup doesn't reach them — each opts
  in explicitly with
  `versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }` in its
  `dependencyResolutionManagement`. Don't re-add per-build catalogs.
- **Build cache and configuration cache are enabled** (root `gradle.properties`). Gradle code must be
  configuration-cache safe: lazy `Provider`s, no `Project` access at execution time, no eager
  configuration resolution.
- **`0.0.1-SNAPSHOT` is hardcoded in several places** and must stay in sync: root and
  `plugins/gradle.properties`, `artifact-transform/build.gradle.kts`, `DEV_KIT_VERSION` in
  `plugins/src/main/kotlin/.../constants.kt`, and `pluginDevKitVersion` in
  `plugins/settings/src/main/kotlin/org/gradle/kotlin/dsl/DevKitDependencyExtensions.kt`.

## Recipe: support a new Kotlin version

1. Add `testAgainst("X.Y.Z")` (or `testAgainstWithFixtures`) to the `pluginDevKit { }` block in
   `compiler-plugin-runtime/build.gradle.kts`.
2. If the Kotlin test-framework API changed, add
   `k<name>/src/testFixtures/kotlin/.../DevKitTestCompatImpl.kt` plus its
   `META-INF/services/org.jetbrains.kotlin.compiler.plugin.devkit.DevKitTestCompat` resource. If it
   didn't, point the new target's `testFixtures` `srcDirs` at an existing target's — the existing
   file shows both patterns.

Version *removal* works the same way in reverse; see commit `52b654a17` for a worked example.
