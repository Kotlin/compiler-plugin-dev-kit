package org.jetbrains.kotlin.compiler.plugin.devkit

plugins {
    kotlin("jvm")
    id("com.github.gmazzo.buildconfig")
    `java-gradle-plugin`
    `java-test-fixtures`
}

dependencies {
    implementation(kotlin("gradle-plugin-api"))
    implementation("org.jetbrains.kotlin.compiler.plugin.devkit:gradle-plugin-runtime")
    testImplementation(kotlin("test-junit5"))
}

interface PluginConfigExtension {
    val compilerPlugin: Property<Project>
    val pluginAnnotations: Property<Project>
}

val configExtension = project.extensions.create<PluginConfigExtension>("pluginConfig")
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
    it.plugins.withId("org.jetbrains.kotlin.compiler.plugin.devkit.compiler-plugin") {
        if (noCompilerPluginProjects) {
            compilerPlugin.convention(it)
            noCompilerPluginProjects = false
        } else {
            compilerPlugin.convention(null)
        }
    }
}

buildConfig {
    packageName(project.group.toString())

    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")

    buildConfigField("KOTLIN_PLUGIN_ARTIFACT") {
        type("org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact")
        expression(
            compilerPlugin.map {
                """
        org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact(
          groupId = "${it.group}",
          artifactId = "${it.name}",
          version = "${it.version}",
        )
      """
                    .trimIndent()
            }
        )
    }

    buildConfigField("ANNOTATIONS_LIBRARY_COORDINATES") {
        type("String")
        expression(pluginAnnotations.map { "\"${it.group}:${it.name}:${it.version}\"" })
    }
    buildConfigField("PLUGIN_INFO") {
        type("org.jetbrains.kotlin.compiler.plugin.devkit.DevKitPluginInfo")
        expression(
            """
            org.jetbrains.kotlin.compiler.plugin.devkit.DevKitPluginInfo(
              id = KOTLIN_PLUGIN_ID,
              artifact = KOTLIN_PLUGIN_ARTIFACT,
              libraryCoordinates = ANNOTATIONS_LIBRARY_COORDINATES,
            )
            """
                .trimIndent()
        )
    }
}
