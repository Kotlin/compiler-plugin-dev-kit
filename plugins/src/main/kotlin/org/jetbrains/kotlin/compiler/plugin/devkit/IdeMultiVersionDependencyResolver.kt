/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")
@file:OptIn(ExternalKotlinTargetApi::class)

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinResolvedBinaryDependency
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver.Companion.gradleArtifact
import org.jetbrains.kotlin.gradle.plugin.ide.dependencyResolvers.IdeBinaryDependencyResolver
import org.jetbrains.kotlin.gradle.plugin.ide.dependencyResolvers.IdeBinaryDependencyResolver.ArtifactResolutionStrategy
import org.jetbrains.kotlin.gradle.plugin.sources.AbstractKotlinSourceSet
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

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

private class IdeMultiVersionDependencyResolver(private val resolver: IdeBinaryDependencyResolver) :
    IdeDependencyResolver {
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
                 * inappropriate IDE Modules
                 * TODO this might not trigger if our version is different from the resolved version
                 *  we should just go with the version from the jar's name.
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

context(_: HasProject)
internal fun IdeMultiVersionCompilationBinaryDependencyResolver(
    preferredVersionForIdeImport: Provider<out KotlinToolingVersion>
): IdeDependencyResolver =
    IdeMultiVersionDependencyResolver(
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
    IdeMultiVersionDependencyResolver(
        artifactResolutionStrategy =
            ArtifactResolutionStrategy.Compilation(
                compilationSelector = { it.selectCompilation(preferredVersionForIdeImport.orNull) },
                componentFilter = { it is ProjectComponentIdentifier },
                setupArtifactViewAttributes = {
                    // for local projects, the artifact transform messes up the IDE since it can't
                    // identify the project from the artifact, so we turn it off
                    attribute(KotlinCompilerVersion.attribute, KotlinCompilerVersion.noVersion)
                },
            )
    )
