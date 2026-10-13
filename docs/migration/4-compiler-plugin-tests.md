## Compiler Plugin Tests

Any pre-existing non-`internal-test-framework` tests can stay as-is.

Always use `TestConfigurationBuilder`. Don't use `NonGroupingStageTestConfigurationBuilder` or any other synonyms for it.

Do **not** change your test data while migrating. The migration process does not require you to change your test data at all! You'll only complicate the process by modifying your test data during.

Note that the `internal-test-framework` generates and deletes files when running tests. Thus, test runs are eventually idempotent but not immediately; running tests once might produce errors, but might also genreate/delete dump files, so that running them again might produce no errors whatsoever. Always check the test results when you see failures instead of rerunning them.

For `internal-test-framework` base test classes, the DevKit provides a few with reasonable defaults.
DevKit test bases do all their configuration in configuration lambdas passed to the constructor.

The ready-to-use DevKit test bases correspond to `internal-test-framework` test bases as follows:

|`internal-test-framework` base|DevKitTest base|
|`AbstractJsTest`|`DevKitJsBoxTest`|
|`AbstractJsDiagnosticWithBackendTestBase`|`DevKitJsDiagnosticTest`|
|`AbstractFirBlackBoxCodegenTestBase`|`DevKitJvmBoxTest`|
|`AbstractFirPhasedDiagnosticTest`|`DevKitJvmDiagnosticTest`|

These test bases add some default directives. If they're in `defaultDirectives`, remove them. **If they're not**, then you need to disable them using unary minus `-`!
for `DevKit*DiagnosticTest`, `FIR_DUMP` and `DISABLE_GENERATED_FIR_TAGS`
for `DevKit*BoxTest`, `FIR_DUMP` and `DUMP_IR`
For `DevKitJvm*Test`, `FULL_JDK` and `IGNORE_DEXING`

**Carefully look at your pre-existing test's `defaultDirectives`**. Is it missing the directives expected for its type? If so, unset the directives **explicitly** using `defaultDirectives { -SOME_DIRECTIVE }`. 

For instance, if your `Abstract*BoxTest` *currently* does not have `DUMP_IR`, and now you're having it extend `DevKit*BoxTest`, then you **need** to add `defaultDirectives { -DUMP_IR }` in a configuration lambda to preserve behavior. Avoid using directives that disable the dump handler itself.

Please **double and triple check this**! If you see **new dump files** being generated, this means you previously didn't have one of these directives in your `defaultDirectives`, and thus you need to explicitly disable them using `defaultDirectives { -SOME_DIRECTIVE }`. Delete the new dump files afterwards, then run the tests.

Note that `DevKitJsBoxTest` requires no configuration of `pathToTestDir` or `testGroupOutputDirPrefix`.

All `DevKitTest`s set up `KotlinStandardLibrariesPathProvider`, so don't override `createKotlinStandardLibrariesPathProvider`.

[comment]: # (TODO #59)

If you're using `DevKitTest` directly, or one of the `Backported*` test bases, then please apply `TestConfigurationBuilder.devKitDefaults()` so you get versioned dump files and other goodies. 
Don't use it if you're using the ready-to-use DevKit test bases (`DevKit{Jvm|Js}{Box|Diagnostic}Test`). They apply it automatically, so it's redundant to apply it twice.

If you have an `ExtensionRegistrarConfigurator: EnvironmentConfigurator` that configures your plugin, check if it's using your plugin registrar directly (e.g. by calling `with(registrar) { registerExtensions() }`). If it is, **delete** it and instead use `configurePlugin(MyComponentRegistrar())`.
If it's doing more complicated configuration (e.g. passing arguments), you can leave it as-is.

If you have a `PluginAnnotationsProvider: EnvironmentConfigurator` and/or a `PluginAnnotationsClasspathProvider: RuntimeClasspathProvider`, first check if it adds the apt libraries indiscriminately. 
If it does, then use `configureDefaultTestDataLibraries()` (`configurePlugin` already calls this method, and it only needs to be called once). 
If it's conditional, then make sure you've `register`ed it in `build.gradle.kts` under `pluginDevKit.testDataLibraries`. You should have a generated `object TestDataLibraries` that has the aptly-named library. 
You can configure it as follows:
```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.configureTestDataLibrary

// Assuming you have a `TestConfigurationBuilder` as a receiver, e.g. in a constructor lambda to `DevKitTest`
configureTestDataLibrary(TestDataLibraries.someConditionalLibraryName) { module: TestModule -> module.directives.contains(SOME_DIRECTIVE) }
```

If your code follows the pre-devkit template closely, your `Abstract*Test` classes should now look like:
```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmBoxTest

open class AbstractJvmBoxTest : DevKitJvmBoxTest({ configurePlugin(MyComponentRegistrar()) })
```

For test generation, you **must** move the test generator `main` into `generateTests` source set.
If you don't move it, you'll get runtime binary incompatibility errors. You've been warned!
Do **not** have duplicate generate test files. Your only test generator **must** be in the `generateTests` source set.

Replace your usage of `generateTestGroupSuiteWithJUnit5` with `generateDevKitTestsWithJUnit5`, which automatically calls `testGroup`, so remove that call as well.
You **must** use `generateDevKitTestsWithJUnit5`. Do not bypass it to use the underlying `generateTestGroupSuiteWithJUnit5`.

Your setup might've passed arguments to the test generator. That's no longer necessary. The `generateTests` task passes no arguments to your `main`. The DevKit passes the necessary information using system properties. Thus, please delete any `args` wiring you have. `generateDevKitTestsWithJUnit5` does not need any such arguments.

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

The DevKit expects your test generator to be at `<pluginPackage>.GenerateTestsKt`. You can configure it using `pluginDevKit { generateTestsClass = "my.generator.ClassKt" }`.

You should now run your compiler plugin tests (e.g. using `check` or `:my-compiler-plugin:allTests`) and verify that everything passes.
If tests fail and generate new dump files, **delete the new dump files**, and disable the dump directive as explained above. Do **not** ignore the new dump files or add them to `.gitignore`.
Go on to [Initial Setup done][5-initial-setup-done.md]