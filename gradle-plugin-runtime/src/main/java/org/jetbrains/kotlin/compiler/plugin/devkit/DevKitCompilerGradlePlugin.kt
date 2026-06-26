package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

data class DevKitPluginInfo(
  val id: String,
  val artifact: SubpluginArtifact,
  val libraryCoordinates: String,
)

abstract class DevKitCompilerGradlePlugin(val info: DevKitPluginInfo) : KotlinCompilerPluginSupportPlugin {
  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = info.id

  override fun getPluginArtifact(): SubpluginArtifact = info.artifact

  final override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project

    kotlinCompilation.dependencies { implementation(info.libraryCoordinates) }
    if (kotlinCompilation.implementationConfigurationName == "metadataCompilationImplementation") {
      project.dependencies.add("commonMainImplementation", info.libraryCoordinates)
    }

    return project.applyToCompilation(kotlinCompilation)
  }

  open fun Project.applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
    provider { emptyList() }
}