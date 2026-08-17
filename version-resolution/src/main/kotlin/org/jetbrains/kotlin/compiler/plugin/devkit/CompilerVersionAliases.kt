// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.kotlin.compiler.plugin.devkit

public object CompilerVersionAliases {
    // Known tags-to-real version mappings for IDE builds.
    // Android Studio canary builds report a fake version like "2.3.255-dev-255".
    // The real version can be found by checking the IntelliJ tag for the studio build number:
    // https://github.com/JetBrains/intellij-community/blob/idea/<intellij-version>/.idea/libraries/kotlinc_kotlin_compiler_common.xml
    // We handle multiple `ide-mappings` on the classpath by merging them together
    private val BUILT_IN_COMPILER_VERSION_ALIASES: Map<String, String> =
        javaClass.classLoader
            .getResources("META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/ide-mappings.txt")
            .asSequence()
            .map { text ->
                buildMap {
                    text.openStream().reader().forEachLine {
                        if (it.isNotBlank() && !it.startsWith("#")) {
                            val (from, to) = it.split('=', limit = 2)
                            put(from, to)
                        }
                    }
                }
            }
            .reduce { a, b -> a + b }
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
    public fun map(version: KotlinToolingVersion): KotlinToolingVersion? {
        val alias = runCatching {
            val appInfo = Class.forName("com.intellij.openapi.application.ApplicationInfo")
            val build =
                appInfo
                    .getMethod("getInstance")
                    .invoke(null)
                    .let(appInfo.getMethod("getBuild")::invoke)
            val buildWithoutCode =
                build.let(build.javaClass.getMethod("withoutProductCode")::invoke)
                    as Comparable<Comparable<Nothing>>
            val baselineVersion =
                build.let(build.javaClass.getMethod("getBaselineVersion")::invoke) as Int
            val builtInVersions =
                BUILT_IN_COMPILER_VERSION_ALIASES.filter { it.key.startsWith("$baselineVersion") }
                    .mapKeys {
                        build.javaClass
                            .getMethod("fromString", String::class.java)
                            .invoke(null, it.key) as Comparable<Comparable<Nothing>>
                    }
            builtInVersions.findExactOrClosestVersion(buildWithoutCode)
        }
            .getOrNull()
        return when (alias) {
            null -> version // No alias found, return original
            CLI_ONLY -> null // Explicitly marked as CLI-only
            else -> KotlinToolingVersion(alias)
        }
    }
}

private fun <T : Comparable<T>, V> Map<T, V>.findExactOrClosestVersion(forVersion: T): V? =
    get(forVersion) ?: filter { it.key <= forVersion }.maxByOrNull { it.key }?.value
