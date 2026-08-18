# AGENTS.md

## What this is

A dev kit for building **Kotlin compiler plugins that support many Kotlin compiler versions from one source tree**,
published as Gradle plugins plus runtime/test libraries under `org.jetbrains.kotlin.compiler.plugin.devkit:*`.

Compiler internals are unstable across versions, so a supported Kotlin version is modelled as a **KMP target**:
`commonMain` is shared by every version and *recompiled* against that version's compiler, with `expect`/`actual` only
where declarations genuinely differ. A breaking change therefore surfaces as a compile error, along with that version's
deprecations and opt-ins. The matching version is then picked out of one multi-release jar at Gradle build time (for
test fixtures and tests) and again at compiler/IDE runtime.

## The working tree moves between turns

The author edits this repo between prompts, including files you wrote yourself. Don't trust your memory of a file from
an earlier turn — re-read it before editing, and check `git status` / `git diff` when picking work back up. An edit that
fails to apply usually means the file moved on, not that you misread it; look at the new contents before deciding the
change is still wanted.

## Keeping this file current

Re-read this file at the **end** of a change, once the work is done and verified. If the change made anything here
stale, say so in your final response and propose the concrete edit. Don't rewrite this file mid-task.

Keep the bar for *adding* high. This file is for what a reader can't recover on their own: intent, cross-build wiring,
rules they'd unknowingly break. Leave out anything a quick look at the files already tells you.

## Build layout

Three separate Gradle builds, none a subproject of another: the root build, `plugins/` (`includeBuild` in root
`pluginManagement`) and `version-resolution/` (`includeBuild` in root `dependencyResolutionManagement`, because it is
consumed as a *dependency* rather than as a plugin). Edits in either included build are picked up with no publishing
step; consumers outside this repo need a publish.

## The central mechanism

1. **Target naming.** `k` + version with `.` removed and `-` → `_`, lowercased: `2.4.20-dev-7885` → `k2420_dev_7885`.
   Every target has `main`, `testFixtures` and `test` compilations.
2. **Sharing.** `DevKitHierarchyDsl`, mixed into the `pluginDevKit { }` extension, wraps KMP's hierarchy-template API so
   an intermediate source set's membership is a predicate over `KotlinToolingVersion`. Because a given breaking change
   usually lands once, the idiomatic shape is a `pre…`/`post…` split around one version, with `expect` in the shared
   parent and an `actual` on each side. `withSourceSetTree(main, testFixtures, test)` gives the *same* grouping in all
   three trees, so fixtures and tests are shared exactly like main code. `compiler-plugin-test/build.gradle.kts` is the
   worked example.
3. **Per-version dependencies.** `forceKotlinVersion` rewrites every `org.jetbrains.kotlin` dependency of a compilation
   to that target's version. For a `dev` version it forces the *range* `[<classifier minus build number>, <version>]`
   (`2.4.20-dev-7885` → `[2.4.20-dev, 2.4.20-dev-7885]`), because not every dev build publishes every artifact.
   `kotlin("compiler-tests")` is a fake coordinate the same hook rewrites to
   `kotlin-compiler-internal-test-framework`, or to `kotlin-compiler-tests-for-ide` on dev versions.
4. **Packaging.** Every `Jar` gets manifest attribute `Kotlin-Compiler-Plugin-Multi-Release: true`. The jar root holds
   only the entry point; everything else lives under `META-INF/kotlin/plugin/<pluginId>/` (`<pluginId>` being
   `pluginPackage`), in `versions/<version>/` per target and `dependencies/<jar-name>/` per runtime dependency, unzipped
   into its own directory. Burying it all there is what makes a dev-kit plugin immune to — and invisible to — the rest
   of the compiler-plugin classpath, with no shadowing involved.
5. **Selection at Gradle build time.** `MultiReleasePluginTransform`, an artifact transform keyed on the
   `org.jetbrains.kotlin.compiler.version` attribute (`KotlinCompilerVersion`), flattens the matching
   `versions/<version>/` plus the embedded `dependencies/` into the artifact root and drops `META-INF/kotlin/plugin`;
   non-matching artifacts pass through untouched. Only test fixtures and tests use it, and it is deliberately skipped
   for local project dependencies during IDE import, because rewriting the artifact loses the project identity IDEA
   needs.
