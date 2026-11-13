package org.jetbrains.kotlin.compiler.plugin.devkit

import javax.inject.Inject
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.dsl.GradleDependencies
import org.gradle.kotlin.dsl.newInstance

public interface DevKitTestDataLibrary : GradleDependencies {
    /** Dependency collector for all platforms */
    public val common: DependencyCollector

    /** Dependency collector for JVM */
    public val jvm: DependencyCollector

    /** Dependency collector for JS */
    public val js: DependencyCollector
}

public interface DevKitTestDataLibraries : DevKitTestDataLibrary {
    /**
     * Registers a library for test data.
     *
     * You should use [DevKitCompilerConfigExtension.testDataLibraries].[common], or one of its
     * platform-specific collectors, if you want to register a library for all test data.
     *
     * This provides a convenient way of registering a conditionally available library for test
     * data. You should use `configureTestDataLibrary` in your `testFixtures`, providing the
     * `condition` for the library to be available.
     *
     * The library will be passed to tests under the property "[name].[jvm|js].classpath". if
     * [addToBuildConfig], it'll also be available under TestDataLibraries.[name].
     *
     * The library resolves transitively by default.
     *
     * This is *not* idempotent.
     */
    public fun register(
        name: String,
        isTransitive: Boolean = true,
        addToBuildConfig: Boolean = true,
        block: DevKitTestDataLibrary.() -> Unit = {},
    ): DevKitTestDataLibrary
}

internal abstract class DevKitTestDataLibrariesImpl
@Inject
constructor(private val pluginDevKit: DevKitCompilerConfigExtension) : DevKitTestDataLibraries {
    override fun register(
        name: String,
        isTransitive: Boolean,
        addToBuildConfig: Boolean,
        block: DevKitTestDataLibrary.() -> Unit,
    ): DevKitTestDataLibrary =
        pluginDevKit.project.objects.newInstance<DevKitTestDataLibrary>().apply {
            pluginDevKit.register(this, name, isTransitive, addToBuildConfig)
            block()
        }
}
