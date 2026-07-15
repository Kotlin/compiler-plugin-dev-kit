package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension

/**
 * Applies `com.autonomousapps.testkit` and fixes up the friction points that come from combining
 * it with a "real" Maven publication (e.g. via `com.vanniktech.maven.publish` + signing):
 * - The plugin's auto-generated `testKitSupportFor*` publication is only meant for the local
 *   `functionalTestRepo`; it must not race the module's real publication at the same coordinates
 *   when publishing to Maven Central.
 * - Sign tasks must run before every publish task, since the testkit publication and the main
 *   publication can share outputs from each other's signing tasks.
 * - This project's `installForFunctionalTest` task is rolled into the root project's aggregator
 *   task of the same name, if the consuming build defines one.
 */
public class DevKitFunctionalTestPublishingPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        pluginManager.apply("com.autonomousapps.testkit")

        // Every publish task must run after every sign task to avoid implicit-dependency
        // validation errors where the testkit publication and main publication share outputs
        // from each other's signing tasks.
        tasks
            .named { it.startsWith("publish") && it.contains("PublicationTo") }
            .configureEach { mustRunAfter(tasks.matching { task -> task.name.startsWith("sign") }) }

        // `testKitSupportForJava` is only meant for the local FunctionalTest repo; don't let it
        // publish to Maven Central where it would race the real `maven` publication at the same
        // coordinates.
        tasks
            .matching { it.name == "publishTestKitSupportForJavaPublicationToMavenCentralRepository" }
            .configureEach { enabled = false }

        // When TestKit adds its coordinate-correcting publication, it replaces the local `maven`
        // publish. Modules whose existing coordinates already match `(group, project.name,
        // version)` keep the original task. TestKit creates the extra publication in
        // `afterEvaluate`, so check after it.
        afterEvaluate {
            val publishing = extensions.findByType(PublishingExtension::class.java) ?: return@afterEvaluate
            if (publishing.publications.findByName("testKitSupportForJava") == null) return@afterEvaluate
            tasks.matching { it.name == "publishMavenPublicationToFunctionalTestRepository" }
                .configureEach { enabled = false }
        }
    }
}
