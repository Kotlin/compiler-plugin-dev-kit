package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

public class DevKitCompilerPluginRegistrar(
    private val anchor: Class<*>,
    classLoaderId: String,
    registrarClass: String,
) : CompilerPluginRegistrar() {
    override val pluginId: String = classLoaderId

    private val versionedRegistrar by lazy {
        val loader = VersionedPluginClassLoading.resolve(anchor, classLoaderId)
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

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        versionedRegistrar(this, configuration)
    }

    override val supportsK2: Boolean
        get() = true
}
