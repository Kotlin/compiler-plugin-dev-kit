package org.jetbrains.kotlin.compiler.plugin.devkit.services

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

internal fun TestConfigurationBuilder.configurePlugin(registrar: CompilerPluginRegistrar) {
    useConfigurators(registrar::ExtensionRegistrarConfigurator)
    configureAnnotations()
}

private fun CompilerPluginRegistrar.ExtensionRegistrarConfigurator(testServices: TestServices) =
    object : EnvironmentConfigurator(testServices) {
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration
    ) = registerExtensions(configuration)
}
