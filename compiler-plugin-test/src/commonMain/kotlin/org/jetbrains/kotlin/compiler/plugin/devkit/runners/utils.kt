@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.firIdenticalCompat
import org.jetbrains.kotlin.compiler.plugin.devkit.services.DevKitTestConfigurator
import org.jetbrains.kotlin.compiler.plugin.devkit.versionDumpClassifier
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives

internal fun RegisteredDirectivesBuilder.jvmDefaults() {
    +JvmEnvironmentConfigurationDirectives.FULL_JDK
    +CodegenTestDirectives.IGNORE_DEXING
}

public fun TestConfigurationBuilder.devKitDefaults() {
    defaultDirectives {
        versionDumpClassifier()
        firIdenticalCompat()
    }
    useMetaTestConfigurators(::DevKitTestConfigurator)
}
