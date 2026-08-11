package org.jetbrains.kotlin.compiler.plugin.devkit

import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

public abstract class DevKitCommandLineProcessor(processorClass: KClass<out DevKitCLP>) :
    CommandLineProcessor {
    private val versionedProcessor by lazy {
        val loader = VersionedPluginClassLoading.resolve(processorClass.java)
        val clp = loader.loadClass(processorClass.jvmName).getDeclaredConstructor().newInstance()
        val method =
            clp.javaClass.getMethod(
                "processOption",
                AbstractCliOption::class.java,
                String::class.java,
                CompilerConfiguration::class.java,
            );
        { option: AbstractCliOption, value: String, configuration: CompilerConfiguration ->
            method.invoke(clp, option, value, configuration)
        }
    }

    final override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        versionedProcessor(option, value, configuration)
    }
}

public interface DevKitCLP {
    public fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    )
}
