@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.directives.TestDumpDirectives

internal fun RegisteredDirectivesBuilder.jvmDefaults() {
    +JvmEnvironmentConfigurationDirectives.FULL_JDK
    +CodegenTestDirectives.IGNORE_DEXING
}

internal fun RegisteredDirectivesBuilder.versionDumpClassifier() {
    TestDumpDirectives.DUMP_CLASSIFIER with
        KotlinCompilerVersion.VERSION.replace(".", "").replace("-", "_").lowercase()
    +CodegenTestDirectives.IGNORE_DEXING
}
