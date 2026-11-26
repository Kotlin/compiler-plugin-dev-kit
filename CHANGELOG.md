# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **On ABI breaks in the `compiler-plugin-*` libraries.**
> A compiler plugin embeds the `compiler-plugin-*` artifacts of the DevKit version that built it, so ABI changes
> in those artifacts don't affect it. A published **compiler library** doesn't embed dependencies, so ABI changes do
> affect it.

## [Unreleased]

### Added

- The DevKit supports newly released Kotlin versions, including `2.5.0-RC2`, and `dev` builds up to `2.5.20-dev-6103`.

### Changed

- **Breaking**: `TagsGeneratorCheckerCompat` is removed from `2.2.20-RC..<2.3`. The class is unnecessary because that version range already supports `DISABLE_GENERATED_FIR_TAGS`. Indirectly using the class through `registerTagsGeneratorChecker` is unaffected.

### Fixed

- IDE sync improvements:
    - The DevKit now registers the IDE import dependency resolvers once for each project, not once for each target. The duplicate
      registrations slowed IDE sync.
    - IDE import's knowledge a local compiler library is faster and never stale. Previously, the information
      was read from the JAR of the compiler library, without declaring an explicit dependency on it. Now, the information is
      provided using an extra (local-only) artifact that IDE import explicitly depends on.
    - The POM of `kotlin-compiler-tests-for-ide` declares unpublished test framework modules (which it embeds anyway). These
      missing modules slowed down IDE dependency resolution, so the DevKit now excludes them (#121).
- The `DISABLE_GENERATED_FIR_TAGS` directive now covers Kotlin `2.2.20-RC..<2.3`, so tests can disable the tags
  generator on every supported version.

## [0.1.0] - 2026-11-20

### Added

- The DevKit is now available on the Gradle Plugin Portal and on Maven Central (#103).
- Backported compiler declarations:
    - `IrAnnotation` and its builder (pre-`2.3.20`)
    - `FirNamedFunction` and its builder (pre-`2.3.20`)
    - `KtDiagnosticWithSource` (pre-`2.2.20-Beta1`)
    - `getContainingClass*` utilities (pre-`2.3.0`)
    - contextual overloads of the `toSymbol` utilities (pre-`2.3.0`)
    - `mainFirFilesByTestFileCompat` for the `mainFirFiles` rename in `2.3.20`
- In IDE import, a local **compiler library** now resolves as the set of source-sets visible for the current version.
  This matches the declarations visible during compilation.
- The DevKit now supports newly released Kotlin and IDE versions: Kotlin `2.5.0-RC`, IntelliJ IDEA `2026.2.5` and
  `2026.3.1.1`, the newest Android Studio builds, and `dev` builds up to `2.5.20-dev-5013`.

### Changed

- Compatibility declarations that aren't `expect`/`actual` moved from the version-specific source sets of
  `compiler-plugin-runtime` and `compiler-plugin-test` into the new `compiler-plugin-runtime-compat` and
  `compiler-plugin-test-compat` modules (and declared as `api` dependencies). This is a refactoring change, so
  ABI-compatibility is preserved.
- Test data libraries (`testDataLibraries`) now resolve their dependencies transitively by default (#102). The DevKit
  excludes the Kotlin test artifacts, like `kotlin-test`, `kotlin-reflect`, and the stdlib variants, from every test
  data library.
- Additional files are now excluded in FIR dumps pre-`2.3.20`. This removes redundant information, and makes it more
  likely that the dumps match. You may need to update FIR dumps for that version range.

### Fixed

- A local project that depends on another local compiler plugin no longer resolves the other plugin's entry point into
  its own dependencies.
- The plugin manifest no longer sets `Kotlin-Compiler-Plugin-Since-Build` to the string `null` when `ideaSinceBuild`
  isn't configured.
- The functional-test setup of `gradle-plugin` now registers plugin dependencies declared in the same build and in the
  root build, not only in included builds (#105).

[Unreleased]: https://github.com/Kotlin/compiler-plugin-dev-kit/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Kotlin/compiler-plugin-dev-kit/compare/v0.0.4-dev-377...v0.1.0
