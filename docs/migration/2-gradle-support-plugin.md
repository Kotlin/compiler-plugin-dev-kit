## Gradle Support Plugin

You likely have a Gradle support plugin for your compiler plugin.
The DevKit generates the info you need for implementing `KotlinCompilerPluginSupportPlugin` in a convenient `DevKitPluginInfo` class, which has your plugin ID, your `SubpluginArtifact`, and any "companion" libraries you wish to add.
It also simplifies functional/integration testing for your Gradle plugin.

In its `build.gradle.kts`:

Apply the DevKit plugin for Gradle plugins:
```kotlin
plugins {
    pluginDevKit("gradle-plugin")
}
```
You can remove application of `build-config` and `java-gradle-plugin`  plugins because the DevKit applies them itself.
It also adds a dependency on Kotlin Gradle Plugin (KGP), so remove that if it's explicitly set.

### Use DevKitSupportPlugin

in `build.gradle.kts`, remove any build-config fields you have for Plugin ID, coordinates, or annotation/support/companion library

Configure the following (`PluginInfo` won't be generated properly otherwise!!):

[comment]: # (TODO #56)
```
import org.jetbrains.kotlin.compiler.plugin.devkit.ProjectOrGav

pluginDevKit {
	compilerPlugin = ProjectOrGav.LocalProject(project(":my-compiler-plugin").isolated)
}
```

`PluginInfo` will have `null` fields if you don't configure `compilerPlugin`. Do **not** ignore that or use `!!`. Instead, just configure `compilerPlugin`

[comment]: # (TODO #60)

Locate your support plugin (It's fully-qualified name is in `gradlePlugin.plugins.create(...).implementationClass`), and replace `: KotlinCompilerPluginSupportPlugin` with `: DevKitSupportPlugin(PluginInfo.PLUGIN_INFO)`
The `PluginInfo` class is in `pluginPackage` which you might've set earlier, and it defaults to your project's `group`.

If you override `isApplicable` returning `true`, you can remove that too since that's the DevKit default
Remove `getCompilerPluginId` and `getPluginArtifact`
If you add `dependencies` that are now covered by `companionLibrary`, remove them too.

Note: The DevKit does not have its own `Project` type. References to `Project` in this section is to `org.gradle.api.Project`

If you have some `shouldAddRuntimeDependency` property on your Gradle extension (which is good practice), wire it:
```kotlin
override fun Project.shouldAddRuntimeDependency(): Boolean = extensions.getByType(MyGradleExtension::class.java).shouldAddRuntimeDependency.get()
```
Replace `override fun applyToCompilation` with `override fun Project.applyToCompilation`
Your `applyToCompilation` likely adds your companion libraries. The DevKit takes care of that. Thus, delete any code that unconditionally adds such dependencies (e.g. `dependencies { implementation(...) }`).
Only leave such code if it conditionally adds a dependency (e.g. based on some flag other than a general `shouldAddRuntimeDependency`).

Your plugin should now look something like:
```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSupportPlugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class MySupportPlugin : DevKitSupportPlugin(PluginInfo.PLUGIN_INFO) {
    override fun apply(target: Project) {
    	target.extensions.create(...)
    	...
    }

    override fun Project.shouldAddRuntimeDependency() =
        extensions.getByType(MyGradleExtension::class.java).shouldAddRuntimeDependency.get()

    override fun Project.applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        ...
        return provider { ... }
    }
}
```

Your Gradle plugin users will need to add the DevKit EAP repository as well.
Go on to [Compiler Plugin][3-compiler-plugin.md]