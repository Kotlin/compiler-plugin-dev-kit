# Compiler plugin

Apply the DevKit plugin for compiler plugins:

```kotlin title="build.gradle.kts"
plugins {
    kotlin("multiplatform")
    pluginDevKit("compiler-plugin")
}

pluginDevKit {
    componentRegistrar = "com.example.MyComponentRegistrar"
    commandLineProcessor = "com.example.MyCommandLineProcessor"
}
```

The plugin makes one target for each [registered version](versions.md). Put the plugin code in
`src/commonMain/kotlin`. The [Multiversioning guide](multiversioning/0-multiversioning.md) describes code that
differs between versions.

The plugin adds the opt-ins for `ExperimentalCompilerApi` and `UnsafeDuringIrConstructionAPI` to every compilation.

## Entry points

A compiler plugin has two entry points. Set the fully qualified name of each class in `pluginDevKit { }`. If one of
the names isn't set, the build fails.

| Property               | Interface                  | Replaces                  |
|------------------------|----------------------------|---------------------------|
| `componentRegistrar`   | `DevKitComponentRegistrar` | `CompilerPluginRegistrar` |
| `commandLineProcessor` | `DevKitCLP`                | `CommandLineProcessor`    |

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

```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCLP

class MyCommandLineProcessor : DevKitCLP {
    override val pluginOptions: Collection<CliOption>
        get() = emptyList()

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
    }
}
```

Rules for the entry points:

- Each class must have a public constructor with no parameters.
- The interfaces have no `pluginId` and no `supportsK2`. The DevKit sets the plugin ID, and sets `supportsK2` to
  `true`.
- Don't write the `META-INF/services` files. The DevKit generates them.

## Plugin ID

`pluginPackage` sets the plugin ID. It is also the package of the classes that the DevKit generates. The default is
the `group` of the project.

```properties title="gradle.properties"
kotlin.compiler.plugin.devkit.pluginPackage=com.example.myplugin
```

## Dependencies

Declare the dependencies of the plugin in the default configurations, for example `implementation`. The DevKit embeds
them in the plugin jar, so the published POM declares no dependencies. Don't use the Shadow plugin.

In the compiler, the plugin sees only the compiler and its own dependencies. It doesn't see the classes of other
plugins, so the dependencies of two plugins can't conflict.

## Companion libraries

A companion library is a library that your plugin needs on the classpath of the user's code, for example a library
with annotations. Register it with `companionLibrary(...)` or with the `companionLibraries` property:

```properties title="gradle.properties"
kotlin.compiler.plugin.devkit.companionLibraries=:plugin-annotations
```

Each entry is one of these:

- A project path, for example `:my-plugin-annotations`, for a project in the same build.
- A dependency notation, for example `com.example:some-library:1.0.0`.
- An artifact name, for example `my-plugin-annotations`, for a library with the same group and version as the
  project. Use it for a library in an included build.

The DevKit uses the companion libraries in two places:

- Every compiler plugin test has them on its classpath.
- The [Gradle support plugin](gradle-plugin.md) adds them as dependencies of the user's code.

## Libraries for compiler plugins

For a library that compiler plugins use, apply `pluginDevKit("compiler-library")` instead. The library compiles for
each version like a compiler plugin does. The differences:

- It has no entry points.
- Its published POM declares its dependencies.
- It generates no tests by default. To generate them, set `generateTestsEnabled = true`.
