/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")
@file:OptIn(ExternalKotlinTargetApi::class)

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.ide.dependencyResolvers.IdeBinaryDependencyResolver
import org.jetbrains.kotlin.gradle.plugin.sources.AbstractKotlinSourceSet

context(_: HasProject)
internal fun IdeMultiVersionCompilationBinaryDependencyResolver() =
    IdeBinaryDependencyResolver(
        artifactResolutionStrategy =
            IdeBinaryDependencyResolver.ArtifactResolutionStrategy.Compilation(
                compilationSelector = { sourceSet ->
                    val compilations = (sourceSet as AbstractKotlinSourceSet).compilations
                    compilations.maxWithOrNull(compareBy { (it.target as? DevKitTarget)?.version })
                }
            )
    )
