# Compiler Plugin

## `build.gradle.kts`

Apply the DevKit plugin:

```kotlin
plugins {
    pluginDevKit("compiler-plugin")
}
```

Remove the following:

- Applications of `build-config`, `node-gradle`, `idea`, D8, and `test-fixtures` plugins, since the DevKit applies them,
  or, in the case of `test-fixtures`, has its own implementation
- `kotlin("jvm")`, and use `kotlin("multiplatform")` instead because the DevKit sets up individual Kotlin versions as
  separate "platforms" (more on this in the multiversioning guide)
- Any `generateTests` task or similar should be removed, as well as its configuration, since test generation is handled
  automatically by the DevKit
- `Configuration`s that solely bring in `testArtifacts` should be removed (the pre-DevKit template used
  `setLibraryProperty` in this case).
- `Configuration`s that solely bring in companion libraries; you already declared them with `companionLibrary()`
  in [step 1](1-initial-setup.md).
- Test configuration of `internal-test-framework` properties, e.g. `idea.ignore.disabled.plugins`, `idea.home.path`,
  `javascript.engine.path.V8`, `javascript.engine.path.repl`, `kotlin.js.test.root.out.dir`
- `repl.js`, if you have it, since the DevKit bundles it on its own
- Dependencies on `kotlin("compiler")`, `kotlin("compiler-internal-test-framework")`, and `junit5`
- GradleUp's Shadow plugin, since the DevKit embeds dependencies in a special way to avoid dependency conflicts
    - Similarly, any custom `embedded` configuration should be removed, and instead use the default dependency
      configurations
- Opt-ins for `ExperimentalCompilerApi` and `UnsafeDuringIrConstructionAPI`
- Delete service files for `CommandLineProcessor` and `CompilerPluginRegistrar` (found in
  `resources/META-INF/services`). Instead, configure the same information in `pluginDevKit {}` under
  `commandLineProcessor` and `componentRegistrar` respectively.

If you have some companion libraries that should be **conditionally** applied, you can use the `testDataLibraries` DSL,
which passes the necessary classpath to test runtime, and generates build-config to wire it:

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

The pre-DevKit template comes with manual configuration of source set directories. Since the source set names have
changed, you need to e.g. use `commonMain` instead of `main`. The following code should set up the configuration as it
previously was:

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

Alternatively, you can move the relevant files into the default source set directories (`src/commonMain/kotlin`,
`src/commonTestFixtures/kotlin`, `src/commonTest/data`).

The Gradle configuration should look like the following, and any extra configuration should be clearly related to your
specific plugin and build setup:

```kotlin
plugins {
    pluginDevKit("compiler-plugin")
}

/* flat source set configuration from above, if it was in use already */

pluginDevKit {
    testDataLibraries { /* `register`ing conditional libraries */ }
    componentRegistrar =
        "path.to.MyComponentRegistrar"
    commandLineProcessor =
        "path.to.MyCommandLineProcessor"
}
```

## Command line processor

Extend the `DevKitCLP` interface and remove `override val pluginId: String`. `processOption` and `pluginOptions` should
work just as before.
It should look like:

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

## Plugin Registrar

Extend the `DevKitComponentRegistrar` interface and remove `override val pluginId: String` and
`override val supportsK2: Boolean`.
Because you're no longer extending `CompilerPluginRegistrar` directly, you'll need to use
`CompilerPluginRegistrar.ExtensionStorage` when overriding `registerExtensions`.

It should look like:

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

Go on to [Compiler Plugin Tests](4-compiler-plugin-tests.md)