package org.jetbrains.kotlin.compiler.plugin.template

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.template.ir.SimpleIrGenerationExtension
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

class SimplePluginComponentRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String
        get() = BuildConfig.KOTLIN_PLUGIN_ID
    override val supportsK2: Boolean
        get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // CompatContext implementation depends on the version you're running!
        // Try uncommenting the following line, and compare `test` and `testK240_beta1`
        //require(CompatContext.context.isInBeta)
        FirExtensionRegistrarAdapter.registerExtension(SimplePluginRegistrar())
        IrGenerationExtension.registerExtension(SimpleIrGenerationExtension())
    }
}
