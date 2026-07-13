package org.jetbrains.kotlin.compiler.plugin.devkit

import java.nio.file.FileSystems.newFileSystem
import java.nio.file.Path
import java.util.jar.Manifest
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input

private const val PLUGIN_VERSIONS_PATH = "META-INF/kotlin/plugin/versions"

@CacheableTransform
public abstract class MultiReleasePluginTransform :
    TransformAction<MultiReleasePluginTransform.Parameters> {

    public interface Parameters : TransformParameters {
        @get:Input public val kotlinVersion: Property<String>
    }

    @get:[Classpath InputArtifact]
    protected abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile.toPath()
        run default@{
            when {
                !input.exists() -> return
                input.extension == "jar" ->
                    newFileSystem(input).use { inputFs ->
                        val inputRoot = inputFs.rootDirectories.single()
                        val version = findVersion(inputRoot) ?: return@default
                        val versionDir = inputRoot / PLUGIN_VERSIONS_PATH / version.toString()

                        val output =
                            outputs
                                .file(
                                    input.name.removeSuffix(".jar") +
                                        "-${version.toString()}.jar"
                                )
                                .toPath()

                        return newFileSystem(output, mapOf("create" to true)).use { outputFs ->
                            val outputRoot = outputFs.rootDirectories.single()
                            copyToOutput(inputRoot, versionDir, outputRoot)
                        }
                    }

                // TODO this code path is unexercised and is likely pointless.
                input.isDirectory() -> {
                    val version = findVersion(input) ?: return@default
                    val versionDir = input / PLUGIN_VERSIONS_PATH / version.toString()
                    val output = outputs.dir(input.name + "-$version").toPath()
                    return copyToOutput(input, versionDir, output)
                }
            }
        }
        // Default: pass through original artifact
        if (input.isDirectory()) outputs.dir(inputArtifact) else outputs.file(inputArtifact)
    }

    private fun findVersion(inputRoot: Path): KotlinToolingVersion? {
        val manifestPath = inputRoot / "META-INF" / "MANIFEST.MF"
        if (!manifestPath.exists()) return null

        val manifest = manifestPath.inputStream().use(::Manifest)
        if (manifest.mainAttributes.getValue("Kotlin-Compiler-Plugin-Multi-Release") != "true")
            return null

        val versionsDir = inputRoot / PLUGIN_VERSIONS_PATH
        if (!versionsDir.isDirectory()) return null
        val realVersion =
            CompilerVersionAliases.map(KotlinToolingVersion(parameters.kotlinVersion.get()))
                ?: return null

        return VersionResolution.resolveVersion(
            realVersion,
            versionsDir.listDirectoryEntries().map { KotlinToolingVersion(it.name) },
        )
    }

    @OptIn(ExperimentalPathApi::class)
    private fun copyToOutput(inputRoot: Path, versionDir: Path, outputRoot: Path) {
        val outputVersionsDir = outputRoot / PLUGIN_VERSIONS_PATH
        val markerFile = outputRoot / ".marker"

        versionDir.copyToRecursively(outputRoot, followLinks = false)

        inputRoot.copyToRecursively(
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
