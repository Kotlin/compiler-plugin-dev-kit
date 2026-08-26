package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
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
    dependencySubstitution.all {
        val requested = requested
        if (requested is ModuleComponentSelector && requested.group == "org.jetbrains.kotlin") {
            when (requested.module) {
                "kotlin-compiler-tests" if forIde ->
                    useModule("kotlin-compiler-tests-for-ide", "$version")
                "kotlin-compiler-tests" ->
                    useModule("kotlin-compiler-internal-test-framework", "$version")
                "kotlin-compiler-internal-test-framework" if forIde ->
                    useVersion("[${version.lowestDev}, $version]")
                else ->
                    when (val mapped = kotlinModuleMappings[requested.module]) {
                        null -> {}
                        else if !forIde -> useVersion("$version")
                        "UNSUPPORTED",
                        "DEAD" ->
                            error(
                                "Requested dependency $requested has no reasonable IDE equivalent"
                            )
                        else -> useModule(mapped, "$version")
                    }
            }
        }
    }

private fun DependencySubstitution.useVersion(version: String) =
    useModule((requested as ModuleComponentSelector).module, version)

// TODO technically uses internal API. See if
//  newSelector is exposed somewhere
private fun DependencySubstitution.useModule(
    module: String,
    version: String,
) =
    useTarget(
        DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId((requested as ModuleComponentSelector).group, module),
            DefaultMutableVersionConstraint(version).apply { strictly(version) },
            requested.attributes,
            requested.capabilitySelectors,
        )
    )

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
