package org.jetbrains.kotlin.compiler.plugin.devkit.services

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

public fun TestConfigurationBuilder.configurePlugin(registrar: DevKitComponentRegistrar) {
    useConfigurators(registrar::ExtensionRegistrarConfigurator)
}

private fun DevKitComponentRegistrar.ExtensionRegistrarConfigurator(testServices: TestServices) =
    object : EnvironmentConfigurator(testServices) {
        override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
            module: TestModule,
            configuration: CompilerConfiguration,
        ) = registerExtensions(configuration)
    }
