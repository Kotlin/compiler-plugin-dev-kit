# Compiler plugin

## `build.gradle.kts`

Apply the DevKit plugin for compiler plugins:

```kotlin
plugins {
    pluginDevKit("compiler-plugin")
}
```

Remove these items:

- The `build-config`, `node-gradle`, `idea`, and D8 plugins. The DevKit applies them.
- The `test-fixtures` plugin. The DevKit has its own implementation.
- `kotlin("jvm")`. Apply `kotlin("multiplatform")` instead, because the DevKit sets up each Kotlin version as a
  separate target. The [multiversioning guide](../multiversioning/0-multiversioning.md) tells more about the targets.
- The `generateTests` task, or a similar task, and its configuration. The DevKit generates the tests.
- `Configuration`s that only bring in `testArtifacts`. The pre-DevKit template uses `setLibraryProperty` for them.
- `Configuration`s that only bring in companion libraries. You declared these libraries with `companionLibrary()` in
  [Initial setup](1-initial-setup.md).
- The `internal-test-framework` properties in the test configuration, for example `idea.ignore.disabled.plugins`,
  `idea.home.path`, `javascript.engine.path.V8`, `javascript.engine.path.repl`, and `kotlin.js.test.root.out.dir`.
- `repl.js`. The DevKit bundles it.
- The dependencies on `kotlin("compiler")`, `kotlin("compiler-internal-test-framework")`, and `junit5`.
- GradleUp's Shadow plugin. The DevKit embeds dependencies in a way that prevents dependency conflicts.
    - Custom `embedded` configurations. Use the default dependency configurations instead.
- The opt-ins for `ExperimentalCompilerApi` and `UnsafeDuringIrConstructionAPI`.
- The service files for `CommandLineProcessor` and `CompilerPluginRegistrar` in `resources/META-INF/services`. Set the
  same class names in `pluginDevKit {}`, as `commandLineProcessor` and `componentRegistrar`.

If only **some** tests use a library, register it with the `testDataLibraries` DSL. The DevKit passes the library
classpath to the test runtime. It also generates a `TestDataLibraries` object with a property that has the same name as
the library:

```kotlin
pluginDevKit {
    testDataLibraries {
        register("someConditionalLibrary") {
            common(project(":my-conditional-library"))
            jvm("some.group:some-external-library:1.0.0")
        }
    }
}
```

[comment]: # (TODO #50 so we can suggest adding `useSimplifiedSourceSetLayout`)

The pre-DevKit template sets the source set directories manually. The DevKit uses other source set names, for example
`commonMain` instead of `main`. This code keeps the directories of the pre-DevKit template:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            kotlin.setSrcDirs(listOf("src"))
        }
        commonTestFixtures {
            kotlin.setSrcDirs(listOf("test-fixtures"))
        }
        commonTest {
            kotlin.setSrcDirs(listOf("test"))
            resources.setSrcDirs(listOf("testData"))
        }
        generateTests {
            kotlin.setSrcDirs(listOf("generateTests"))
        }
    }
}
```

As an alternative, move the files into the default source set directories:

| Pre-DevKit template directory | DevKit directory                |
|-------------------------------|---------------------------------|
| `src`                         | `src/commonMain/kotlin`         |
| `test-fixtures`               | `src/commonTestFixtures/kotlin` |
| `test`                        | `src/commonTest/kotlin`         |
| `testData`                    | `src/commonTest/data`           |
| `generateTests`               | `src/generateTests/kotlin`      |

The result looks like this. Keep other configuration only if it is specific to your plugin or your build:

```kotlin
plugins {
    kotlin("multiplatform")
    pluginDevKit("compiler-plugin")
}

/* the source set configuration from above, if you keep the old directories */

pluginDevKit {
    testDataLibraries { /* `register`ing conditional libraries */ }
    componentRegistrar =
        "path.to.MyComponentRegistrar"
    commandLineProcessor =
        "path.to.MyCommandLineProcessor"
}
```

## Command line processor

Replace `CommandLineProcessor` with the `DevKitCLP` interface. Remove `override val pluginId: String`. `processOption`
and `pluginOptions` don't change.

Example:

```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCLP

class MyCommandLineProcessor : DevKitCLP {
    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        ...
    }

    override val pluginOptions: Collection<CliOption>
        get() = ...
}
```

## Plugin registrar

Replace `CompilerPluginRegistrar` with the `DevKitComponentRegistrar` interface. Remove `override val pluginId: String`
and `override val supportsK2: Boolean`.
Your class doesn't extend `CompilerPluginRegistrar`, so change the receiver of `registerExtensions` from
`ExtensionStorage` to `CompilerPluginRegistrar.ExtensionStorage`.

Example:

```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitComponentRegistrar

class MyComponentRegistrar : DevKitComponentRegistrar {
    override fun CompilerPluginRegistrar.ExtensionStorage.registerExtensions(
        configuration: CompilerConfiguration
    ) {
        FirExtensionRegistrarAdapter.registerExtension(MyPluginRegistrar())
        IrGenerationExtension.registerExtension(MyIrGenerationExtension())
    }
}
```

Go on to [Compiler plugin tests](4-compiler-plugin-tests.md)
