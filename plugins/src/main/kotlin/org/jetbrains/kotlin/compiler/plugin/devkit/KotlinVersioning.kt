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
import org.jetbrains.kotlin.tooling.core.isDev
import org.jetbrains.kotlin.tooling.core.toKotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

private val KotlinToolingVersion.lowestDev: KotlinToolingVersion
    get() {
        require(isDev)
        val classifier = requireNotNull(classifier)
        val lastDashIndex = classifier.indexOfLast { it == '-' }
        val newClassifier =
            if (lastDashIndex == -1) classifier else classifier.substring(0, lastDashIndex)
        return toKotlinVersion().toKotlinToolingVersion(newClassifier)
    }
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
    if (requested.group == "org.jetbrains.kotlin") {
        val newVersion = if (version.isDev) "[${version.lowestDev}, $version]" else "$version"
        when (requested.name) {
            "kotlin-compiler-tests" if version.isDev ->
                useTarget("${requested.group}:kotlin-compiler-tests-for-ide:$newVersion")
            "kotlin-compiler-tests" ->
                useTarget("${requested.group}:kotlin-compiler-internal-test-framework:$newVersion")
            else -> useVersion(newVersion)
        }
    }
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
