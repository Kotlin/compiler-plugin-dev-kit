# Gradle support plugin

If your compiler plugin has a Gradle support plugin, do this step. If it doesn't, go on to
[Compiler plugin](3-compiler-plugin.md).

The DevKit generates `PluginInfo`, which holds the data that `KotlinCompilerPluginSupportPlugin` needs:

- the plugin ID
- the `SubpluginArtifact`
- the companion libraries

In the `build.gradle.kts` of your Gradle plugin, apply the DevKit plugin for Gradle plugins:

```kotlin
plugins {
    pluginDevKit("gradle-plugin")
}
```

Remove the `build-config` and `java-gradle-plugin` plugins, because the DevKit applies them.
The DevKit also adds a `compileOnly` dependency on `kotlin("gradle-plugin-api")`. If you declare this dependency, remove
it.

## Use `DevKitSupportPlugin`

In `build.gradle.kts`, remove the `build-config` fields for the plugin ID, the coordinates, and the companion libraries.

Set `compilerPlugin` to your compiler plugin project:

[comment]: # (TODO #56)

```kotlin
import org.jetbrains.kotlin.compiler.plugin.devkit.ProjectOrGav

pluginDevKit {
    compilerPlugin = ProjectOrGav.LocalProject(project(":my-compiler-plugin").isolated)
}
```

If you don't set `compilerPlugin`, `PluginInfo` has `null` fields.

In this section, `Project` is `org.gradle.api.Project`.

[comment]: # (TODO #60)

To migrate your `KotlinCompilerPluginSupportPlugin`, do these steps:

1. Find your support plugin. Its fully qualified name is in `gradlePlugin.plugins.create(...).implementationClass`.
2. Replace `: KotlinCompilerPluginSupportPlugin` with `: DevKitSupportPlugin(PluginInfo.PLUGIN_INFO)`. `PluginInfo` is
   in the `pluginPackage` that you set in [Initial setup](1-initial-setup.md).
3. If you override `isApplicable` to return `true`, remove the override.
4. Remove `getCompilerPluginId` and `getPluginArtifact`.
5. If your Gradle extension has a `shouldAddRuntimeDependency` property, override `shouldAddRuntimeDependency` to read
   it:
    ```kotlin
    override fun Project.shouldAddRuntimeDependency(): Boolean =
        extensions.getByType(MyGradleExtension::class.java).shouldAddRuntimeDependency.get()
    ```
6. Replace `override fun applyToCompilation` with `override fun Project.applyToCompilation`.
7. If `applyToCompilation` adds dependencies that `companionLibrary` already adds, remove that code. If the code adds
   them conditionally, keep it. An example is a condition on a flag other than `shouldAddRuntimeDependency`.

Your plugin now looks similar to this:

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

The users of your Gradle plugin must also add the DevKit EAP repository.

Go on to [Compiler plugin](3-compiler-plugin.md)
