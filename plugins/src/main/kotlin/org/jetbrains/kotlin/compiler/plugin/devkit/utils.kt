package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.Action
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal val KotlinToolingVersion.versionString: String
    get() = toString()

// Uses same convention as Metro
// https://github.com/ZacSweers/metro/tree/7e38de5535d66ea5491e155711fd9830c564924a/compiler-compat#version-naming-convention
public val KotlinToolingVersion.baseVersionName: String
    get() = versionString.replace(".", "").replace("-", "_").lowercase()
public val KotlinToolingVersion.versionName: String
    get() = "k$baseVersionName"

internal fun DependencyHandler.registerMultiReleasePluginTransform(
    version: Provider<KotlinToolingVersion>
) {
    registerTransform(MultiReleasePluginTransform::class.java) {
        parameters { kotlinVersion.set(version.map(KotlinToolingVersion::versionString)) }
        from.attribute(KotlinCompilerVersion.attribute, noVersion)
        to.attributeProvider(KotlinCompilerVersion.attribute, version.map(::KotlinCompilerVersion))
    }
}

internal fun DependencyHandler.setupCompilerVersionAttribute() {
    artifactTypes {
        all {
            attributes.attribute(KotlinCompilerVersion.attribute, noVersion)
        }
    }
}

internal fun DependencyHandler.addIf(
    condition: Provider<out Boolean>,
    configurationName: String,
    dependencyNotation: Any,
    action: Action<in ExternalModuleDependency> = {},
) = addProvider(configurationName, condition.filter { it }.map { dependencyNotation }, action)
