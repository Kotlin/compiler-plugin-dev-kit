package org.jetbrains.kotlin.compiler.plugin.devkit

import javax.inject.Inject
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.DEPENDENCIES_PATH
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.PLUGIN_PATH
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution.VERSIONS_PATH
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal abstract class DevKitJar : Jar() {
    @get:Inject abstract val archiveOperations: ArchiveOperations
    @get:InputFiles abstract val includedDependencies: ConfigurableFileCollection
    @get:Input abstract val pluginId: Property<String>

    // TODO technically uses internal API. See if it's exposed elsewhere
    private val versionedFiles: CopySpec = mainSpec.addChild()

    fun versioned(version: KotlinToolingVersion, spec: CopySpec.() -> Unit) =
        versionedFiles.into(version.versionString, spec)

    @TaskAction
    override fun copy() =
        with(ProviderFlatMap.Unsafe) {
            versionedFiles.into("$PLUGIN_PATH/${pluginId()}/$VERSIONS_PATH")
            into("$PLUGIN_PATH/${pluginId()}/$DEPENDENCIES_PATH") {
                includedDependencies.elements().forEach {
                    into(it.asFile.name.removeSuffix(".jar")) {
                        from(archiveOperations.zipTree(it.asFile))
                    }
                }
            }
            super.copy()
        }
}
