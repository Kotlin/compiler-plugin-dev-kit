package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

public abstract class DevKitCompilerPluginRegistrar(registrarClass: String) :
    CompilerPluginRegistrar() {
    // Doesn't exist before 2.3, so we re-declare it here to avoid `NoSuchMethodError`
    /**
     * Uniquely identifies the Kotlin compiler plugin. Must match the `pluginId` specified in
     * [CommandLineProcessor]. The ID can be used in combination with `-Xcompiler-plugin-order` to
     * control execution order of compiler plugins.
     */
    abstract override val pluginId: String
    private val versionedRegistrar by lazy {
        val loader =
            VersionedPluginClassLoading.resolve(
                this@DevKitCompilerPluginRegistrar.javaClass,
                pluginId,
            )
        loader ?: return@lazy { _, _ -> }
        val registrar = loader.loadClass(registrarClass).getDeclaredConstructor().newInstance()
        val method =
            registrar.javaClass.getMethod(
                "registerExtensions",
                ExtensionStorage::class.java,
                CompilerConfiguration::class.java,
            );
        { storage: ExtensionStorage, configuration: CompilerConfiguration ->
            method.invoke(registrar, storage, configuration)
            storage.registerDisposable {
                loader.close()
            }
        }
    }

    final override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        versionedRegistrar(this, configuration)
    }

    override val supportsK2: Boolean
        get() = true
}
