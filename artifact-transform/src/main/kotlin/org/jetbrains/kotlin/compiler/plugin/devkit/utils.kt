package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

val KotlinToolingVersion.versionString
    get() = toString()

fun DependencyHandler.registerMultiReleasePluginTransform(version: Provider<KotlinToolingVersion>) {
    registerTransform(MultiReleasePluginTransform::class.java) {
        it.parameters { it.kotlinVersion.set(version.map(KotlinToolingVersion::versionString)) }
        it.from
            .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
            .attribute(KotlinCompilerVersion.attribute, KotlinCompilerVersion.none)
        it.to
            .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
            .attributeProvider(
                KotlinCompilerVersion.attribute,
                version.map(::KotlinCompilerVersion),
            )
    }
}

fun DependencyHandler.setupCompilerVersionAttribute() {
    artifactTypes {
        it.named("jar") {
            it.attributes.attribute(KotlinCompilerVersion.attribute, KotlinCompilerVersion.none)
        }
    }
}
