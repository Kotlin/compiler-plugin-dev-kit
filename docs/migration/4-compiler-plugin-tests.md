# Compiler Plugin Tests

Any pre-existing non-`internal-test-framework` tests can stay as-is.

Always use `TestConfigurationBuilder`. Don't use `NonGroupingStageTestConfigurationBuilder` or any other synonyms for
it.

No test data changes are needed for this migration. By the end, your test data should remain unaffected.

For `internal-test-framework` base test classes, the DevKit provides a few with reasonable defaults.
DevKit test bases do all their configuration in configuration lambdas passed to the constructor.

The ready-to-use DevKit test bases correspond to `internal-test-framework` test bases as follows:

| `internal-test-framework` base            | DevKitTest base           |
|-------------------------------------------|---------------------------|
| `AbstractJsTest`                          | `DevKitJsBoxTest`         |
| `AbstractJsDiagnosticWithBackendTestBase` | `DevKitJsDiagnosticTest`  |
| `AbstractFirBlackBoxCodegenTestBase`      | `DevKitJvmBoxTest`        |
| `AbstractFirPhasedDiagnosticTest`         | `DevKitJvmDiagnosticTest` |

These test bases add some default directives. If they're in `defaultDirectives`, remove them:

- `DevKit*DiagnosticTest`: `FIR_DUMP` and `DISABLE_GENERATED_FIR_TAGS`
- `DevKit*BoxTest`: `FIR_DUMP` and `DUMP_IR`
- `DevKitJvm*Test`: `FULL_JDK` and `IGNORE_DEXING`

For instance, if your `Abstract*BoxTest` *currently* does not have `DUMP_IR`, and now you're having it extend
`DevKit*BoxTest`, then you **need** to add `defaultDirectives { -DUMP_IR }` in a configuration lambda to preserve
behavior. Avoid using directives that disable the dump handler itself.

If you see **new dump files** being generated, remove directives as stated above, then delete the new dump files, and
run the tests.

Note that `DevKitJsBoxTest` requires no configuration of `pathToTestDir` or `testGroupOutputDirPrefix`.

All `DevKitTest`s set up `KotlinStandardLibrariesPathProvider`, so don't override
`createKotlinStandardLibrariesPathProvider`.

[comment]: # (TODO #59)

If you're using `DevKitTest` directly, or one of the `Backported*` test bases, then please apply
`TestConfigurationBuilder.devKitDefaults()` so you get versioned dump files and other goodies.
Don't use it if you're using the ready-to-use DevKit test bases (`DevKit{Jvm|Js}{Box|Diagnostic}Test`), since they apply
it automatically.

If you have an `ExtensionRegistrarConfigurator: EnvironmentConfigurator` that configures your plugin, check if it's
using your plugin registrar directly (e.g. by calling `with(registrar) { registerExtensions() }`). If it is, **delete**
it and instead use `configurePlugin(MyComponentRegistrar())`.
If it's doing more complicated configuration (e.g. passing arguments), you can leave it as-is.

If you have a `PluginAnnotationsProvider: EnvironmentConfigurator` and/or a
`PluginAnnotationsClasspathProvider: RuntimeClasspathProvider`, first check if it adds the apt libraries
indiscriminately.
If it does, then use `configureDefaultTestDataLibraries()` (`configurePlugin` already calls this method, and it only
needs to be called once).
If it's conditional, then make sure you've `register`ed it in `build.gradle.kts` under `pluginDevKit.testDataLibraries`.
You should have a generated `object TestDataLibraries` that has the aptly-named library.
You can configure it as follows:

```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configureTestDataLibrary

// Assuming you have a `TestConfigurationBuilder` as a receiver, e.g. in a constructor lambda to `DevKitTest`
configureTestDataLibrary(TestDataLibraries.someConditionalLibraryName) { module: TestModule ->
    module.directives.contains(
        SOME_DIRECTIVE
    )
}
```

If your code follows the pre-DevKit template closely, your `Abstract*Test` classes should now look like:

```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmBoxTest

open class AbstractJvmBoxTest : DevKitJvmBoxTest({ configurePlugin(MyComponentRegistrar()) })
```

For test generation, you **must** move the test generator `main` into `generateTests` source set.
If you don't move it, you'll get runtime binary incompatibility errors. You've been warned!
Do **not** have duplicate generate test files. Your only test generator **must** be in the `generateTests` source set.

Replace your usage of `generateTestGroupSuiteWithJUnit5` with `generateDevKitTestsWithJUnit5`, which automatically calls
`testGroup`, so remove that call as well.
You **must** use `generateDevKitTestsWithJUnit5`. Do not bypass it to use the underlying
`generateTestGroupSuiteWithJUnit5`.

Your setup might've passed arguments to the test generator. That's no longer necessary. The `generateTests` task passes
no arguments to your `main`. The DevKit passes the necessary information using system properties. Thus, please delete
any `args` wiring you have. `generateDevKitTestsWithJUnit5` does not need any such arguments.

It should look like:

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

The DevKit expects your test generator to be at `<pluginPackage>.GenerateTestsKt`. You can configure it using
`pluginDevKit { generateTestsClass = "my.generator.ClassKt" }`.

You should now run your compiler plugin tests (e.g. using `check` or `:my-compiler-plugin:allTests`) and verify that
everything passes.
Go on to [Initial Setup done](5-initial-setup-done.md)