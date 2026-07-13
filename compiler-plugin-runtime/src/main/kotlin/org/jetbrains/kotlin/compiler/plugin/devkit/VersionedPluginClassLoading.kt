package org.jetbrains.kotlin.compiler.plugin.devkit

import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.FileSystems.newFileSystem
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.Manifest
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.toPath
import org.jetbrains.kotlin.config.KotlinCompilerVersion

internal const val PLUGIN_VERSIONS_PATH = "META-INF/kotlin/plugin/versions"

object VersionedPluginClassLoading {
    private val cache = ConcurrentHashMap<ClassLoader, ClassLoader>()

    fun resolve(anchor: Class<*>): ClassLoader {
        val parent = anchor.classLoader
        val currentVersion = KotlinCompilerVersion.getVersion()?.let(::KotlinToolingVersion)
        val urls = if (parent is URLClassLoader) parent.urLs.toList() else runCatching {
            val method = parent.javaClass.getMethod("getUrls")
            @Suppress("UNCHECKED_CAST")
            method.invoke(parent) as? Iterable<URL>
        }.getOrNull()
        if (currentVersion == null || urls == null) return parent
        return cache.getOrPut(parent) { resolveUncached(parent, urls, currentVersion) }
    }

    private fun resolveUncached(
        parent: ClassLoader,
        urls: Iterable<URL>,
        currentVersion: KotlinToolingVersion,
    ): ClassLoader = buildList {
        urls.forEach { url ->
            url.toURI().toPath().openDirectory { root ->
                findVersion(root, currentVersion)?.let {
                    add(URI((root / PLUGIN_VERSIONS_PATH / it.toString()).toUri().toString() + "/").toURL())
                }
            }
            add(url)
        }
    }
        .let { URLClassLoader(it.toTypedArray(), parent.parent) }

    private inline fun Path.openDirectory(block: (Path) -> Unit) =
        when {
            extension == "jar" ->
                newFileSystem(this).use {
                    block(it.rootDirectories.single())
                }

            isDirectory() -> block(this)
            else -> {}
        }

    // Copied from MultiReleasePluginTransform
    private fun findVersion(inputRoot: Path, version: KotlinToolingVersion): KotlinToolingVersion? {
        val manifestPath = inputRoot / "META-INF" / "MANIFEST.MF"
        if (!manifestPath.exists()) return null

        val manifest = manifestPath.inputStream().use(::Manifest)
        if (manifest.mainAttributes.getValue("Kotlin-Compiler-Plugin-Multi-Release") != "true")
            return null

        val versionsDir = inputRoot / PLUGIN_VERSIONS_PATH
        if (!versionsDir.isDirectory()) return null

        return VersionResolution.resolveVersion(
            version,
            versionsDir.listDirectoryEntries().map { KotlinToolingVersion(it.name) },
        )
    }
}