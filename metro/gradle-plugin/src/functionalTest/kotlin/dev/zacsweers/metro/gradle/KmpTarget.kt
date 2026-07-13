// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.gradle

import org.jetbrains.kotlin.compiler.plugin.devkit.test.detectHostNativeTarget

/**
 * Targets a parameterized [MetroProject] is exercised against. Each entry maps to a single Kotlin
 * multiplatform target declared by [MetroProject.applyMetroDefault] when `multiplatform = true`.
 */
enum class KmpTarget(val gradleTargetName: String) {
  JVM("jvm"),
  JS("js"),
  WASM_JS("wasmJs"),

  /**
   * Kotlin/Native host target. Resolves to `linuxX64` on Ubuntu CI, `macosArm64`/`macosX64` on
   * macOS, etc. — the host target is the only one Kotlin/Native can compile without pulling a
   * cross-compilation toolchain, which keeps the test matrix fast and portable.
   */
  NATIVE_HOST(detectHostNativeTarget());

  /** The Gradle compile task for this target's main compilation, e.g. `compileKotlinJvm`. */
  val compileTaskName: String =
    "compileKotlin" + gradleTargetName.replaceFirstChar { it.titlecase() }

  override fun toString(): String = gradleTargetName

  companion object {
    /**
     * The set of targets parameterized IC tests should iterate. Driven by the
     * `metro.functionalTestKmpTarget` system property (wired through
     * `gradle-plugin/build.gradle.kts` from a Gradle property of the same name):
     * - unset/empty → `[JVM]` (PR default — JVM coverage only, fast).
     * - `all` → every entry of [entries] (used by the multiplatform job on main).
     * - a specific `gradleTargetName` (e.g. `js`, `wasmJs`, `linuxX64`) → just that target (used by
     *   the main-only per-target parallel jobs).
     * - the literal `native_host` is also accepted as an alias for [NATIVE_HOST].
     */
    fun selectedTargets(): List<KmpTarget> {
      val raw = System.getProperty("metro.functionalTestKmpTarget").orEmpty()
      return when {
        raw.isEmpty() -> listOf(JVM)
        raw.equals("all", ignoreCase = true) -> entries.toList()
        raw.equals("native_host", ignoreCase = true) -> listOf(NATIVE_HOST)
        else ->
          listOf(
            entries.firstOrNull { it.gradleTargetName == raw }
              ?: error(
                "Unknown metro.functionalTestKmpTarget=$raw. Expected one of ${entries.map { it.gradleTargetName }} or `all` or `native_host`."
              )
          )
      }
    }
  }
}
