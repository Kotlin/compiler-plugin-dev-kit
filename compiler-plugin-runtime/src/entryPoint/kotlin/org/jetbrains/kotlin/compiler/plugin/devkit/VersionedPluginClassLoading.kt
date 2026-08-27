package org.jetbrains.kotlin.compiler.plugin.devkit

import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.toPath
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.addOverlayAndDependencies
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

public object VersionedPluginClassLoading {
    private val cache = ConcurrentHashMap<String, ClassLoader>()

    public fun resolve(anchor: Class<*>, id: String): ClassLoader {
        val parent = anchor.classLoader
        val currentVersion =
            KotlinCompilerVersion.getVersion()
                ?.let(::KotlinToolingVersion)
                ?.let(CompilerVersionAliases::map)
        val urls =
            if (parent is URLClassLoader) parent.urLs.toList()
            else
                runCatching {
                    val method = parent.javaClass.getMethod("getUrls")
                    @Suppress("UNCHECKED_CAST")
                    method.invoke(parent) as? Iterable<URL>
                }
                    .getOrNull()
        if (currentVersion == null || urls == null) return parent
        return cache.getOrPut(id) { resolveUncached(parent, id, urls, currentVersion) }
    }

    private fun resolveUncached(
        parent: ClassLoader,
        id: String,
        urls: Iterable<URL>,
        currentVersion: KotlinToolingVersion,
    ): ClassLoader {
        val paths = buildList {
            urls.forEach { addOverlayAndDependencies(id, it.toURI().toPath(), currentVersion) }
        }
        return URLClassLoader(
            paths.map { it.toURL() }.toTypedArray(),
            parent.parent,
        )
    }

    private fun Path.toURL(): URL = URI(toUri().toString() + "/").toURL()
}
