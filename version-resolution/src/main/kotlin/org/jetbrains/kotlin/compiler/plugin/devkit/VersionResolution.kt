// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.kotlin.compiler.plugin.devkit

public object VersionResolution {
    /**
     * Pick the highest compatible version.
     *
     * `dev` track versions are special-cased to avoid issues with divergent release tracks.
     *
     * When the current version is a dev build:
     * 1. First, look for dev track versions with the same base version (the same trunk lineage) and
     *    compare by build number
     * 2. If none match, cross base versions: lower-base dev versions and non-dev versions compete,
     *    highest minVersion wins
     *
     * IDE versions like 2.4.0-ij261-64 use IntelliJ build numbers that are not comparable with
     * Kotlin dev build numbers, so unmapped IDE builds choose the earliest same-base version.
     *
     * This ensures that a dev build like 2.3.20-dev-7791 doesn't incorrectly match 2.3.20-Beta1
     * just because beta > dev in maturity ordering.
     */
    public fun resolveVersion(
        currentVersion: KotlinToolingVersion,
        versions: List<KotlinToolingVersion>,
    ): KotlinToolingVersion? {
        if (currentVersion.isIdeBuild)
            findLowestSameBaseVersion(currentVersion, versions)?.let {
                return it
            }

        // If current version is DEV, try same-base DEV track versions first. Only same-base dev
        // versions share the current version's trunk lineage; a dev version for an older base
        // version is just an older snapshot of trunk and shouldn't outrank a newer stable version.
        if (currentVersion.isDev) {
            val sameBaseDevVersions = versions.filter {
                it.isDev && it.hasSameBaseVersionAs(currentVersion)
            }

            findHighestCompatibleKotlinToolingVersion(currentVersion, sameBaseDevVersions)?.let {
                return it
            }

            // Crossing base versions: lower-base dev versions and non-dev versions compete,
            // highest minVersion wins (e.g. 2.4.0 stable outranks 2.4.0-dev-2124, and
            // 2.4.10-dev would outrank both).
            // Non-dev versions are compared against the base version (dev classifier stripped),
            // because 2.2.20-dev-5812 is a dev build OF 2.2.20 and should match 2.2.20,
            // but KotlinToolingVersion ordering puts DEV < STABLE so the comparison would
            // otherwise exclude it.
            val baseVersion =
                KotlinToolingVersion(
                    currentVersion.major,
                    currentVersion.minor,
                    currentVersion.patch,
                    null,
                )
            return versions
                .filter { if (it.isDev) currentVersion >= it else baseVersion >= it }
                .maxOrNull()
        }

        // For non-DEV versions, only consider non-DEV versions
        val nonDevVersions = versions.filter { !it.isDev }
        return findHighestCompatibleKotlinToolingVersion(currentVersion, nonDevVersions)
    }

    private fun findHighestCompatibleKotlinToolingVersion(
        currentVersion: KotlinToolingVersion,
        versions: List<KotlinToolingVersion>,
    ) = versions.filter { currentVersion >= it }.maxOrNull()

    private fun findLowestSameBaseVersion(
        currentVersion: KotlinToolingVersion,
        versions: List<KotlinToolingVersion>,
    ) = versions.filter { it.hasSameBaseVersionAs(currentVersion) }.minOrNull()
}
