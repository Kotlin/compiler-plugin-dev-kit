// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.test

import dev.zacsweers.metro.compiler.compat.KotlinToolingVersion

// Not a Boolean to avoid warnings about constants in if conditions
val OVERRIDE_COMPILER_VERSION: String by lazy {
  System.getProperty("metro.test.overrideCompilerVersion")
}

val JVM_TARGET: String by lazy { System.getProperty("metro.test.jvmTarget") }

val BUILD_COMPILER_VERSION: String by lazy { System.getProperty("metro.test.buildCompilerVersion") }

val TEST_COMPILER_VERSION: String by lazy { System.getProperty("metro.test.compilerVersion") }

val COMPILER_TOOLING_VERSION: String by lazy { TEST_COMPILER_VERSION }

val COMPILER_VERSION: KotlinVersion by lazy {
  val toolingVersion = KotlinToolingVersion(TEST_COMPILER_VERSION)
  KotlinVersion(toolingVersion.major, toolingVersion.minor, toolingVersion.patch)
}
