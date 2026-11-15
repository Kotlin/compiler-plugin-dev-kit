package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.internal.GradleInternal
import org.gradle.api.invocation.Gradle
import org.gradle.internal.build.CompositeBuildParticipantBuildState
import org.gradle.internal.composite.IncludedBuildInternal
import org.gradle.util.Path

// Internal Gradle APIs :<

internal val IncludedBuild.gradle: Gradle?
    get() = (this as? IncludedBuildInternal)?.target?.mutableModel

internal val Gradle.asIncludedBuild: IncludedBuild?
    get() = ((this as? GradleInternal)?.owner as? CompositeBuildParticipantBuildState)?.model

internal tailrec fun Gradle.root(): Gradle =
    when (val parent = parent) {
        null -> this
        else -> parent.root()
    }

internal fun Gradle.includedBuildsByPath(): Map<Path, IncludedBuild> = buildMap {
    val visited = HashSet<Gradle>()
    asIncludedBuild?.let { put(Path.path(this@includedBuildsByPath.buildPath), it) }
    val visitBuildTree = DeepRecursiveFunction { build ->
        if (!visited.add(build)) return@DeepRecursiveFunction
        for (included in build.includedBuilds) {
            val asGradle = included.gradle ?: continue
            put(Path.path(asGradle.buildPath), included)
            callRecursive(asGradle)
        }
    }
    visitBuildTree(this@includedBuildsByPath)
}
