package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder

/** Keeps legacy BACKEND directives usable after Kotlin renamed the phase to CODEGEN. */
internal expect fun TestConfigurationBuilder.configureTestPhaseCompat()
