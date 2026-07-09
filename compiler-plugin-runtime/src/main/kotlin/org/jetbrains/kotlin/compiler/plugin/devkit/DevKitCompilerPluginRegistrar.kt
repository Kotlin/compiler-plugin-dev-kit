package org.jetbrains.kotlin.compiler.plugin.devkit

import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

abstract class DevKitCompilerPluginRegistrar(registrarClass: KClass<out DevKitComponentRegistrar>) :
    CompilerPluginRegistrar() {
    private val versionedRegistrar by lazy {
        val loader = VersionedPluginClassLoading.resolve(registrarClass.java)
        val registrar =
            loader.loadClass(registrarClass.jvmName).getDeclaredConstructor().newInstance()
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
}

interface DevKitComponentRegistrar {
    fun CompilerPluginRegistrar.ExtensionStorage.registerExtensions(
        configuration: CompilerConfiguration
    )
}
