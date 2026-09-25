package org.jetbrains.kotlin.compiler.plugin.devkit

import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.name
import org.gradle.api.Plugin
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.maven

public class DevKitSettingsPlugin : Plugin<Settings> {
    override fun apply(target: Settings): Unit = target.run {
        extensions.create("pluginDevKit", DevKitSettingsExtension::class.java, this)
        pluginManagement.repositories.kotlinDevRepositories()
        dependencyResolutionManagement.repositories.kotlinDevRepositories()
        val includedCompanionBuilds =
            listFromGradleProperty(COMPANION_LIBRARIES_PROPERTY).filter { it.isIncludedBuildPath }

        includedCompanionBuilds.forEach { includeBuild(it) }
        gradle.lifecycle.beforeProject {
            if (this == rootProject && includedCompanionBuilds.isNotEmpty()) {
                pluginManager.apply("base")
                tasks.named("check") {
                    includedCompanionBuilds.forEach {
                        dependsOn(gradle.includedBuild(Path(it).name).task(":check"))
                    }
                }
            }
            pluginManager.apply("$DEV_KIT_GROUP.functional-test-publishing")
        }
    }
}

public abstract class DevKitSettingsExtension(private val settings: Settings) {
    @get:Inject protected abstract val objects: ObjectFactory

    /**
     * Includes the build at [path], and uses the [pathToVersionCatalog] relative to [path] to
     * create a `libs` version catalog
     */
    public fun includeRootBuild(
        path: String,
        pathToVersionCatalog: String? = "gradle/libs.versions.toml",
    ): Unit = settings.run {
        pluginManagement.includeBuild(path)
        includeBuild(path)
        if (pathToVersionCatalog != null)
            dependencyResolutionManagement {
                val catalogPath = settings.rootDir.toPath() / path / pathToVersionCatalog
                versionCatalogs {
                    create("libs") { from(objects.fileCollection().from(catalogPath)) }
                }
            }
    }
}

private fun RepositoryHandler.kotlinDevRepositories() {
    maven("https://redirector.kotlinlang.org/maven/bootstrap")
    maven("https://redirector.kotlinlang.org/maven/dev/")
    // Publications used by IJ
    // https://kotlinlang.slack.com/archives/C7L3JB43G/p1757001642402909
    maven("https://redirector.kotlinlang.org/maven/intellij-dependencies/")
}
