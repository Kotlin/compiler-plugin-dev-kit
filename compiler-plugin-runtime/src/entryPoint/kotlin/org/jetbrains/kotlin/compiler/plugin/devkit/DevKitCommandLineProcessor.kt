package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

public class DevKitCommandLineProcessor(
    private val anchor: Class<*>,
    override val pluginId: String,
    processorClass: String,
) : CommandLineProcessor {
    private typealias ProcessOption = (AbstractCliOption, String, CompilerConfiguration) -> Any?

    private val versionedProcessor:
        Pair<ProcessOption, () -> Collection<AbstractCliOption>> by lazy {
        val loader = VersionedPluginClassLoading.resolve(anchor, pluginId)
        loader ?: return@lazy Pair({ _, _, _ -> }, { emptyList() })
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

    override val pluginOptions: Collection<AbstractCliOption>
        get() = versionedProcessor.second()

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        versionedProcessor.first(option, value, configuration)
    }
}
