package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

public abstract class DevKitCompilerPluginRegistrar(registrarClass: String) :
    CompilerPluginRegistrar() {
    private val versionedRegistrar by lazy {
        val loader =
            VersionedPluginClassLoading.resolve(
                this@DevKitCompilerPluginRegistrar.javaClass,
                pluginId,
            )
        val registrar = loader.loadClass(registrarClass).getDeclaredConstructor().newInstance()
        val method =
            registrar.javaClass.getMethod(
                "registerExtensions",
                ExtensionStorage::class.java,
                CompilerConfiguration::class.java,
            );
        { storage: ExtensionStorage, configuration: CompilerConfiguration ->
            method.invoke(registrar, storage, configuration)
        }
    }

    final override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        versionedRegistrar(this, configuration)
    }

    override val supportsK2: Boolean
        get() = true
}
