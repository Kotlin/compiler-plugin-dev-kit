package org.jetbrains.kotlin.compiler.plugin.devkit

import java.nio.file.FileSystems.newFileSystem
import java.util.jar.Manifest
import kotlin.io.path.*
import org.gradle.api.artifacts.transform.*
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

private const val PLUGIN_VERSIONS_PATH = "META-INF/kotlin/plugin/versions"

@CacheableTransform
public abstract class MultiReleasePluginTransform :
    TransformAction<MultiReleasePluginTransform.Parameters> {

    public interface Parameters : TransformParameters {
        @get:Input
        public val kotlinVersion: Property<String>
    }

    @get:[Classpath InputArtifact]
    protected abstract val inputArtifact: Provider<FileSystemLocation>

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

            val versionsDir = inputFs.getPath(PLUGIN_VERSIONS_PATH)
            if (!versionsDir.isDirectory()) return@use
            val version =
                VersionResolution.resolveVersion(
                    KotlinToolingVersion(parameters.kotlinVersion.get()),
                    versionsDir.listDirectoryEntries().map { KotlinToolingVersion(it.name) },
                ) ?: return@use
            val versionDir = versionsDir / version.versionString

            val output =
                outputs
                    .file(input.name.removeSuffix(".jar") + "-${version.versionString}.jar")
                    .toPath()

            return newFileSystem(output, mapOf("create" to true)).use { outputFs ->
                val outputRoot = outputFs.rootDirectories.single()
                val outputVersionsDir = outputRoot.resolve(PLUGIN_VERSIONS_PATH)
                val markerFile = outputRoot.resolve(".marker")

                versionDir.copyToRecursively(outputRoot, followLinks = false)

                inputFs.rootDirectories.single().copyToRecursively(
                    outputRoot,
                    followLinks = false,
                ) { src, dst ->
                    when {
                        dst == markerFile -> SKIP_SUBTREE
                        dst == outputVersionsDir -> SKIP_SUBTREE
                        dst.exists() -> CONTINUE
                        else -> src.copyToIgnoringExistingDirectory(dst, followLinks = false)
                    }
                }
            }
        }

        // Default: pass through original artifact
        outputs.file(inputArtifact)
    }
}
