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
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.dependencyResolvers.IdeBinaryDependencyResolver
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

context(_: HasProject)
internal fun IdeMultiVersionCompilationBinaryDependencyResolver(
    preferredVersionForIdeImport: Provider<out KotlinToolingVersion>
) =
    IdeBinaryDependencyResolver(
        artifactResolutionStrategy =
            IdeBinaryDependencyResolver.ArtifactResolutionStrategy.Compilation(
                compilationSelector = { it.selectCompilation(preferredVersionForIdeImport.orNull) },
                componentFilter = { it !is ProjectComponentIdentifier },
            )
    )

context(_: HasProject)
internal fun IdeMultiVersionCompilationProjectDependencyResolver(
    preferredVersionForIdeImport: Provider<out KotlinToolingVersion>
) =
    IdeBinaryDependencyResolver(
        artifactResolutionStrategy =
            IdeBinaryDependencyResolver.ArtifactResolutionStrategy.Compilation(
                compilationSelector = { it.selectCompilation(preferredVersionForIdeImport.orNull) },
                componentFilter = { it is ProjectComponentIdentifier },
                setupArtifactViewAttributes = {
                    // for local projects, the artifact transform messes up the IDE since it can't
                    // identify the project from the artifact, so we turn it off
                    attribute(KotlinCompilerVersion.attribute, KotlinCompilerVersion.noVersion)
                },
            )
    )