6. **Selection at compiler runtime.** The entry point is *generated* per plugin: buildconfig emits
   `<pluginPackage>.PluginInfo`, whose `REGISTRAR_CLASS`/`PROCESSOR_CLASS` initializers declare local subclasses of
   `DevKitCompilerPluginRegistrar` / `DevKitCommandLineProcessor`, and `GenerateServices` writes `META-INF/services`
   files that name them by synthetic binary name (`PluginInfo$REGISTRAR_CLASS$1$Plugin`) — so that generated shape is
   load-bearing. At runtime those classes ask `VersionedPluginClassLoading` for a `URLClassLoader` over the resolved
   `versions/<version>/` and `dependencies/` dirs, parented to the plugin classloader's **parent**, and reflectively
   delegate into it. The plugin therefore sees a classpath of only itself and its dependencies while the compiler sees
   an ordinary plugin, needing no cooperation from the compiler or the IDE. A consumer only implements
   `DevKitComponentRegistrar` / `DevKitCLP` and names those classes via `componentRegistrar` / `commandLineProcessor`.

   Caveat: plugins built against different dev kit versions still share those ~6 entry-point classes on the real plugin
   classpath. Renaming the entry-point package is the escape hatch if it ever changes.
7. **Which version matches.** `VersionResolution.resolveVersion` picks the highest compatible version, special-casing
   the
   `dev` track so `2.3.20-dev-7791` doesn't match `2.3.20-Beta1`. `CompilerVersionAliases` maps an IDE build number
   through `ide-mappings.txt`, which `fetch-all-ide-kotlin-versions.py` regenerates.

## Module notes

### `pluginDevKit { }` — `compiler-plugin` and `compiler-library`

One extension either way; the flag decides whether the module gets the generated entry point and its runtime
dependencies embedded in the jar (a *plugin*, which must set `componentRegistrar`/`commandLineProcessor`) or publishes
those dependencies normally (a *library*, like `compiler-plugin-runtime` and `compiler-plugin-test`).

- **Resources of every source set whose name ends in `Test` are remapped to `src/<sourceSetName>/data`** — the test data
  root kotlin-compiler-internal-test-framework expects.
- The build's own Kotlin version is always registered, and is the **surrogate** target: the only publishable one, and
  the one whose jar aggregates every version, so a consumer resolves a single artifact and lets the dev kit pick from
  it. Every other target's elements configurations carry a `FakeKotlinCompilerVersion` attribute purely to lose against
  the surrogate during resolution.
- `generateTests` runs **once, against the highest registered version's** test fixtures, and every version's
  `compileJava`/`compileKotlin` depends on it. Per target it receives that target's dir under `generatedTestsDir` plus
  the data dirs of the test source set's whole `dependsOn` closure; `DevKitTestGenerator` derives a package suffix from
  the output dir name, which is how one shared test data tree yields one generated suite per version.
- The internal test framework's JS/Wasm half wants a pile of `-D` paths — hence the per-version
  `testArtifacts`/`testArtifactsWasm` resolution, the d8 executable and the generated `repl.js`.
- `testDataLibraries { }` entries reach test data as `<name>.jvm.classpath` / `<name>.js.classpath` system properties
  and, optionally, as a `TestDataLibraries` buildconfig object in `commonTestFixtures`.

### `pluginDevKit("gradle-plugin")` and `gradle-plugin-runtime`

For a plugin's *Gradle* plugin module, which stays a plain `kotlin("jvm")` project. Its single `functionalTest` source
set is compiled once and *run* per version, by a `<versionName>FunctionalTest` task that only varies the Kotlin version
it passes to the build under test. Its buildconfig-generated `PluginInfo` feeds
`DevKitSupportPlugin`, the `KotlinCompilerPluginSupportPlugin` base class the consumer extends;
`gradle-plugin-runtime`'s `testFixtures` holds the TestKit harness those functional tests run on.

### `plugins/settings/`

The settings plugin's id is the group itself, so a settings script applies it as `kotlin("compiler.plugin.devkit")`.
This project also carries the `pluginDevKit(...)` accessors in package `org.gradle.kotlin.dsl` — that is why build
scripts can write `pluginDevKit("compiler-library")` inside `plugins { }` and `pluginDevKit("version-resolution")` as a
dependency.

### `compiler-plugin-runtime`

`src/entryPoint/kotlin` is the one compilation deliberately *not* versioned, since it runs before a version is known.

### `compiler-plugin-test`

`DevKitTestCompat.kt` is the `expect` compatibility surface, with `actual`s per version group (`pre2420Dev7600Main`,
`post2420DevMain`, …).

### `version-resolution`

Must keep **no** KGP dependency, since it also runs inside the compiler — that is why `KotlinToolingVersion` is vendored
here rather than imported, and `VersionResolution` is shared verbatim by the artifact transform and the runtime
classloader. `CompilerVersionAliases` reads `ide-mappings.txt` off the classpath at runtime and **merges every copy it
finds**, to ensure that different versions of the devkit can co-exist.

