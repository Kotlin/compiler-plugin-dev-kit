package org.jetbrains.kotlin.compiler.plugin.devkit

import com.autonomousapps.GradleTestKitSupportExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Applies `com.autonomousapps.testkit` and disables its default testKitSupportFor* publication */
public class DevKitFunctionalTestPublishingPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        pluginManager.apply("com.autonomousapps.testkit")

        // Unnecessary. Gradle functional tests should use the same coordinates as the published
        // ones anyway.
        project.configure<GradleTestKitSupportExtension> { disablePublication() }
    }
}
