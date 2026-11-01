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
        gradle.settingsEvaluated {
            dependencyResolutionManagement.repositories.kotlinDevRepositories()
        }
        gradle.lifecycle.beforeProject {
            pluginManager.apply("$DEV_KIT_GROUP.functional-test-publishing")
        }
        extensions.create("pluginDevKit", DevKitSettingsExtension::class.java, this)
    }
}

public abstract class DevKitSettingsExtension(private val settings: Settings) {
    @get:Inject protected abstract val objects: ObjectFactory

    /**
     * Uses the [pathToVersionCatalog] relative to [pathToRoot] to create a [name] version catalog
     */
    public fun createVersionCatalogFrom(
        pathToRoot: String,
        pathToVersionCatalog: String = "gradle/libs.versions.toml",
        name: String = "libs",
    ): Unit = settings.run {
        dependencyResolutionManagement {
            val catalogPath = settings.rootDir.toPath() / pathToRoot / pathToVersionCatalog
            versionCatalogs {
                create(name) { from(objects.fileCollection().from(catalogPath)) }
            }
        }
    }

    /** Includes the build at [path], and wires [Settings.rootProject]'s `:check` task to it. */
    public fun includeBuildWithChecks(path: String): Unit = settings.run {
        includeBuild(path)
        gradle.beforeProject {
            if (this == rootProject) {
                pluginManager.apply("base")
                tasks.named("check") {
                    dependsOn(gradle.includedBuild(Path(path).name).task(":check"))
                }
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
