@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.JvmCodegenDirectivesCompat.IGNORE_DEXING
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives

internal fun RegisteredDirectivesBuilder.jvmDefaults() {
    +JvmEnvironmentConfigurationDirectives.FULL_JDK
    +IGNORE_DEXING
}
