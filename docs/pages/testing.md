# Compiler plugin tests

The DevKit sets up the
[Kotlin compiler test framework](https://github.com/JetBrains/kotlin/blob/master/compiler/test-infrastructure/ReadMe.md)
for every [registered version](versions.md). You write the test base classes, the test generator, and the test data
one time. The DevKit generates the tests and runs them against each version.

## Layout

| Directory                       | Content                                                           |
|---------------------------------|-------------------------------------------------------------------|
| `src/commonTestFixtures/kotlin` | The test base classes and their configuration.                    |
| `src/jsTestFixtures/kotlin`     | The JS test base classes.                                         |
| `src/commonTest/data`           | The test data, for example `box` and `diagnostics` directories.   |
| `src/generateTests/kotlin`      | The test generator.                                               |
| `build/test-gen`                | The generated tests. Set `generatedTestsDir` to change it.        |

The DevKit adds the test framework, JUnit 5, and its own test libraries. Don't add them yourself.

## Test base classes

Extend a DevKit runner and give it your configuration. `configurePlugin` registers the extensions of your plugin:

```kotlin title="src/commonTestFixtures/kotlin/AbstractJvmBoxTest.kt"
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmBoxTest
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin

open class AbstractJvmBoxTest : DevKitJvmBoxTest({
    configurePlugin(MyComponentRegistrar())
})
```

Each runner adds default directives. All runners use the LightTree parser by default.

| Runner                    | Default directives                                                    |
|---------------------------|-----------------------------------------------------------------------|
| `DevKitJvmBoxTest`        | `DUMP_IR`, `FIR_DUMP`, `FULL_JDK`, `IGNORE_DEXING`                    |
| `DevKitJvmDiagnosticTest` | `FIR_DUMP`, `DISABLE_GENERATED_FIR_TAGS`, `FULL_JDK`, `IGNORE_DEXING` |
| `DevKitJsBoxTest`         | `DUMP_IR`, `FIR_DUMP`                                                 |
| `DevKitJsDiagnosticTest`  | `FIR_DUMP`, `DISABLE_GENERATED_FIR_TAGS`                              |

For a test without these defaults, extend a `Backported*` base class. The
[Migration guide](migration/4-compiler-plugin-tests.md#test-bases) maps each base class of the test framework to its
DevKit base class.

### Skipped JS tests

JS tests run only for CLI versions from `2.3.0` on. IDE versions get no JS tests.

The DevKit also skips the JS tests of a version below the Kotlin version of the companion libraries, because KLIBs
aren't forward compatible. The DevKit reads that version from the klibs. To set it yourself:

```kotlin title="build.gradle.kts"
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

pluginDevKit {
    companionLibrariesKotlinVersion = KotlinToolingVersion("2.3.0")
}
```

## Test generator

The generator is a `main` function in the `generateTests` source set:

```kotlin title="src/generateTests/kotlin/GenerateTests.kt"
import org.jetbrains.kotlin.compiler.plugin.devkit.generateDevKitTestsWithJUnit5

fun main() = generateDevKitTestsWithJUnit5 {
    testClass<AbstractJvmDiagnosticTest> {
        model("diagnostics")
    }

    testClass<AbstractJvmBoxTest> {
        model("box")
    }
}
```

By default, the DevKit runs the class `<pluginPackage>.GenerateTestsKt`. To use another class, set
`generateTestsClass` in `pluginDevKit { }`.

### JS tests in the generator

The generator compiles one time for each kind of version, because only some versions have the JS test framework.
Each compilation has `generateTests` and its own source sets:

| Source set              | Versions                                                         |
|-------------------------|------------------------------------------------------------------|
| `generateTestsJs`       | The CLI versions from `2.3.0` on.                                |
| `generateTestsCliNonJs` | The CLI versions before `2.3.0`.                                 |
| `generateTestsIde`      | The IDE versions.                                                |
| `generateTestsCli`      | Every CLI version. The first two compilations share this source set. |

Register the JS tests behind an `expect` function:

```kotlin title="src/generateTests/kotlin/GenerateTests.kt"
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitTestGroup
import org.jetbrains.kotlin.compiler.plugin.devkit.generateDevKitTestsWithJUnit5

fun main() = generateDevKitTestsWithJUnit5 {
    testClass<AbstractJvmBoxTest> {
        model("box")
    }
    addJsTests()
}

expect fun DevKitTestGroup.addJsTests()
```

```kotlin title="src/generateTestsJs/kotlin/GenerateTests.js.kt"
actual fun DevKitTestGroup.addJsTests() {
    testClass<AbstractJsBoxTest> {
        model("box")
    }
}
```

`generateTestsCliNonJs` and `generateTestsIde` each need an empty `actual`.

## Libraries in test data

Every test has the [companion libraries](compiler-plugin.md#companion-libraries) on its classpath.

If only some tests use a library, register it with `testDataLibraries`:

```kotlin title="build.gradle.kts"
pluginDevKit {
    testDataLibraries {
        register("someConditionalLibrary") {
            common(project(":my-conditional-library"))
            jvm("some.group:some-external-library:1.0.0")
        }
    }
}
```

The DevKit generates a `TestDataLibraries` object with a property for the library. Give the property to
`configureTestDataLibrary`, with a condition that selects the test modules:

```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configureTestDataLibrary

configureTestDataLibrary(TestDataLibraries.someConditionalLibrary) { module: TestModule ->
    SOME_DIRECTIVE in module.directives
}
```

## Run the tests

| Task            | Runs                                                                               |
|-----------------|------------------------------------------------------------------------------------|
| `<target>Test`  | The tests of one version, for example `k2420_dev_7885Test`.                        |
| `defaultTest`   | The tests of the [default test version](versions.md#default-test-version).         |
| `allTests`      | The tests of every version.                                                        |
| `check`         | `allTests` and the other checks of the project.                                    |

```bash
./gradlew defaultTest --tests 'Foo*'
```

The [Kotlin Compiler DevKit](https://github.com/JetBrains/kotlin-compiler-devkit) IDE plugin runs the tests of a
test data file from the editor.

## Dump files of different versions

A dump can differ between compiler versions. The DevKit then uses a dump file with the version in its name. For the
test data file `foo.kt` and the dump extension `.fir.txt`:

- `foo.fir.txt` is the dump of the highest registered version.
- `foo.2420_dev_7885.fir.txt` is the dump of `2.4.20-dev-7885`.

A version uses its own dump file if the file exists. Otherwise it uses the dump file of the nearest higher version
that has one. So a version needs its own file only when its dump differs from the dump of that higher version.

A new highest version takes over `foo.fir.txt`. The previous highest version then needs its own file, if its dump
differs.

To write the dump files that don't match, run the tests with `-PupdateTestData`:

```bash
./gradlew k2420_dev_7885Test -PupdateTestData
```

A CI job that registers only one version must
[set `classifiedDumpVersions`](ci.md#one-job-for-each-version).

### Dumps as diffs

With `DUMP_AS_DIFF`, the dump of a version is a unified diff against `foo.fir.txt`. The file is
`foo.2420_dev_7885.fir.patch`.

```kotlin title="src/commonTestFixtures/kotlin/AbstractJvmBoxTest.kt"
import org.jetbrains.kotlin.compiler.plugin.devkit.TestDumpDirectives.DUMP_AS_DIFF

open class AbstractJvmBoxTest : DevKitJvmBoxTest({
    configurePlugin(MyComponentRegistrar())
    defaultDirectives { +DUMP_AS_DIFF }
})
```

### Your own classifiers

A dump can also differ for a reason other than the version. Declare a classifier, and add it in the test
configuration:

```kotlin title="src/commonTestFixtures/kotlin/AbstractJvmBoxTest.kt"
import org.jetbrains.kotlin.compiler.plugin.devkit.SingleTestDumpClassifier
import org.jetbrains.kotlin.compiler.plugin.devkit.TestDumpDirectives.DUMP_CLASSIFIER

object Windows : SingleTestDumpClassifier<Windows>("windows")

open class AbstractJvmBoxTest : DevKitJvmBoxTest({
    configurePlugin(MyComponentRegistrar())
    if (System.getProperty("os.name").startsWith("Windows")) {
        defaultDirectives { DUMP_CLASSIFIER with Windows.classifier }
    }
})
```

The test uses `foo.windows.fir.txt` if the file exists. Otherwise it uses the dump without `windows`. For classifiers
that fall back to each other, extend `TestDumpRoot`.
