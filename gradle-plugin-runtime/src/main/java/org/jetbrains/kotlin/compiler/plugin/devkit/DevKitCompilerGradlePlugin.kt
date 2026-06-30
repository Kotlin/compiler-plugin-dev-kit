package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

data class DevKitPluginInfo(
    val id: String,
    val artifact: SubpluginArtifact,
    val libraryCoordinates: String,
)

abstract class DevKitCompilerGradlePlugin(val info: DevKitPluginInfo) :
    KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = info.id

    override fun getPluginArtifact(): SubpluginArtifact = info.artifact

    @OptIn(ExperimentalBuildToolsApi::class, ExperimentalKotlinGradlePluginApi::class)
    final override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val kotlin = project.extensions.findByType(KotlinBaseExtension::class.java)!!

        kotlinCompilation.defaultSourceSet.dependencies {
            // Add annotations library to all compilations.
            implementation(info.libraryCoordinates)
        }
        val version = kotlin.compilerVersion.map(::KotlinToolingVersion)
        val pluginClasspathName =
            lowerCamelCaseName(
                PLUGIN_CLASSPATH_CONFIGURATION_NAME,
                kotlinCompilation.target.disambiguationClassifier,
                kotlinCompilation.name,
            )
        project.configurations.named(pluginClasspathName) {
            it.attributes.attributeProvider(
                KotlinCompilerVersion.attribute,
                version.map(::KotlinCompilerVersion),
            )
        }
        project.dependencies.setupCompilerVersionAttribute()
        project.dependencies.registerMultiReleasePluginTransform(version)

        return project.applyToCompilation(kotlinCompilation)
    }

    open fun Project.applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> = provider { emptyList() }
}

private fun lowerCamelCaseName(vararg nameParts: String?): String {
    val nonEmptyParts = nameParts.mapNotNull { it?.takeIf(String::isNotEmpty) }
    return nonEmptyParts
        .drop(1)
        .joinToString(
            separator = "",
            prefix = nonEmptyParts.firstOrNull().orEmpty(),
            transform = String::capitalizeAsciiOnly,
        )
}

private fun String.capitalizeAsciiOnly(): String {
    if (isEmpty()) return this
    val c = this[0]
    return if (c in 'a'..'z')
        buildString(length) {
            append(c.uppercaseChar())
            append(this@capitalizeAsciiOnly, 1, this@capitalizeAsciiOnly.length)
        }
    else this
}

private const val PLUGIN_CLASSPATH_CONFIGURATION_NAME = "kotlinCompilerPluginClasspath"
