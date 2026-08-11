/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSet
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.utils.addExtendsFromRelation

internal fun createResourcesTask(compilation: DevKitCompilation) {
    val project = compilation.project
    val resourceSet = project.files({ compilation.allKotlinSourceSets.map { it.resources } })
    val resourcesDestinationDir = project.file(compilation.output.resourcesDir)
    project.locateOrRegisterTask<ProcessResources>(compilation.processResourcesTaskName) {
        resourcesTask ->
        resourcesTask.description = "Processes ${resourceSet}."
        resourcesTask.from(resourceSet)
        resourcesTask.into(resourcesDestinationDir)
    }
}

internal fun wireJavaSources(compilation: DevKitCompilation) {
    val javaSourceSet = compilation.javaSourceSet
    val compileJavaTask = compilation.compileJavaTaskProvider

    if (compilation.name == KotlinCompilation.MAIN_COMPILATION_NAME) {
        // Because configuration names are different in this particular case
        // TODO maybe unnecessary
        setupDependenciesCrossInclusionForJava(compilation, javaSourceSet)
    }

    setupJavaSourceSetSourcesAndResources(javaSourceSet, compilation)

    val javaClasses =
        compilation.project.layout.files(compileJavaTask.map { it.destinationDirectory })
    compilation.output.classesDirs.from(javaClasses)

    // Configure proper outputs for SourceSet
    (javaSourceSet.output.classesDirs as? ConfigurableFileCollection)?.from(
        compilation.output.classesDirs.minus(javaClasses)
    )

    javaSourceSet.output.setResourcesDir(
        java.util.concurrent.Callable { compilation.output.resourcesDir }
    )
}

internal fun setupJavaSourceSetSourcesAndResources(
    javaSourceSet: SourceSet,
    compilation: DevKitCompilation,
) {
    val project = compilation.target.project
    javaSourceSet.java.setSrcDirs(listOf("src/${compilation.defaultSourceSet.name}/java"))
    compilation.defaultSourceSet.kotlin.srcDirs(javaSourceSet.java.sourceDirectories)

    // To avoid confusion in the sources layout, remove the default Java source directories
    // (like src/main/java, src/test/java) and instead add sibling directories to those where the
    // Kotlin
    // sources are placed (i.e. src/jvmMain/java, src/jvmTest/java):
    javaSourceSet.resources.setSrcDirs(compilation.defaultSourceSet.resources.sourceDirectories)
    compilation.defaultSourceSet.resources.srcDirs(javaSourceSet.resources.sourceDirectories)
    project.tasks
        .named(
            compilation.processResourcesTaskName,
            ProcessResources::class.java,
        )
        .configure {
            // Now 'compilation' has additional resources dir from java compilation which points to
            // the initial
            // resources location. Because of this, ProcessResources task will copy same files
            // twice,
            // so we are excluding duplicates.
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

    // The resources' processing is done with the Kotlin resource processing task:
    project.tasks.named(javaSourceSet.processResourcesTaskName).configure {
        dependsOn(project.tasks.named(compilation.processResourcesTaskName))
        enabled = false
    }
}

// see
// https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph
internal fun setupDependenciesCrossInclusionForJava(
    compilation: DevKitCompilation,
    javaSourceSet: SourceSet,
) {
    // Make sure Kotlin compilation dependencies appear in the Java source set classpaths:
    val project = compilation.project

    listOfNotNull(
            compilation.legacyApiConfigurationName,
            compilation.legacyImplementationConfigurationName,
            compilation.legacyCompileOnlyConfigurationName,
        )
        .forEach { configurationName ->
            project.addExtendsFromRelation(
                javaSourceSet.compileClasspathConfigurationName,
                configurationName,
            )
        }

    listOfNotNull(
            compilation.legacyApiConfigurationName,
            compilation.legacyImplementationConfigurationName,
            compilation.legacyRuntimeOnlyConfigurationName,
        )
        .forEach { configurationName ->
            project.addExtendsFromRelation(
                javaSourceSet.runtimeClasspathConfigurationName,
                configurationName,
            )
        }

    listOfNotNull(
            javaSourceSet.compileOnlyConfigurationName,
            javaSourceSet.apiConfigurationName.takeIf {
                project.configurations.findByName(it) != null
            },
            javaSourceSet.implementationConfigurationName,
        )
        .forEach { configurationName ->
            project.addExtendsFromRelation(
                compilation.compileDependencyConfigurationName,
                configurationName,
            )
        }

    listOfNotNull(
            javaSourceSet.runtimeOnlyConfigurationName,
            javaSourceSet.apiConfigurationName.takeIf {
                project.configurations.findByName(it) != null
            },
            javaSourceSet.implementationConfigurationName,
        )
        .forEach { configurationName ->
            project.addExtendsFromRelation(
                compilation.runtimeDependencyConfigurationName,
                configurationName,
            )
        }
}

private val DevKitCompilation.legacyApiConfigurationName
    get() = defaultSourceSet.apiConfigurationName
private val DevKitCompilation.legacyImplementationConfigurationName
    get() = defaultSourceSet.implementationConfigurationName
private val DevKitCompilation.legacyRuntimeOnlyConfigurationName
    get() = defaultSourceSet.runtimeOnlyConfigurationName
private val DevKitCompilation.legacyCompileOnlyConfigurationName
    get() = defaultSourceSet.compileOnlyConfigurationName
