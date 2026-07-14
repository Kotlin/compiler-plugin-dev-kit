// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.gradle

import com.autonomousapps.kit.gradle.Plugin
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion

object GradlePlugins {
  val metro = Plugin("dev.zacsweers.metro", pluginUnderTestVersion)

  val agpKmp =
    Plugin("com.android.kotlin.multiplatform.library", System.getProperty("metro.agpVersion"))
}
