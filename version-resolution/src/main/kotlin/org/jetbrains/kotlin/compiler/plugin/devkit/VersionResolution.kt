// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.kotlin.compiler.plugin.devkit

import java.nio.file.FileSystems.newFileSystem
import java.nio.file.Path
import java.util.jar.Manifest
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import org.jetbrains.kotlin.compiler.plugin.devkit.CompilerVersionAliases.ideBranchPoint
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isDev

public object VersionResolution {
    public const val PLUGIN_PATH: String = "META-INF/kotlin/plugin"
    public const val VERSIONS_PATH: String = "versions"
    public const val DEPENDENCIES_PATH: String = "dependencies"

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
     * An IDE build like 2.4.0-ij261-64 takes the newest build of its own IDE branch that is not
     * ahead of it: `ij261` names the branch the IDE's compiler was cut on, so a version carrying it
     * describes that compiler exactly. Only when the plugin holds no build of that branch does it
     * fall back to the earliest same-base version, because an IntelliJ build number and a Kotlin
     * dev build number say nothing about each other.
     *
     * This ensures that a dev build like 2.3.20-dev-7791 doesn't incorrectly match 2.3.20-Beta1
     * just because beta > dev in maturity ordering.
     */
    internal fun resolveVersion(
        currentVersion: KotlinToolingVersion,
        versions: List<KotlinToolingVersion>,
    ): KotlinToolingVersion? {
        if (currentVersion.isIdeBuild) {
            findHighestSameIdeBranchVersion(currentVersion, versions)?.let {
                return it
            }
            CompilerVersionAliases.IDE_BUILD_NUMBER?.ideBranchPoint?.let {
                return resolveVersion(it, versions)
            }
            findLowestSameBaseVersion(currentVersion, versions)?.let {
                return it
            }
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

    private fun findHighestSameIdeBranchVersion(
        currentVersion: KotlinToolingVersion,
        versions: List<KotlinToolingVersion>,
    ) =
        versions
            .filter { it.idePlatform == currentVersion.idePlatform && currentVersion >= it }
            .maxOrNull()

    public fun MutableList<in Path>.addOverlayAndDependencies(
        id: String?,
        path: Path,
        currentVersion: KotlinToolingVersion,
    ): KotlinToolingVersion? {
        path.openDirectory { root ->
            // find ID as the unique directory here
            val id =
                id
                    ?: (root / PLUGIN_PATH)
                        .takeIf { it.isDirectory() }
                        ?.listDirectoryEntries()
                        ?.singleOrNull()
                        ?.name
                    ?: return@openDirectory
            return findVersion(root, id, currentVersion)?.also {
                val dependenciesDir = root / PLUGIN_PATH / id / DEPENDENCIES_PATH
                if (dependenciesDir.isDirectory()) {
                    dependenciesDir.forEachDirectoryEntry {
                        addOverlayAndDependencies(null, it, currentVersion)
                    }
                }

                add(root / PLUGIN_PATH / id / VERSIONS_PATH / it.toString())
                add(path)
            }
        }
        // we ignore entryPoint and other dependencies from irrelevant jars
        // importantly, id == null means this is a dependency, so
        // for dependencies, we'll add them to the classpath
        if (id == null) add(path)
        return null
    }

    private fun findVersion(
        inputRoot: Path,
        id: String,
        version: KotlinToolingVersion,
    ): KotlinToolingVersion? {
        val manifestPath = inputRoot / "META-INF" / "MANIFEST.MF"
        if (!manifestPath.exists()) return null

        val manifest = manifestPath.inputStream().use(::Manifest)
        if (manifest.mainAttributes.getValue("Kotlin-Compiler-Plugin-Multi-Release") != "true")
            return null

        val versionsDir = inputRoot / PLUGIN_PATH / id / VERSIONS_PATH
        if (!versionsDir.isDirectory()) return null

        return resolveVersion(
            version,
            versionsDir.listDirectoryEntries().map { KotlinToolingVersion(it.name) },
        )
    }

    public inline fun Path.openDirectory(block: (Path) -> Unit): Boolean =
        when {
            !exists() -> false
            extension == "jar" ->
                newFileSystem(this).use {
                    block(it.rootDirectories.single())
                    true
                }

            isDirectory() -> {
                block(this)
                true
            }
            else -> false
        }
}
