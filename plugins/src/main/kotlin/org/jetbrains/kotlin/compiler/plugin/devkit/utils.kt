package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.Action
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal val KotlinToolingVersion.versionString: String
    get() = toString()

// Uses same convention as Metro
// https://github.com/ZacSweers/metro/tree/7e38de5535d66ea5491e155711fd9830c564924a/compiler-compat#version-naming-convention
public val KotlinToolingVersion.baseVersionName: String
    get() = versionString.replace(".", "").replace("-", "_").lowercase()
public val KotlinToolingVersion.versionName: String
    get() = "k$baseVersionName"

internal fun DependencyHandler.setupCompilerVersionAttribute() {
    artifactTypes {
        all {
            attributes.attribute(KotlinCompilerVersion.attribute, noVersion)
        }
    }
}

internal fun DependencyHandler.addIf(
    condition: Provider<out Boolean>,
    configurationName: String,
    dependencyNotation: Any,
    action: Action<in ExternalModuleDependency> = {},
) = addProvider(configurationName, condition.filter { it }.map { dependencyNotation }, action)

internal inline fun <reified T : Any> ObjectFactory.property(convention: T) =
    property<T>().convention(convention)

internal inline fun <reified T : Any> ObjectFactory.property(convention: Provider<T>) =
    property<T>().convention(convention)

internal val SourceSet.kotlin: SourceDirectorySet
    get() = extensions.getByName("kotlin") as SourceDirectorySet

context(kotlinExtension: KotlinJvmProjectExtension)
internal val SourceSet.compilation
    get() = kotlinExtension.target.compilations.named(name)

context(hp: HasProject)
internal val project
    get() = hp.project

context(_: HasProject)
internal val configurations
    get() = project.configurations
context(_: HasProject)
internal val tasks
    get() = project.tasks
context(_: HasProject)
internal val layout
    get() = project.layout
context(_: HasProject)
internal val rootDir
    get() = project.rootDir
context(_: HasProject)
internal val dependencies
    get() = project.dependencies
context(_: HasProject)
internal val objects
    get() = project.objects
context(_: HasProject)
internal val kotlinToolingVersion
    get() = project.kotlinToolingVersion

context(_: HasProject)
internal fun dependencies(configuration: DependencyHandlerScope.() -> Unit) =
    project.dependencies(configuration)
