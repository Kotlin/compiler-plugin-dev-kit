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
import org.jetbrains.kotlin.compiler.plugin.devkit.CompilerVersionAliases.ideBranchPoint
import org.jetbrains.kotlin.compiler.plugin.devkit.KnownVersions.kotlinModuleMappings
import org.jetbrains.kotlin.compiler.plugin.devkit.KotlinCompilerVersion.Companion.noVersion
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

private val KotlinToolingVersion.capitalizedVersionName
    get() = "K$baseVersionName"

/**
 * The `dev` build this version's IDE branch was cut from, as closely as the recorded IDE builds pin
 * it down: what the newest IDE build of the same baseline shipped before that line moved onto `-ij`
 * builds. Null when no recorded build of the baseline predates the branch.
 */
internal val KotlinToolingVersion.ideBranchPoint: KotlinToolingVersion?
    get() {
        return if (!isIdeBuild) this
        else
            CompilerVersionAliases.BUILT_IN_COMPILER_VERSION_ALIASES.filterValues { it == this }
                .keys
                .minOrNull()
                ?.ideBranchPoint
    }

context(_: HasProject)
internal fun NamedDomainObjectProvider<out Configuration>.withKotlinVersion(
    version: KotlinToolingVersion,
    forIde: Boolean,
    action: ResolvableConfiguration.() -> Unit = {},
) =
    configurations.resolvable("$name${version.capitalizedVersionName}") {
        extendsFrom(this@withKotlinVersion)
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

private fun ResolutionStrategy.forceKotlinVersion(version: KotlinToolingVersion, forIde: Boolean) =
    dependencySubstitution.all {
        val requested = requested
        if (requested is ModuleComponentSelector && requested.group == "org.jetbrains.kotlin") {
            when (val mapped = kotlinModuleMappings[requested.module]) {
                null -> {}
                else if !forIde -> useVersion("$version")
                "UNSUPPORTED",
                "DEAD" -> error("Requested dependency $requested has no reasonable IDE equivalent")
                else -> useModule(mapped, "$version")
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

internal fun KotlinCompilation<*>.forceKotlinVersion(
    version: KotlinToolingVersion,
    forIde: Boolean,
) {
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
