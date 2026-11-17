@file:Suppress("FunctionName")
@file:OptIn(ExternalKotlinTargetApi::class)

package org.jetbrains.kotlin.compiler.plugin.devkit

import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.PLUGIN_PATH
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.addOverlayAndDependencies
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.openDirectory
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectArtifactDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinResolvedBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.extras.artifactsClasspath
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver.Companion.gradleArtifact
import org.jetbrains.kotlin.gradle.plugin.ide.dependencyResolvers.IdeBinaryDependencyResolver
import org.jetbrains.kotlin.gradle.plugin.ide.dependencyResolvers.IdeBinaryDependencyResolver.ArtifactResolutionStrategy
import org.jetbrains.kotlin.gradle.plugin.sources.AbstractKotlinSourceSet
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

public const val SOURCE_SETS_PATH: String = "sourceSets"

private fun KotlinSourceSet.selectCompilation(
    preferredVersionForIdeImport: KotlinToolingVersion?
): KotlinCompilation<*>? {
    this as AbstractKotlinSourceSet
    if (preferredVersionForIdeImport != null)
        compilations
            .find { (it.target as? DevKitTarget)?.version == preferredVersionForIdeImport }
            ?.let {
                return it
            }
    return compilations.maxWithOrNull(compareBy { (it.target as? DevKitTarget)?.version })
}

private class IdeMultiVersionBinaryDependencyResolver(
    private val resolver: IdeBinaryDependencyResolver
) : IdeDependencyResolver {
    constructor(
        binaryType: String = IdeaKotlinBinaryDependency.KOTLIN_COMPILE_BINARY_TYPE,
        artifactResolutionStrategy: ArtifactResolutionStrategy =
            ArtifactResolutionStrategy.Compilation(),
    ) : this(IdeBinaryDependencyResolver(binaryType, artifactResolutionStrategy))

    override fun resolve(sourceSet: KotlinSourceSet) =
        resolver
            .resolve(sourceSet)
            .map {
                if (it !is IdeaKotlinResolvedBinaryDependency) return@map it
                val coordinates = it.coordinates ?: return@map it
                val artifact = it.gradleArtifact ?: return@map it
                val version =
                    coordinates.attributes[KotlinCompilerVersion.attribute.name].takeUnless(
                        KotlinCompilerVersion.NO_VERSION::equals
                    ) ?: return@map it
                /**
                 * Without disambiguation by [version] IDE incorrectly creates one Library per GAV
                 * with one of the resolution artifacts from the multi-version jar and reuses it in
                 * inappropriate IDE Modules TODO this might not trigger if our version is different
                 * from the resolved version we should just go with the version from the jar's name.
                 */
                if (artifact.file.nameWithoutExtension.endsWith("-mv-$version")) {
                    it.copy(
                        coordinates =
                            coordinates.copy(
                                sourceSetName = KotlinToolingVersion(version).versionName
                            )
                    )
                } else it
            }
            .toSet()
}

private class IdeMultiVersionProjectDependencyResolver(
    private val resolver: IdeBinaryDependencyResolver,
    private val preferredVersionForIdeImport: Provider<out KotlinToolingVersion>,
) : IdeDependencyResolver {
    constructor(
        preferredVersionForIdeImport: Provider<out KotlinToolingVersion>,
        binaryType: String = IdeaKotlinBinaryDependency.KOTLIN_COMPILE_BINARY_TYPE,
        artifactResolutionStrategy: ArtifactResolutionStrategy =
            ArtifactResolutionStrategy.Compilation(),
    ) : this(
        IdeBinaryDependencyResolver(binaryType, artifactResolutionStrategy),
        preferredVersionForIdeImport,
    )

    override fun resolve(sourceSet: KotlinSourceSet): Set<IdeaKotlinDependency> =
        resolver
            .resolve(sourceSet)
            .flatMap { dependency ->
                if (dependency !is IdeaKotlinProjectArtifactDependency)
                    return@flatMap listOf(dependency)

                val version =
                    sourceSet.selectCompilation(preferredVersionForIdeImport.orNull)?.let {
                        (it.target as? DevKitTarget)?.version
                    } ?: return@flatMap listOf(dependency)

                val sourceSets =
                    dependency.artifactsClasspath.firstNotNullOfOrNull { artifact ->
                        findSourceSetsForVersion(artifact.toPath(), version)?.takeIf {
                            it.isNotEmpty()
                        }
                    } ?: return@flatMap listOf(dependency)

                dependency.resolved(sourceSets)
            }
            .toSet()
}

context(_: HasProject)
internal fun IdeMultiVersionCompilationBinaryDependencyResolver(
    preferredVersionForIdeImport: Provider<out KotlinToolingVersion>
): IdeDependencyResolver =
    IdeMultiVersionBinaryDependencyResolver(
        artifactResolutionStrategy =
            ArtifactResolutionStrategy.Compilation(
                compilationSelector = { it.selectCompilation(preferredVersionForIdeImport.orNull) },
                componentFilter = { it !is ProjectComponentIdentifier },
            )
    )

context(_: HasProject)
internal fun IdeMultiVersionCompilationProjectDependencyResolver(
    preferredVersionForIdeImport: Provider<out KotlinToolingVersion>
): IdeDependencyResolver =
    IdeMultiVersionProjectDependencyResolver(
        preferredVersionForIdeImport = preferredVersionForIdeImport,
        artifactResolutionStrategy =
            ArtifactResolutionStrategy.Compilation(
                compilationSelector = { it.selectCompilation(preferredVersionForIdeImport.orNull) },
                componentFilter = { it is ProjectComponentIdentifier },
                setupArtifactViewAttributes = {
                    // for local projects, the artifact transform messes up the IDE since it can't
                    // identify the project from the artifact, so we turn it off
                    attribute(KotlinCompilerVersion.attribute, KotlinCompilerVersion.noVersion)
                },
            ),
    )

internal fun findSourceSetsForVersion(
    path: Path,
    version: KotlinToolingVersion,
): List<String>? {
    val resolvedVersion =
        with(DevNullList) { addOverlayAndDependencies(null, path, version) } ?: return null

    path.openDirectory { root ->
        val id =
            (root / PLUGIN_PATH)
                .takeIf { it.isDirectory() }
                ?.listDirectoryEntries()
                ?.singleOrNull()
                ?.name ?: return@openDirectory

        val sourceSetsFile = root / PLUGIN_PATH / id / SOURCE_SETS_PATH / resolvedVersion.toString()
        return sourceSetsFile
            .takeIf { it.exists() }
            ?.readText()
            .orEmpty()
            .lines()
            .filter {
                it.isNotBlank()
            }
    }
    return null
}

private object DevNullList : AbstractMutableList<Any?>() {
    override val size: Int
        get() = 0

    override fun get(index: Int) = throw IndexOutOfBoundsException()

    override fun set(index: Int, element: Any?) = throw IndexOutOfBoundsException()

    override fun removeAt(index: Int): Any? = throw IndexOutOfBoundsException()

    override fun add(index: Int, element: Any?) {}
}
