package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.Plugin
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.maven

public class DevKitSettingsPlugin : Plugin<Settings> {
    override fun apply(target: Settings): Unit = target.run {
        pluginManagement.repositories.kotlinDevRepositories()
        dependencyResolutionManagement.repositories.kotlinDevRepositories()
        dependencyResolutionManagement.repositories.ideStarterRepositories()
        gradle.lifecycle.beforeProject {
            this@beforeProject.pluginManager.apply("$DEV_KIT_GROUP.functional-test-publishing")
        }
    }
}

/**
 * Where IDE Starter and the Driver SDK live, for the IDE tests.
 *
 * Declared here rather than on the project that wants them, because a project-level repository
 * would shadow every repository this block declares. Starter pulls platform modules of its own
 * build in transitively, so the whole namespace has to be reachable - but only that namespace,
 * which leaves these two free for the builds that never run an IDE test.
 */
private fun RepositoryHandler.ideStarterRepositories() {
    for (channel in listOf("releases", "snapshots")) {
        maven("https://www.jetbrains.com/intellij-repository/$channel") {
            content { includeGroupByRegex("""com\.jetbrains\.intellij(\..*)?""") }
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
