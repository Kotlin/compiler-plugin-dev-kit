package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

public abstract class DevKitCommandLineProcessor(processorClass: String) : CommandLineProcessor {
    private val versionedProcessor by lazy {
        val loader = VersionedPluginClassLoading.resolve(this@DevKitCommandLineProcessor.javaClass)
        val clp = loader.loadClass(processorClass).getDeclaredConstructor().newInstance()
        val processOption =
            clp.javaClass.getMethod(
                "processOption",
                AbstractCliOption::class.java,
                String::class.java,
                CompilerConfiguration::class.java,
            )
        val pluginOptions = clp.javaClass.getMethod("getPluginOptions")

        return@lazy { option: AbstractCliOption, value: String, configuration: CompilerConfiguration
            ->
            processOption.invoke(clp, option, value, configuration)
        } to
            {
                @Suppress("UNCHECKED_CAST")
                pluginOptions.invoke(clp) as Collection<AbstractCliOption>
            }
    }
    final override val pluginOptions: Collection<AbstractCliOption>
        get() = versionedProcessor.second()

    final override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        versionedProcessor.first(option, value, configuration)
    }
}
