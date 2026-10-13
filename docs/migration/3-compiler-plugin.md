## Compiler Plugin

The DevKit has a custom format for compiler plugin JARs. This extends to the devkit compiler plugin runtimes as well. This format is handled by a Gradle artifact transform locally.
Thus, if you look at the jars for`compiler-plugin-runtime`, `compiler-plugin-test`, or your own compiler plugin after applying the devkit, they'll look quite empty, and you'll be very confused.
Instead, use the sources jar directly, or find the transformed jar in Gradle caches.
Your IDE, Gradle, and compilation experience are **completely unaffected** by this custom format and transform. It's only relevant if you (or your AI) start snooping around in the jars.

The DevKit sets up different Kotlin versions in different source sets. That's completely normal and expected.
The DevKit works harmoniously with the rest of the compiler infrastructure. It's absolutely compatible with the Kotlin versions it'll pull in. Do **not** attempt to change the Kotlin versions the DevKit sets on configurations or dependencies for the compiler-plugin.

### `build.gradle.kts`
Apply the DevKit plugin:
```kotlin
plugins {
	pluginDevKit("compiler-plugin")
}
```
The DevKit plugin applies `build-config`, `node-gradle`, `idea`, and the D8 plugin, and it has its own implementation of `test-fixtures`, so you should remove them.
Test generation is also handled by the DevKit automatically, so any `generateTests` task should be removed, as well as its configuration. 

It also brings in the needed test artifacts, so any configuration that brings in kotlin classpaths and such should be removed (the pre-devkit template used `setLibraryProperty` in this case). If you've configured `companionLibrary()`, then those are passed in too. The test configuration is done for JVM and JS. 
Any test configuration that sets up properties intended for the test framework should likely be rmeoved since it's handled by the DevKit, e.g. `idea.ignore.disabled.plugins`, `idea.home.path`, `javascript.engine.path.V8`, `javascript.engine.path.repl`, `kotlin.js.test.root.out.dir`
Also, a local copy of `repl.js` for the test framework is no longer necessary, so it can be deleted.

If you have some companion libraries that should be **conditionally** applied, you can use the `testDataLibraries` DSL, which passes the necessary classpath to test runtime, and generates build-config to wire it:
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

The pre-devkit template comes with manual configuration of source set directories. Since the source set names have changed, you need to e.g. use `commonMain` instead of `main`. The following code should set up the configuration as it previously was:
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
	        kotlin.setSrcDirs(listOf("test", pluginDevKit.generatedTestsDir))
	        resources.setSrcDirs(listOf("testData"))
	    }
        generateTests {
        	kotlin.setSrcDirs(listOf("generateTests"))
        }
	}
}
```
Alternatively, you can move the relevant files into the default source set directories (`src/commonMain/kotlin`, `src/commonTestFixtures/kotlin`, `src/commonTest/data`)

The DevKit sets up dependencies on the compiler, the `internal-test-framework`, and `junit5`, so such dependencies can be removed too.
The DevKit takes care of embedding and shadowing dependencies, so GradleUp's Shadow plugin should be removed, and the default dependency configurations should be used instead of any custom `embedded` configuration.

Opt-ins for `ExperimentalCompilerApi` and `UnsafeDuringIrConstructionAPI` are added by default, so they can be safely removed.

The DevKit controls the plugin entry points, and deals with generating the relevant service files. You should copy and delete the `CommandLineProcessor` and `CompilerPluginRegistrar` service files under `resources/META-INF/services`, and configure them in `pluginDevKit` under `commandLineProcessor` and `componentRegistrar` respectively.

The Gradle configuration should look like the following, and any extra configuration should be clearly related to your specific plugin and build setup:
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

### Command line processor

Extend the `DevKitCLP` interface and remove `override val pluginId: String`. `processOption` and `pluginOptions` shoud work just as before
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

#### Plugin Registrar

Extend the `DevKitComponentRegistrar` interface and remove `override val pluginId: String` and `override val supportsK2: Boolean`.
Because you're no longer extending `CompilerPluginRegistrar` directly, you'll need to use `CompilerPluginRegistrar.ExtensionStorage` when overriding `registerExtensions`

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