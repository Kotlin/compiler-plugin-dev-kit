// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.test

import org.jetbrains.kotlin.compiler.plugin.devkit.KotlinToolingVersion

val JVM_TARGET: String by lazy { System.getProperty("metro.test.jvmTarget") }

val BUILD_COMPILER_VERSION: KotlinToolingVersion by lazy {
  KotlinToolingVersion(System.getProperty("metro.test.buildCompilerVersion"))
}

val TEST_COMPILER_VERSION: KotlinToolingVersion by lazy {
  KotlinToolingVersion(System.getProperty("metro.test.compilerVersion"))
}

val COMPILER_VERSION: KotlinVersion by lazy {
  val toolingVersion = TEST_COMPILER_VERSION
  KotlinVersion(toolingVersion.major, toolingVersion.minor, toolingVersion.patch)
}
