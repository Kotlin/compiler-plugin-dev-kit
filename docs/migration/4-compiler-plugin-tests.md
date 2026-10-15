# Compiler plugin tests

Tests that don't use `internal-test-framework` don't change.

Always use `TestConfigurationBuilder`. Don't use `NonGroupingStageTestConfigurationBuilder` or any other alias of it.

This migration needs no changes to the test data. At the end, the test data must be the same as before.

## Test bases

The DevKit has its own test bases with defaults. A DevKit test base takes its configuration as a lambda in its
constructor.

Replace each `internal-test-framework` test base with its DevKit test base:

| `internal-test-framework` base            | DevKit test base          |
|-------------------------------------------|---------------------------|
| `AbstractJsTest`                          | `DevKitJsBoxTest`         |
| `AbstractJsDiagnosticWithBackendTestBase` | `DevKitJsDiagnosticTest`  |
| `AbstractFirBlackBoxCodegenTestBase`      | `DevKitJvmBoxTest`        |
| `AbstractFirPhasedDiagnosticTest`         | `DevKitJvmDiagnosticTest` |

These test bases add default directives. Remove these directives from your `defaultDirectives`:

- `DevKit*DiagnosticTest`: `FIR_DUMP` and `DISABLE_GENERATED_FIR_TAGS`
- `DevKit*BoxTest`: `FIR_DUMP` and `DUMP_IR`
- `DevKitJvm*Test`: `FULL_JDK` and `IGNORE_DEXING`

If your old test base doesn't have one of these directives, disable it in the configuration lambda. For example, if
your `Abstract*BoxTest` doesn't have `DUMP_IR`, you **must** add `defaultDirectives { -DUMP_IR }`. Don't use directives
that disable the dump handler itself.

If the tests write **new dump files**, disable the matching directive as shown above. Then delete the new dump files and
run the tests again.

`DevKitJsBoxTest` doesn't need `pathToTestDir` or `testGroupOutputDirPrefix`.

`DevKitTest` and its subclasses set up `KotlinStandardLibrariesPathProvider`, so don't override
`createKotlinStandardLibrariesPathProvider`.

[comment]: # (TODO #59)

If you use `DevKitTest` directly or a `Backported*` test base, call `TestConfigurationBuilder.devKitDefaults()`. It
sets up versioned dump files and other defaults. The ready-to-use test bases (`DevKit{Jvm|Js}{Box|Diagnostic}Test`)
already call it, so don't call it with them.

## Plugin configuration

If you have an `ExtensionRegistrarConfigurator: EnvironmentConfigurator` that configures your plugin:

- If it only calls your plugin registrar, for example with `with(registrar) { registerExtensions() }`, **delete** it.
  Use `configurePlugin(MyComponentRegistrar())` instead.
- If it does more, for example passes arguments, you can keep it.

If you have a `PluginAnnotationsProvider: EnvironmentConfigurator` or a
`PluginAnnotationsClasspathProvider: RuntimeClasspathProvider`, check which tests get the libraries:

- If every test gets them, delete the provider. `configurePlugin` adds the companion libraries to every test. If you
  don't call `configurePlugin`, call `configureDefaultTestDataLibraries()`.
- If only some tests get a library, use the library that you registered with `testDataLibraries` in
  [Compiler plugin](3-compiler-plugin.md). Give its `TestDataLibraries` property to `configureTestDataLibrary`, as in
  the example below.

```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configureTestDataLibrary

// The receiver is a `TestConfigurationBuilder`, for example in the constructor lambda of `DevKitTest`
configureTestDataLibrary(TestDataLibraries.someConditionalLibrary) { module: TestModule ->
    module.directives.contains(
        SOME_DIRECTIVE
    )
}
```

If your code follows the pre-DevKit template, your `Abstract*Test` classes now look like this:

```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmBoxTest

open class AbstractJvmBoxTest : DevKitJvmBoxTest({ configurePlugin(MyComponentRegistrar()) })
```

## Test generator

1. Move the test generator `main` into the `generateTests` source set. If you don't, the tests fail at runtime with
   binary incompatibility errors.
2. Delete all other test generators.
3. Replace `generateTestGroupSuiteWithJUnit5` with `generateDevKitTestsWithJUnit5`.
4. Remove the `testGroup` call. `generateDevKitTestsWithJUnit5` calls it.
5. Delete the code that passes arguments to the test generator. The DevKit passes no arguments to `main`. It gives the
   generator its inputs as system properties.

The test generator now looks like this:

```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.generateDevKitTestsWithJUnit5

fun main() = generateDevKitTestsWithJUnit5 {
    testClass<AbstractJvmDiagnosticTest> {
        model("diagnostics")
    }

    testClass<AbstractJvmBoxTest> {
        model("box")
    }

    testClass<AbstractJsDiagnosticTest> {
        model("diagnostics")
    }

    testClass<AbstractJsBoxTest> {
        model("box")
    }
}
```

By default, the DevKit runs the test generator class `<pluginPackage>.GenerateTestsKt`. To use another class, set
`pluginDevKit { generateTestsClass = "my.generator.ClassKt" }`.

Run the compiler plugin tests, for example with `check` or `:my-compiler-plugin:allTests`. Make sure that all tests
pass.

Go on to [Initial setup done](5-initial-setup-done.md)
