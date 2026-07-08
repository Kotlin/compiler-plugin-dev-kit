# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Commands

```bash
./gradlew :compiler:test                   # Legacy compiler tests
./gradlew :compiler-tests:test             # New compiler tests (JetBrains infrastructure)
./gradlew :gradle-plugin:functionalTest    # Gradle integration tests
./gradlew -p samples check                 # Sample project tests
./gradlew :compiler-tests:generateTests    # Regenerate test classes after adding test data files
./gradlew dokkaGenerateHtml                # Generate API documentation
./metrow check                             # All validation, tests, linting, API validation (expensive)
cd benchmark && ./run_benchmarks.sh metro  # Performance benchmarks
```

Run with `--quiet` to reduce noise. Do not add `--info`, `--no-daemon`, or other extra flags. Always run `./gradlew` from the directory containing the wrapper; never `cd` into a module first.

Tests are slow, so don't run them gratuitously — but when the task at hand is a failing test or verifying a fix, running tests is expected, no need to ask first. Prefer the narrowest target that answers the question (a single `--tests` filter, a single `k<version>Test` suite) over a full suite; widen scope only once you need the bigger picture.

## Architecture

Metro is a compile-time dependency injection framework implemented as a Kotlin compiler plugin with full multiplatform support (JVM, Android, JS, Wasm, Native). It requires no KAPT/KSP.

### Composite Build Layout

`metro/` is a Gradle project that includes two external builds:
- `build-logic/` — Metro-specific Gradle conventions
- `../plugins` — The parent `compiler-plugin-dev-kit` project, which provides shared build infrastructure (`metro.base`, `metro.android`, `metro.publish` plugins) and the `compiler-plugin-runtime`/`gradle-plugin-runtime` artifacts used across all plugins in the dev kit

### Core Modules

**`compiler/`** — The Kotlin compiler plugin, distributed as a shadow JAR to avoid classpath conflicts.
- Two-phase: FIR (analysis/validation via `fir/`) → IR (code generation via `ir/`)
- `graph/` — Dependency graph construction, topological sorting, and cycle detection
- Entry point: `MetroCompilerPluginRegistrar.kt`

**`runtime/`** — Multiplatform annotation API (`@DependencyGraph`, `@Inject`, `@Provides`, `@Binds`, `@Scope`, `createGraph()`). `internal/` holds runtime support classes (factories, providers, double-check lazy).

**`gradle-plugin/`** — Wires the compiler plugin and runtime onto consumer projects; exposes `metro { }` DSL. `MetroGradleSubplugin.kt` is the main entry point.

**`compiler-compat/`** — Versioned compatibility layers for different Kotlin compiler releases. Each `k<version>/` subdirectory has a `version.txt` and is auto-included as `:compiler-compat:<kN>`.

**`compiler-tests/`** — Modern test infrastructure (JetBrains compiler testing framework). Tests are generated; source lives in `src/test/data/`:
- `box/` — Full compilation + execution
- `diagnostic/` — Error message validation
- `dump/` — FIR/IR tree inspection

Add a file there, then run `generateTests` to produce the JUnit test class.

**Interop modules** (`interop-dagger/`, `interop-javax/`, `interop-jakarta/`, `interop-guice/`) — Bridge types for migrating from existing DI frameworks.

### Key Architectural Rules

- FIR is for analysis and validation only; IR is for code generation. Do not mix concerns across phases.
- Kotlin compiler plugin APIs are unstable — Metro tracks Kotlin releases closely via `compiler-compat/`.
- Public modules have binary compatibility validation (`apiCheck`). Run it when changing public APIs.
- Use KotlinPoet for code generation; do not hand-write IR strings.
- Prefer fakes over mocks in tests.

## Working Style

- Edit only the exact file specified. Do not touch adjacent modules or "nearby" code unless asked. If unsure which file, ask first.
- Explain the plan before making edits on non-trivial changes; wait for approval.
- Do not make changes beyond what was asked. If something adjacent should also change, mention it but don't do it.
- Check official docs via WebFetch first, but it's fine to dig into `.gradle` caches or other library/dependency internals (e.g. decompiling or reading resolved sources) when that's what it takes to pin down exact behavior — this comes up often with the Kotlin compiler test framework, whose internals aren't well documented externally.
- Before removing or "cleaning up" existing system properties, config, or setup code because source-tracing suggests it's unused: (1) check `git log`/`git blame` on that line first — if it was deliberately added, treat that as evidence it's load-bearing even if you can't immediately find the consumer; (2) don't trust one traced code path as proof of "unused" — frameworks (especially the Kotlin compiler test framework) often read the same-ish property through multiple independent routes, including ones that bypass whatever abstraction you were reading (e.g. `EnvironmentBasedStandardLibrariesPathProvider`/`StandardLibrariesPathProviderForKotlinProject` are two separate `KotlinStandardLibrariesPathProvider` impls, but plenty of code — e.g. `JavaCompilerFacade` → `CodegenTestUtil.prepareJavacOptions` → `ForTestCompileRuntime.*` — reads the raw `TestCompilePaths` system properties directly, ignoring which impl is wired in); (3) verify by actually running the affected tests (a real `compiler-tests` test task, not just `--dry-run`/a narrow single test) before *and* after the change — a passing narrow test or a clean compile is not proof the property is dead.
- Avoid dense expression chains with trailing Elvis (`?: run { ... }`) fallbacks. Prefer named local values, early returns, or small helpers.
- Avoid large stacked boolean conditions. Split into named boolean values describing the state.
- Keep comments minimal. Don't transcribe the investigation or chat that led to a fix into a comment — if the fix is self-explanatory or easily justified from the code, it needs no comment, and if the underlying problem is general, state the general rule in one line rather than the specific saga that surfaced it. Reserve longer comments for genuinely non-obvious, load-bearing context that the code can't convey on its own.
- Reuse existing infrastructure. Grep before adding anything new.
