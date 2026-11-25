# Gradle support plugin

The Gradle support plugin applies your compiler plugin to the build of a user. Apply the DevKit plugin for Gradle
plugins, and set the compiler plugin that it applies:

```kotlin title="build.gradle.kts"
plugins {
    pluginDevKit("gradle-plugin")
}

pluginDevKit {
    compilerPlugin(project(":my-compiler-plugin"))
}
```

The DevKit plugin applies `java-gradle-plugin`, and adds a `compileOnly` dependency on
`kotlin("gradle-plugin-api")`.

`compilerPlugin` takes one of these:

- `project(":my-compiler-plugin")`: a project of the same build.
- `"my-compiler-plugin"`: an artifact with the same group and version as the project. Use it for a compiler plugin in
  another build of a composite build.
- `"com.example:my-compiler-plugin:1.0.0"`: explicit coordinates.

The `kotlin.compiler.plugin.devkit.compilerPlugin` property takes the same values, with `:my-compiler-plugin` for a
project.

## Write the plugin

Extend `DevKitSupportPlugin` and give it the generated `PluginInfo.PLUGIN_INFO`. `PluginInfo` is in the
[`pluginPackage`](compiler-plugin.md#plugin-id). It has the plugin ID, the coordinates of the compiler plugin, and
the companion libraries.

```kotlin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class MySupportPlugin : DevKitSupportPlugin(PluginInfo.PLUGIN_INFO) {
    override fun apply(target: Project) {
        target.extensions.create("myPlugin", MyGradleExtension::class.java)
    }

    override fun Project.applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> = provider { emptyList() }
}
```

`DevKitSupportPlugin` does this for you:

- It applies the compiler plugin to every compilation.
- It adds each [companion library](compiler-plugin.md#companion-libraries) as an `implementation` dependency of the
  compilation. To add them on a condition, override `Project.shouldAddRuntimeDependency()`.

Override `Project.applyToCompilation` to return the options of the compiler plugin. The default is no options.

:::note
The users of your Gradle plugin must also add the DevKit EAP repository.
:::

A plugin that doesn't extend `DevKitSupportPlugin` can set `addRuntimeDependency = false` in `pluginDevKit { }`. The
published plugin then has no dependency on the DevKit library. `PluginInfo` still has `PLUGIN_ID`, `PLUGIN_ARTIFACT`,
and `COMPANION_LIBRARY_COORDINATES`.

## Functional tests

A functional test runs a Gradle build that applies your plugin, with
[Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html). Put the tests in the `functionalTest`
source set. The DevKit compiles them one time and runs them against each CLI version. IDE versions get no functional
tests.

| Task                          | Runs                                                                              |
|-------------------------------|-----------------------------------------------------------------------------------|
| `<target>FunctionalTest`      | The functional tests of one version, for example `k2420FunctionalTest`.           |
| `defaultFunctionalTest`       | The functional tests of the [default test version](versions.md#default-test-version). |
| `functionalTest`              | The same as `defaultFunctionalTest`.                                              |
| `allVersionedFunctionalTests` | The functional tests of every version. `check` runs it.                           |

The DevKit has test fixtures for these tests, in the package `org.jetbrains.kotlin.compiler.plugin.devkit.test`.

Declare a project class that applies your plugin:

```kotlin
import com.autonomousapps.kit.gradle.Plugin
import org.jetbrains.kotlin.compiler.plugin.devkit.test.AbstractDevKitGradleProject
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion

open class MyProject : AbstractDevKitGradleProject() {
    override val pluginUnderTest: Plugin
        get() = Plugin("com.example.myplugin", pluginUnderTestVersion)
}
```

Then write a test that builds a project with your sources:

```kotlin
import com.autonomousapps.kit.GradleBuilder.build
import org.jetbrains.kotlin.compiler.plugin.devkit.test.assertOutputContains

class MyProjectTest {
    @Test
    fun testCompiles() {
        val project = object : MyProject() {
            override fun sources() = listOf(source("fun main() {}"))
        }.gradleProject

        val result = build(project.rootDir, "build")
        result.assertOutputContains("BUILD SUCCESSFUL")
    }
}
```

More fixtures:

- `KotlinPlugins.jvm()` and `KotlinPlugins.multiplatform()`: the Kotlin plugin of the test build. The default version
  is the version of the test task.
- `assertOutputContainsOnDifferentKotlinVersions(map)`: checks the build output against the text for the version of
  the test task. Use it when the output differs between Kotlin versions.
`functionalTestProperties` in `pluginDevKit { }` adds lines to the `gradle.properties` of every test build.

### Multiplatform tests

`AbstractIncrementalCompilationTest` is a JUnit 4 base class for incremental compilation tests of a multiplatform
build. Each test class runs for one `KmpTarget`:

```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.test.AbstractDevKitGradleProject
import org.jetbrains.kotlin.compiler.plugin.devkit.test.AbstractIncrementalCompilationTest
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KmpTarget

open class MyMultiplatformProject : AbstractDevKitGradleProject(multiplatform = true) {
    override val pluginUnderTest: Plugin
        get() = Plugin("com.example.myplugin", pluginUnderTestVersion)
}

@RunWith(Parameterized::class)
class MyIncrementalTest(target: KmpTarget) : AbstractIncrementalCompilationTest(target) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun targets() = KmpTarget.selectedTargets()
    }

    @Test
    fun compiles() {
        val project = object : MyMultiplatformProject() {
            override fun sources() = listOf(source("fun foo() = 1"))
        }.gradleProject

        project.compileKotlin()
    }
}
```

`KmpTarget.selectedTargets()` reads the `kmpTarget` property:

```bash
./gradlew functionalTest -Pkotlin.compiler.plugin.devkit.gradle.test.kmpTarget=native_host
```

| Value                 | Targets                               |
|-----------------------|---------------------------------------|
| Not set               | `jvm`                                 |
| `jvm`, `js`, `wasmJs` | That target.                          |
| `native_host`         | The native target of the current OS.  |
| `all`                 | Every target above.                   |

The fixtures skip these tests:

- `AbstractIncrementalCompilationTest` skips the versions below `2.3.21`, because multiplatform incremental
  compilation doesn't work there. To run them, pass `requiresMultiplatformIc = false`.
- A multiplatform test build doesn't run for a version below the Kotlin version of the companion libraries, because
  KLIBs aren't forward compatible. [`companionLibrariesKotlinVersion`](testing.md#skipped-js-tests) sets that
  version.
