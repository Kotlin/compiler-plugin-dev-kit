// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

apply(plugin = "com.vanniktech.maven.publish")

val extension = project.extensions.create<MetroArtifactExtension>("metroArtifact")

extension.artifactId.convention(project.name)

extension.name.convention(project.name)

extension.description.convention(providers.gradleProperty("POM_DESCRIPTION"))

afterEvaluate {
  extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
      val publicationArtifactId = artifactId
      val metroArtifactId = extension.artifactId.get()
      val hasProjectNamePrefix = publicationArtifactId.startsWith("${project.name}-")
      val isGradlePluginMarker = publicationArtifactId.endsWith(".gradle.plugin")
      artifactId =
        when {
          publicationArtifactId == project.name -> metroArtifactId
          hasProjectNamePrefix -> metroArtifactId + publicationArtifactId.removePrefix(project.name)
          else -> publicationArtifactId
        }

      pom {
        name.set(extension.name)
        description.set(extension.description)
        if (!isGradlePluginMarker && extension.packaging.isPresent) {
          packaging = extension.packaging.get()
        }
      }
    }
  }
}

// Compiler-compat artifacts get explicit API mode too; only the main :compiler module is exempt.
if (project.path != ":compiler") {
  plugins.withType<KotlinBasePlugin> { configure<KotlinProjectExtension> { explicitApi() } }
}
