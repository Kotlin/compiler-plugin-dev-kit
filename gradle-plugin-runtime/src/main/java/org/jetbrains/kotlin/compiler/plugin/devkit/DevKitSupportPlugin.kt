package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public data class DevKitPluginInfo(
    public val id: String,
    public val artifact: SubpluginArtifact,
    public val companionLibraryCoordinates: List<String>,
)

public abstract class DevKitSupportPlugin(public val info: DevKitPluginInfo) :
    KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = info.id

    override fun getPluginArtifact(): SubpluginArtifact = info.artifact

    public open fun Project.shouldAddRuntimeDependency(): Boolean = true

    final override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.project

        if (project.shouldAddRuntimeDependency())
            kotlinCompilation.defaultSourceSet.dependencies {
                info.companionLibraryCoordinates.forEach(::implementation)
            }

        return project.applyToCompilation(kotlinCompilation)
    }

    public open fun Project.applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> = provider { emptyList() }
}
