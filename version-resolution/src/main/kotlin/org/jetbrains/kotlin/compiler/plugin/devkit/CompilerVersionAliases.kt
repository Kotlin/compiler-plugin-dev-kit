// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
// Adapted from
// https://github.com/ZacSweers/metro/blob/02791af0eb95e6e8122f751ba3aa06b35f30cb6f/compiler-compat/src/main/kotlin/dev/zacsweers/metro/compiler/compat/CompilerVersionAliases.kt
// and
// https://github.com/Mr3zee/Kotlin-External-FIR-Support/blob/4cef960a1527d09ef3f8c95508a9ee0c6c304309/src/main/kotlin/com/github/mr3zee/kefs/KotlinVersionService.kt
package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isDev

public object CompilerVersionAliases {
    private val ANDROID_STUDIO_DEV_REGEX = Regex("""^(\d+\.\d+)\.255-dev-\d+$""")

    /**
     * The `dev` build this version's IDE branch was cut from, as closely as the recorded IDE builds
     * pin it down: what the newest IDE build of the same baseline shipped before that line moved
     * onto `-ij` builds. Null when no recorded build of the baseline predates the branch.
     */
    public val IdeBuildNumber.ideBranchPoint: KotlinToolingVersion?
        get() {
            return BUILT_IN_COMPILER_VERSION_ALIASES.entries
                .filter { (build, shipped) ->
                    build.baselineVersion == baselineVersion &&
                        build < this &&
                        shipped.isDev &&
                        !shipped.isIdeBuild
                }
                .maxByOrNull { it.key }
                ?.value
        }

    // Known tags-to-real version mappings for IDE builds.
    // Android Studio canary builds report a fake version like "2.3.255-dev-255".
    // The real version can be found by checking the IntelliJ tag for the studio build number:
    // https://github.com/JetBrains/intellij-community/blob/idea/<intellij-version>/.idea/libraries/kotlinc_kotlin_compiler_common.xml
    // We handle multiple `ide-mappings` on the classpath by merging them together
    public val BUILT_IN_COMPILER_VERSION_ALIASES: Map<IdeBuildNumber, KotlinToolingVersion> =
        javaClass.classLoader
            .getResources("META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/ide-mappings.txt")
            .asSequence()
            .map { text ->
                buildMap {
                    text.openStream().reader().forEachLine {
                        if (it.isNotBlank() && !it.startsWith("#")) {
                            val (from, to) = it.split('=', limit = 2)
                            put(IdeBuildNumber(from), KotlinToolingVersion(to))
                        }
                    }
                }
            }
            .reduce { a, b -> a + b }

    /**
     * Resolves a compiler version through alias mappings.
     *
     * If no alias matches, the original version is returned unchanged.
     *
     * This is primarily used to map fake IDE compiler versions (e.g., Android Studio canary builds
     * reporting `2.3.255-dev-255`) to their real compiler versions.
     */
    @Suppress("UNCHECKED_CAST")
    public fun map(version: KotlinToolingVersion): KotlinToolingVersion =
        if (ANDROID_STUDIO_DEV_REGEX.matches("$version"))
            IDE_BUILD_NUMBER?.let(::forIdeBuild) ?: version
        else version

    public val IDE_BUILD_NUMBER: IdeBuildNumber? by lazy {
        runCatching {
            val appInfo = Class.forName("com.intellij.openapi.application.ApplicationInfo")
            val build =
                appInfo
                    .getMethod("getInstance")
                    .invoke(null)
                    .let(appInfo.getMethod("getBuild")::invoke)
            build
                .let(build.javaClass.getMethod("withoutProductCode")::invoke)
                .toString()
                .let(::IdeBuildNumber)
        }
            .getOrNull()
    }

    /**
     * The Kotlin version an IDE build ships
     *
     * A build without an entry of its own takes the highest mapping below it *within its own
     * baseline*: build numbers of different baselines are unrelated, so 261's mappings say nothing
     * about 253.
     */
    public fun forIdeBuild(build: IdeBuildNumber): KotlinToolingVersion? =
        BUILT_IN_COMPILER_VERSION_ALIASES[build]
            ?: BUILT_IN_COMPILER_VERSION_ALIASES.filterKeys {
                    it.baselineVersion == build.baselineVersion && it < build
                }
                .maxByOrNull { it.key }
                ?.value
}

// https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/util/BuildNumber.java
public class IdeBuildNumber private constructor(private val components: IntArray) :
    Comparable<IdeBuildNumber> {
    public constructor(build: String) : this(build.split('.').map { it.toInt() }.toIntArray())

    public val baselineVersion: Int
        get() = components[0]

    override fun compareTo(other: IdeBuildNumber): Int {
        for (i in 0 until components.size.coerceAtMost(other.components.size)) {
            val result = components[i] - other.components[i]
            if (result != 0) return result
        }
        return components.size - other.components.size
    }

    override fun equals(other: Any?): Boolean =
        other is IdeBuildNumber && components.contentEquals(other.components)

    override fun hashCode(): Int = components.contentHashCode()

    override fun toString(): String = components.joinToString(".")
}
