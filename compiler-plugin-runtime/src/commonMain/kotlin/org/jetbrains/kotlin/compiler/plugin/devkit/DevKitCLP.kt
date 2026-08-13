package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.config.CompilerConfiguration

public interface DevKitCLP {
    public fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    )

    public val pluginOptions: Collection<AbstractCliOption>
}
