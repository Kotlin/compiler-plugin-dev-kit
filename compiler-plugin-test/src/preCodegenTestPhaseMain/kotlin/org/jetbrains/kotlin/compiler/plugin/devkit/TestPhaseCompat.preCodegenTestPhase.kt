// The facade class that a file's top-level declarations compile into is part of the published
// binary ABI. This file was renamed, so `@file:JvmName` keeps its pre-rename facade class.
@file:JvmName("TestPhaseCompat_pre250Dev8142Kt")

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder

internal actual fun TestConfigurationBuilder.configureTestPhaseCompat() {}
