package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.LibraryElements
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.compiler.plugin.devkit.KotlinCompilerVersion.Companion.noVersion
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

private val KotlinToolingVersion.capitalizedVersionName
    get() = "K$baseVersionName"

context(_: HasProject)
internal fun NamedDomainObjectProvider<out Configuration>.withKotlinVersion(
    version: KotlinToolingVersion,
    action: ResolvableConfiguration.() -> Unit = {},
) =
    configurations.resolvable("$name${version.capitalizedVersionName}") {
        extendsFrom(this@withKotlinVersion)
        resolutionStrategy.forceKotlinVersion(version)
        action()
    }

context(_: HasProject)
internal fun Configuration.withCompilerVersionAttribute(version: KotlinToolingVersion) {
    attributes.attribute(
        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
        project.objects.named(LibraryElements.JAR),
    )
    attributes.attribute(
        KotlinCompilerVersion.attribute,
        KotlinCompilerVersion(version),
    )
}

internal fun ResolutionStrategy.forceKotlinVersion(version: KotlinToolingVersion) = eachDependency {
    if (requested.group == "org.jetbrains.kotlin") useVersion(version.versionString)
}

internal fun KotlinCompilation<*>.forceKotlinVersion(version: KotlinToolingVersion) {
    configurations.named(compileDependencyConfigurationName) {
        resolutionStrategy.forceKotlinVersion(version)
        withCompilerVersionAttribute(version)
    }
    val runtimeDependencyConfigurationName = runtimeDependencyConfigurationName ?: return
    configurations.named(runtimeDependencyConfigurationName) {
        resolutionStrategy.forceKotlinVersion(version)
        withCompilerVersionAttribute(version)
    }
}

context(_: HasProject)
internal fun DependencyHandler.registerMultiReleasePluginTransform(version: KotlinToolingVersion) {
    registerTransform(MultiReleasePluginTransform::class.java) {
        parameters { kotlinVersion.set(version.versionString) }
        from.attribute(KotlinCompilerVersion.attribute, noVersion)
        to.attribute(KotlinCompilerVersion.attribute, KotlinCompilerVersion(version))
    }
}
