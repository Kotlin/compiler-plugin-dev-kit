// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.kotlin.compiler.plugin.devkit

public object CompilerVersionAliases {
    /**
     * Sentinel value in alias mappings indicating a version that only works in CLI (Gradle) builds,
     * not in IDE (IntelliJ/Android Studio) FIR integration. When [map] encounters this value, it
     * returns `null` to signal that the caller should skip enabling FIR extensions.
     */
    public const val CLI_ONLY: String = "CLI_ONLY"

    /**
     * Resolves a compiler version through alias mappings.
     *
     * If no alias matches, the original version is returned unchanged.
     *
     * Returns `null` if the version is mapped to [CLI_ONLY], indicating it should not be used for
     * IDE FIR integration.
     *
     * This is primarily used to map fake IDE compiler versions (e.g., Android Studio canary builds
     * reporting `2.3.255-dev-255`) to their real compiler versions.
     */
    @Suppress("UNCHECKED_CAST")
    public fun map(
        version: KotlinToolingVersion,
    ): KotlinToolingVersion? {
        val versionString = version.toString()
        // User aliases take priority
        // Then check built-in aliases
        val alias = runCatching {
            val appInfo = Class.forName("com.intellij.openapi.application.ApplicationInfo")
            val build = appInfo.getMethod("getInstance").invoke(null).let(appInfo.getMethod("getBuild")::invoke)
            val buildWithoutCode =
                build.let(build.javaClass.getMethod("withoutProductCode")::invoke) as Comparable<Comparable<Nothing>>
            val baselineVersion = build.let(build.javaClass.getMethod("getBaselineVersion")::invoke) as Int
            val builtInVersions =
                BUILT_IN_COMPILER_VERSION_ALIASES.filter { it.key.startsWith("$baselineVersion") }.mapKeys {
                    build.javaClass.getMethod("fromString", String::class.java)
                        .invoke(null, it.key) as Comparable<Comparable<Nothing>>
                }
            builtInVersions.findExactOrClosestVersion(buildWithoutCode)
        }.getOrNull()
        return when (alias) {
            null -> version // No alias found, return original
            CLI_ONLY -> null // Explicitly marked as CLI-only
            else -> KotlinToolingVersion(alias)
        }
    }
}

private fun <T : Comparable<T>, V> Map<T, V>.findExactOrClosestVersion(forVersion: T): V? =
    get(forVersion) ?: filter { it.key <= forVersion }.maxByOrNull { it.key }?.value