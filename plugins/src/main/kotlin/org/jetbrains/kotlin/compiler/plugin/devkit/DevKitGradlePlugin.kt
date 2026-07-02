package org.jetbrains.kotlin.compiler.plugin.devkit

import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.kotlin

public interface GradlePluginConfigExtension {
    public val compilerPlugin: Property<Project>
    public val pluginAnnotations: Property<Project>
}

public class DevKitGradlePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        // TODO should we apply SAM and assign plugins?
        pluginManager.apply {
            apply("org.jetbrains.kotlin.jvm")
            apply("com.github.gmazzo.buildconfig")
            apply("java-gradle-plugin")
            apply("java-test-fixtures")
        }

        dependencies {
            "implementation"(kotlin("gradle-plugin-api"))
            "implementation"("$DEV_KIT_GROUP:gradle-plugin-runtime")
            "testImplementation"(kotlin("test-junit5"))
        }

        val configExtension = project.extensions.create<GradlePluginConfigExtension>("pluginConfig")
        val compilerPlugin = configExtension.compilerPlugin
        val pluginAnnotations = configExtension.pluginAnnotations

        rootProject.childProjects.values.forEach {
            var noMultiplatformProjects = true
            it.plugins.withId("org.jetbrains.kotlin.multiplatform") {
                if (noMultiplatformProjects) {
                    pluginAnnotations.convention(it)
                    noMultiplatformProjects = false
                } else {
                    pluginAnnotations.convention(null)
                }
            }
            var noCompilerPluginProjects = true
            it.plugins.withId("$DEV_KIT_GROUP.compiler-plugin") {
                if (noCompilerPluginProjects) {
                    compilerPlugin.convention(it)
                    noCompilerPluginProjects = false
                } else {
                    compilerPlugin.convention(null)
                }
            }
        }

        extensions.getByType<BuildConfigExtension>().forClass("${project.group}", "PluginInfo") {
            buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")

            buildConfigField("KOTLIN_PLUGIN_ARTIFACT") {
                type("org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact")
                expression(
                    compilerPlugin.map {
                        """
                        |org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact(
                        |  groupId = "${it.group}",
                        |  artifactId = "${it.name}",
                        |  version = "${it.version}",
                        |)
                        """
                            .trimMargin()
                    }
                )
            }

            buildConfigField("ANNOTATIONS_LIBRARY_COORDINATES") {
                type("String")
                expression(pluginAnnotations.map { "\"${it.group}:${it.name}:${it.version}\"" })
            }
            buildConfigField("PLUGIN_INFO") {
                type("$DEV_KIT_GROUP.DevKitPluginInfo")
                expression(
                    """
                    |$DEV_KIT_GROUP.DevKitPluginInfo(
                    |  id = KOTLIN_PLUGIN_ID,
                    |  artifact = KOTLIN_PLUGIN_ARTIFACT,
                    |  libraryCoordinates = ANNOTATIONS_LIBRARY_COORDINATES,
                    |)
                    """
                        .trimMargin()
                )
            }
        }
    }
}
