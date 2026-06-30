package org.jetbrains.kotlin.compiler.plugin.devkit

import java.nio.file.FileSystems.newFileSystem
import java.util.jar.Manifest
import kotlin.io.path.*
import org.gradle.api.artifacts.transform.*
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input

private const val PLUGIN_VERSIONS_PATH = "META-INF/kotlin/plugin/versions"

@CacheableTransform
abstract class MultiReleasePluginTransform :
    TransformAction<MultiReleasePluginTransform.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        var kotlinVersion: String
    }

    @get:[Classpath InputArtifact]
    abstract val inputArtifact: Provider<FileSystemLocation>

    // TODO support more than just jars?
    @OptIn(ExperimentalPathApi::class)
    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile.toPath()
        if (!input.exists()) return

        newFileSystem(input).use { inputFs ->
            val manifestPath = inputFs.getPath("META-INF", "MANIFEST.MF")
            if (!manifestPath.exists()) return@use

            val manifest = manifestPath.inputStream().use(::Manifest)
            if (manifest.mainAttributes.getValue("Kotlin-Compiler-Plugin-Multi-Release") != "true")
                return@use

            val versionDir = inputFs.getPath(PLUGIN_VERSIONS_PATH, parameters.kotlinVersion)
            if (!versionDir.exists()) return@use

            val output =
                outputs
                    .file(input.name.removeSuffix(".jar") + "-${parameters.kotlinVersion}.jar")
                    .toPath()

            return newFileSystem(output, mapOf("create" to true)).use { outputFs ->
                val outputRoot = outputFs.rootDirectories.single()
                val outputVersionsDir = outputRoot.resolve(PLUGIN_VERSIONS_PATH)

                versionDir.copyToRecursively(outputRoot, followLinks = false)

                inputFs.rootDirectories.single().copyToRecursively(
                    outputRoot,
                    followLinks = false,
                ) { src, dst ->
                    if (src == outputVersionsDir) CopyActionResult.SKIP_SUBTREE
                    else src.copyToIgnoringExistingDirectory(dst, followLinks = false)
                }
            }
        }

        // Default: pass through original artifact
        outputs.file(inputArtifact)
    }
}
