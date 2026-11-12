# AGENTS.md

`docs/spec.md` documents the design and the behavior of the dev kit. Read the part that covers what you are changing.
Product facts belong there. This file holds only rules for agents that work in this repository.

## The working tree moves between turns

The author edits this repo between prompts, including files you wrote yourself. Don't trust your memory of a file from
an earlier turn. Re-read it before you edit, and check `git status` / `git diff` when you pick work back up. An edit
that fails to apply usually means the file moved on, not that you misread it. Look at the new contents before you decide
the change is still wanted.

## Keeping this file current

Re-read this file at the **end** of a change, after you complete and check the work. If the change made anything here or
in the spec out of date, say so in your final response and propose the concrete edit. Don't rewrite this file mid-task.

Keep the bar for *additions* high. This file is for what a reader can't recover on their own: how the builds connect,
and rules they'd unknowingly break. Leave out anything a quick look at the files already tells you, and anything
`docs/spec.md` already says.

## Build layout

Three separate Gradle builds, none a subproject of another. Edits in either
included build take effect with no publishing step. Consumers outside this repo need a publish.

## Repo constraints

- `version-resolution` must have **no** KGP dependency, because it also runs inside the compiler.

## Commands

The DevKit registers no plain `test` task. Each version gets `<target>Test`, registered with `KotlinTestsRegistry` so
`allTests` aggregates them and `check` picks that up.

**In this repo those tasks run nothing.** No module of the root build has a test source set or any test data, so every
`<target>Test` reports `NO-SOURCE` and passes. A green `allTests` says only that every version still *compiles*, which
is the signal to read it for. Don't take one as evidence that behavior works. Only consumer projects have the test
suites that these tasks run. The executable tests are `version-resolution`'s and the `plugins` build's, which run as plain JVM `test`
tasks (`plugins` also has a `functionalTest` suite of composite-build probes):

```bash
./gradlew allTests                       # compiles every registered Kotlin version; runs no tests
./gradlew k2420_dev_7885Test             # one version; see `./gradlew tasks` for the list
./gradlew --project-dir version-resolution test
./gradlew --project-dir plugins functionalTest   # composite-build probes
./gradlew spotlessApply                  # format the whole repo
```

The rest of the per-version test machinery exists for consumers, and behaves as documented when a module has test data:

```bash
./gradlew defaultTest                    # `…devkit.defaultTestVersion`, else the build's own Kotlin version
./gradlew defaultTest --tests 'Foo*'     # forwards the filter to every Test task in the project
./gradlew k2420_dev_7885Test -PupdateTestData   # regenerates golden files
```

Each build publishes separately, so a release is three invocations. Run the root build first, because it compiles the
other two on the way: if it fails, no upload has happened yet.

```bash
for build in . plugins version-resolution; do
  DEVKIT_PUBLISH_VERSION=0.0.2-dev-$(git rev-list --count --first-parent HEAD) \
    ./gradlew --project-dir $build publishAllPublicationsToDevKitSpaceRepository
done
```

The same loop with `publishAllPublicationsToDevKitLocalRepository` writes all three builds to `build/repo` of the repo
root. The Gradle Plugin Portal gets only the plugins, from one more invocation:

```bash
./gradlew --project-dir plugins publishPlugins   # needs GRADLE_PUBLISH_KEY / GRADLE_PUBLISH_SECRET
```

`check` runs `checkArtifacts` in each build. The task compares the publications of each project with
`artifacts/<project>.txt` of that build. When a publication changes, regenerate the lists and commit them:

```bash
for build in . plugins version-resolution; do ./gradlew --project-dir $build dumpArtifacts; done
```

## Conventions

- **Formatting is ktfmt, `Kotlinlang` style.** Spotless is declared in the **root** build script only and covers all
  three builds. The other two have no Spotless of their own. The IDE reads `.idea/ktfmt.xml`.
- **Comment only what the code can't say.** No comments restating what a line does, and no section headers over
  build-script blocks. A comment must explain a workaround, a constraint, or a *why* that isn't recoverable from the
  code.
- **Gradle/Kotlin compatibility is set by `tapmoc`**, not by hand. Every Gradle-side module calls
  `gradle(libs.versions.gradle.get())`, deriving `--release`, `languageVersion`/`apiVersion` and `coreLibrariesVersion`
  from the oldest supported Gradle (catalog `gradle`). Don't re-add `jvmTarget` / `targetCompatibility`.
