package org.jetbrains.kotlin.compiler.plugin.devkit

import java.nio.file.FileSystems.newFileSystem
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
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
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.PLUGIN_PATH
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.addOverlayAndDependencies
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.openDirectory
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

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
            val currentVersion =
                parameters.kotlinVersion
                    .get()
                    .let(::KotlinToolingVersion)
                    .let(CompilerVersionAliases::map)
            input.openDirectory { inputRoot ->
                val version: KotlinToolingVersion
                val paths = buildList {
                    version =
                        addOverlayAndDependencies(null, inputRoot, currentVersion) ?: return@default
                }
                return if (input.isDirectory())
                    outputs.dir(input.name + "-$version").toPath().copyFrom(paths)
                else {
                    val output =
                        outputs.file(input.name.removeSuffix(".jar") + "-$version.jar").toPath()
                    newFileSystem(output, mapOf("create" to true)).use {
                        it.rootDirectories.single().copyFrom(paths)
                    }
                }
            }
        }
        // Default: pass through original artifact
        if (input.isDirectory()) outputs.dir(inputArtifact)
        else if (input.exists()) outputs.file(inputArtifact)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun Path.copyFrom(paths: Iterable<Path>) = paths.forEach {
        it.copyToRecursively(this, followLinks = false) { src, dst ->
            when {
                dst.endsWith(PLUGIN_PATH) -> SKIP_SUBTREE
                dst.exists() -> CONTINUE
                else -> src.copyToIgnoringExistingDirectory(dst, followLinks = false)
            }
        }
    }
}