## Commands

There is no `test` task. Each version gets `<target>Test`, registered with `KotlinTestsRegistry` so `allTests`
aggregates them and `check` picks that up.

```bash
./gradlew defaultTest                    # `…devkit.defaultTestVersion`, else the build's own Kotlin version
./gradlew defaultTest --tests 'Foo*'     # forwards the filter to every Test task in the project
./gradlew allTests                       # every registered Kotlin version
./gradlew k2420_dev_7885Test             # one version; see `./gradlew tasks` for the list
./gradlew k2420_dev_7885Test -PupdateTestData   # ...regenerating golden files
./gradlew spotlessApply                  # format the whole repo
```

## Conventions

- **Formatting is ktfmt, `Kotlinlang` style.** Spotless is declared in the **root** build script only and covers all
  three builds; the other two have no Spotless of their own. The IDE reads `.idea/ktfmt.xml`.
- **Comment only what the code can't say.** No comments restating what a line does, and no section headers over
  build-script blocks. A comment earns its place by explaining a workaround, a constraint, or a *why* that isn't
  recoverable from the code.
- **Gradle/Kotlin compatibility is set by `tapmoc`**, not by hand: every Gradle-side module calls
  `gradle(libs.versions.gradle.get())`, deriving `--release`, `languageVersion`/`apiVersion` and `coreLibrariesVersion`
  from the oldest supported Gradle (catalog `gradle`). Don't re-add `jvmTarget` / `targetCompatibility`.
- `plugins/src/main/kotlin/.../utils.kt` defines `context(_: HasProject)` accessors — that is why plugin code writes
  bare `tasks`, `configurations`, `objects`, `layout` with no `project.` prefix.
- **`DevKitTarget` is a real KMP target**, built on `ExternalKotlinTargetApi`. `DevKitTarget.kt` and
  `DevKitWireJavaSources.kt` are largely adapted from KGP's own JVM target (hence the JetBrains license headers) so the
  targets behave like first-class ones in Gradle *and* in IDE import; `IdeMultiVersionDependencyResolver` picks the
  highest version's compilation for each shared source set. Prefer fixing these in KGP's idiom over inventing a parallel
  one.
- **Isolated Projects should not be violated** unless absolutely necessary.
- **Gradle APIs come from `compileOnly(libs.gradle.public.api)`, never `gradleApi()`**, so the compile classpath is the
  oldest supported Gradle's rather than the building one's. This needs `org.gradle.unsafe.suppress-gradle-api=true` to
  stop `java-gradle-plugin` re-adding `gradleApi()`.
- **The Kotlin plugin is declared once per build**, versioned in that build's root script and unversioned in its
  subprojects.
- **One version catalog for the whole repo**, `gradle/libs.versions.toml`. `plugins/` and `version-resolution/` are
  separate builds, so the conventional lookup doesn't reach them — each opts in explicitly with
  `versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }` in its
  `dependencyResolutionManagement`. Don't re-add per-build catalogs.
- **Build cache and configuration cache are enabled** (root `gradle.properties`). Gradle code must be
  configuration-cache safe: lazy `Provider`s, no `Project` access at execution time, no eager configuration resolution.
  Laziness has a limit, though: a `map { }` that touches an extension member captures the whole extension, so a provider
  that ends up serialized (buildconfig fields do) must instead close over plain values.
- **The dev kit's own coordinates live only in `gradle/libs.versions.toml`** (`devkit-group`, `devkit-version`). Every
  publishing project sets `group`/`version` from there — don't put them back in a `gradle.properties`, which included
  builds can't share. The Kotlin-side `DEV_KIT_GROUP` / `DEV_KIT_VERSION` are generated by buildconfig from the
  project's own `group`/`version`, separately in `plugins` and `plugins:settings` (the latter must not depend on the
  former for Gradle classloading reasons). Both land in the same package, so each needs a distinct `className`.

## Recipe: support a new Kotlin version

1. Add the version to `org.jetbrains.kotlin.compiler.plugin.devkit.testAgainst` in the root `gradle.properties`.
2. Build it. Because the shared sources are recompiled per version, most versions need nothing else.
3. If something *does* break, decide where the split belongs in the hierarchy template (the
   `kotlin.applyPluginDevKitHierarchyTemplate { }` block inside `pluginDevKit { }` in
   `compiler-plugin-test/build.gradle.kts`), then move the differing declaration to an `expect` in the shared parent
   with an `actual` on each side. The `DevKitTestCompat.*.kt` files show the pattern.

Version *removal* works the same way in reverse.
