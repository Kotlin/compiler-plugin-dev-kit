package org.jetbrains.kotlin.compiler.plugin.devkit

import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.toPath
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.addOverlayAndDependencies
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

public object VersionedPluginClassLoading {
    private val cache = ConcurrentHashMap<String, URLClassLoader>()

    public fun resolve(anchor: Class<*>, id: String): URLClassLoader? {
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
        if (currentVersion == null || urls == null) return null
        return cache.getOrPut(id) { resolveUncached(parent, id, urls, currentVersion) }
    }

    private fun resolveUncached(
        parent: ClassLoader,
        id: String,
        urls: Iterable<URL>,
        currentVersion: KotlinToolingVersion,
    ): URLClassLoader {
        val paths = buildList {
            urls.forEach { addOverlayAndDependencies(id, it.toURI().toPath(), currentVersion) }
        }
        val urls = paths.map {
            val url = it.toURL()
            // Disable caching for our directory-in-jar urls so hot reloading works
            if (url.protocol == "jar") url.withHandler(DisableCachesStreamHandler) else url
        }
        return PluginClassLoader(id, urls.toTypedArray(), parent.parent)
    }

    private class PluginClassLoader(val id: String, urls: Array<URL>, parent: ClassLoader) :
        URLClassLoader(urls, parent) {
        override fun close() {
            super.close()
            cache.remove(id, this)
        }
    }

    private object DisableCachesStreamHandler : URLStreamHandler() {
        override fun openConnection(u: URL): URLConnection =
            u.withHandler(null).openConnection().apply { useCaches = false }
    }

    private fun Path.toURL(): URL = URI(toUri().toString() + "/").toURL()

    private fun URL.withHandler(handler: URLStreamHandler?) = URL.of(toURI(), handler)
}