- `plugins/src/main/kotlin/.../utils.kt` defines `context(_: HasProject)` accessors. That is why plugin code writes bare
  `tasks`, `configurations`, `objects`, `layout` with no `project.` prefix.
- **`DevKitTarget` is a real KMP target**, built on `ExternalKotlinTargetApi`. `DevKitTarget.kt` and
  `DevKitWireJavaSources.kt` are largely adapted from KGP's own JVM target (the source of the JetBrains license
  headers). So the targets behave like KMP's own targets in Gradle *and* in IDE import.
  `IdeMultiVersionDependencyResolver` picks the highest version's compilation for each shared source set. Prefer fixing
  these in KGP's idiom over inventing a parallel one.
- **Don't violate Isolated Projects** unless it is absolutely necessary.
- **Gradle APIs come from `compileOnly(libs.gradle.public.api)`, never `gradleApi()`**, so the compile classpath is the
  oldest supported Gradle's, not the building one's. This needs `org.gradle.unsafe.suppress-gradle-api=true` to stop
  `java-gradle-plugin` re-adding `gradleApi()`.
- **Each build declares the Kotlin plugin version only in its root script.** Its subprojects apply the plugin with no
  version.
- **One version catalog for the whole repo**, `gradle/libs.versions.toml`. `plugins/` and `version-resolution/` are
  separate builds, so the conventional lookup doesn't reach them. Each opts in explicitly with
  `versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }` in its
  `dependencyResolutionManagement`. Don't re-add per-build catalogs.
- **Build cache and configuration cache are enabled** (root `gradle.properties`). Gradle code must be
  configuration-cache safe: lazy `Provider`s, no `Project` access at execution time, no eager configuration resolution.
  Laziness has a limit, though. A `map { }` that touches an extension member captures the whole extension, so a provider
  that ends up serialized (buildconfig fields do) must instead close over plain values.
- **The dev kit's own coordinates come only from `gradle/libs.versions.toml`** (`devkit-group`, `devkit-version`). Every
  publishing project sets `group`/`version` from there. Don't put them back in a `gradle.properties`, which included
  builds can't share. Buildconfig generates the Kotlin-side `DEV_KIT_GROUP` / `DEV_KIT_VERSION` from the project's own
  `group`/`version`, separately in `plugins` and `plugins:settings` (the latter must not depend on the former for Gradle
  classloading reasons). Both classes go into the same package, so each needs a distinct `className`.
- **A release overrides that version through the `DEVKIT_PUBLISH_VERSION` environment variable**, which the
  `versionCatalogs { }` block of all three builds' settings scripts reads. It is an environment variable, not a
  property, because it has to reach every build no matter which one you run. `.github/workflows/publish.yml` sets it to
  `<devkit-version without -SNAPSHOT>-dev-<first-parent commit count>` and publishes every commit on `main`. Each
  published commit also gets a prerelease GitHub Release tagged `v<version>`. The checked-in `-SNAPSHOT` value is what
  local and IDE builds keep using. In the root build the conventional path *already* imports the catalog, so its
  `create("libs")` must not call `from` again.
- **A build signs its publications only when the Gradle property `libs.sign.key.private` is set**, together with
  `libs.sign.key.id` and `libs.sign.passphrase`. Don't apply `signing` in a `plugins { }` block. With that plugin
  applied, `com.gradle.plugin-publish` signs in every build, and a build that has no key fails.

## Recipe: support a new Kotlin version

1. Usually nothing. This repo's `gradle.properties` registers by *range*: `minCliVersion` / `maxCliVersion` /
   `includeBetaAndRc`, `minIdeaVersion` / `maxIdeaVersion` / `includeIdeaRc` / `includeIdeaEap`, and `useLatestDev`, all
   `kotlin.compiler.plugin.devkit.*`. A version inside a range arrives as soon as `KnownVersions` lists it. Widen a
   bound, or name one version outright in `kotlin.compiler.plugin.devkit.testAgainst`.
2. Build it. Because the build recompiles the shared sources for each version, most versions need nothing else.
3. If something *does* break, decide where the split belongs in the hierarchy template: the `versionHierarchy { }` block
   inside `pluginDevKit { }` in `compiler-plugin-test/build.gradle.kts`. Then move the differing declaration to an
   `expect` in the shared parent with an `actual` on each side. The `DevKitTestCompat.*.kt` files show the pattern.

Version *removal* works the same way in reverse.

The publications of `compiler-plugin-runtime` and `compiler-plugin-test` have the name of the first registered target,
for example `compiler-plugin-runtime-k2220_beta1`. If the first registered version changes, run `dumpArtifacts`.
