package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.LibraryElements
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.compiler.plugin.devkit.KnownVersions.kotlinModuleMappings
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
        val forIde = version in ideVersions
        resolutionStrategy.forceKotlinVersion(version, forIde)
        action()
    }

context(_: HasProject)
private fun Configuration.withCompilerVersionAttribute(version: KotlinToolingVersion) {
    attributes.attribute(
        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
        project.objects.named(LibraryElements.JAR),
    )
    attributes.attribute(
        KotlinCompilerVersion.attribute,
        KotlinCompilerVersion(version),
    )
}

// generators and JS tests are not in kotlin-compiler-tests-for-ide
// so we use the closest kotlin-compiler-internal-test-framework to provide them
// while hoping that it somehow doesn't conflict with kotlin-compiler-tests-for-ide
private fun ResolutionStrategy.forceKotlinVersion(version: KotlinToolingVersion, forIde: Boolean) =
    eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            when (requested.name) {
                "kotlin-compiler-tests" if forIde ->
                    useTarget("${requested.group}:kotlin-compiler-tests-for-ide:$version")
                "kotlin-compiler-tests" ->
                    useTarget("${requested.group}:kotlin-compiler-internal-test-framework:$version")
                "kotlin-compiler-internal-test-framework" if forIde ->
                    useVersion("[${version.lowestDev}, $version]")
                else ->
                    when (val mapped = kotlinModuleMappings[requested.name]) {
                        null -> {}
                        else if !forIde -> useVersion("$version")
                        "UNSUPPORTED",
                        "DEAD" ->
                            error(
                                "Requested dependency $requested has no reasonable IDE equivalent"
                            )
                        else -> useTarget("${requested.group}:$mapped:$version")
                    }
            }
        }
    }

internal fun KotlinCompilation<*>.forceKotlinVersion(version: KotlinToolingVersion) {
    val forIde = version in ideVersions
    configurations.named(compileDependencyConfigurationName) {
        resolutionStrategy.forceKotlinVersion(version, forIde)
        withCompilerVersionAttribute(version)
        // not released for IDE, but unnecessary anyway
        if (forIde) exclude("org.jetbrains.kotlin", "kotlin-bom")
    }
    val runtimeDependencyConfigurationName = runtimeDependencyConfigurationName ?: return
    configurations.named(runtimeDependencyConfigurationName) {
        resolutionStrategy.forceKotlinVersion(version, forIde)
        withCompilerVersionAttribute(version)
        // not released for IDE, but unnecessary anyway
        if (forIde) exclude("org.jetbrains.kotlin", "kotlin-bom")
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
