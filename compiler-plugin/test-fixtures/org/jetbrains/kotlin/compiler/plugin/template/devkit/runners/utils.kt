@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package org.jetbrains.kotlin.compiler.plugin.template.devkit.runners

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilderBase
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.services.AbstractEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.DefaultsDsl
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider

@DefaultsDsl
context(builder: TestConfigurationBuilderBase<*, *>)
fun defaultDirectives(init: RegisteredDirectivesBuilder.() -> Unit) = builder.defaultDirectives(init)

@DefaultsDsl
context(builder: TestConfigurationBuilderBase<*, *>)
fun useConfigurators(vararg environmentConfigurators: Constructor<AbstractEnvironmentConfigurator>) =
    builder.useConfigurators(environmentConfigurators = environmentConfigurators)

@DefaultsDsl
context(builder: TestConfigurationBuilderBase<*, *>)
fun useCustomRuntimeClasspathProviders(vararg provider: Constructor<RuntimeClasspathProvider>) =
    builder.useCustomRuntimeClasspathProviders(provider = provider)

internal fun RegisteredDirectivesBuilder.jvmDefaults() {
    +JvmEnvironmentConfigurationDirectives.FULL_JDK
    +CodegenTestDirectives.IGNORE_DEXING
}