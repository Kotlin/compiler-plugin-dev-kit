package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.File
import java.io.InputStream
import java.util.Properties
import java.util.zip.ZipFile
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

/**
 * The version of the compiler that built [artifact], read from its klib manifest, or null when
 * [artifact] is no klib at all: a JVM-only jar or class directory has no manifest to read.
 *
 * A klib, zipped or unpacked, keeps a Java-properties manifest at `default/manifest`; klibs before
 * 1.9.20 keep it at `manifest` instead. A common-metadata jar embeds one per source set,
 * `commonMain/default/manifest` and so on, so every such entry is read and the highest version
 * wins. `compiler_version` is the full version of the compiler that built the klib, `-dev-`
 * classifiers included, which is what the test-skipping logic needs; `abi_version` and
 * `metadata_version` only track the KLIB format and would be less precise.
 */
internal fun klibCompilerVersion(artifact: File): KotlinToolingVersion? =
    when {
        artifact.isDirectory -> runCatching {
                artifact
                    .walkTopDown()
                    .filter { it.isFile && it.isKlibManifest(artifact) }
                    .mapNotNull { manifest -> manifest.inputStream().use(::readCompilerVersion) }
                    .maxOrNull()
            }
                .getOrNull()

        artifact.isFile -> runCatching {
                ZipFile(artifact).use { klib ->
                    klib
                        .entries()
                        .asSequence()
                        .filter { it.name.isKlibManifestPath }
                        .mapNotNull { entry ->
                            klib.getInputStream(entry).use(::readCompilerVersion)
                        }
                        .maxOrNull()
                }
            }
                .getOrNull()

        else -> null
    }

// A root `manifest` is a pre-1.9.20 klib, a `<…>/default/manifest` a current one, and a
// common-metadata jar contributes its source-set fragments.
private val String.isKlibManifestPath: Boolean
    get() = this == "manifest" || this == "default/manifest" || endsWith("/default/manifest")

private fun File.isKlibManifest(klibRoot: File): Boolean =
    name == "manifest" && (parentFile == klibRoot || parentFile?.name == "default")

private fun readCompilerVersion(manifest: InputStream): KotlinToolingVersion? = runCatching {
    Properties()
        .apply { load(manifest) }
        .getProperty("compiler_version")
        ?.let { KotlinToolingVersion(it) }
}
    .getOrNull()
