package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

/**
 * Gradle property listing the Kotlin versions to test against, separated by commas or whitespace.
 * Versions declared in the `pluginDevKit { }` block are registered on top of these, so a version
 * that needs an overlay or any other customization can still be named there.
 */
public const val TEST_AGAINST_PROPERTY: String = "$DEV_KIT_GROUP.testAgainst"

/**
 * Gradle property holding the version that the generic `test`/`functionalTest` task delegates to.
 * The version is registered if [TEST_AGAINST_PROPERTY] didn't already list it.
 */
public const val DEFAULT_TEST_VERSION_PROPERTY: String = "$DEV_KIT_GROUP.defaultTestVersion"

/** Gradle property holding the plugin package, and with it the plugin ID. */
public const val PLUGIN_PACKAGE_PROPERTY: String = "$DEV_KIT_GROUP.pluginPackage"

/** Gradle property holding the path of the compiler-plugin project a Gradle plugin applies. */
public const val COMPILER_PLUGIN_PROPERTY: String = "$DEV_KIT_GROUP.compilerPlugin"

/**
 * Gradle property listing the libraries a plugin ships for its users, separated by commas or
 * whitespace. An entry starting with `:` is a project path in this build, published under that
 * project's own `group`, `name` and `version`; anything else is used as-is as a
 * `group:artifact:version` coordinate.
 */
public const val COMPANION_LIBRARIES_PROPERTY: String = "$DEV_KIT_GROUP.companionLibraries"

private val entrySeparators = Regex("[,\\s]+")

context(_: HasProject)
internal fun listFromGradleProperty(property: String): List<String> =
    providers.gradleProperty(property).getOrElse("").split(entrySeparators).filter {
        it.isNotEmpty()
    }

/**
 * A `:`-prefixed entry of a library property is a project in this build, everything else a module.
 */
internal val String.isProjectPath: Boolean
    get() = startsWith(':')

internal val KotlinToolingVersion.versionString: String
    get() = toString()

// Uses same convention as Metro
// https://github.com/ZacSweers/metro/tree/7e38de5535d66ea5491e155711fd9830c564924a/compiler-compat#version-naming-convention
public val KotlinToolingVersion.baseVersionName: String
    get() = versionString.replace(".", "").replace("-", "_").lowercase()
public val KotlinToolingVersion.versionName: String
    get() = "k$baseVersionName"

context(_: HasProject)
internal fun DependencyHandler.setupCompilerVersionAttribute() {
    artifactTypes {
        all {
            attributes.attribute(KotlinCompilerVersion.attribute, noVersion)
        }
    }
}

context(_: HasProject)
internal fun <T> Provider<out Boolean>.ifTrue(value: ProviderFlatMap.() -> T?) = provider {
    if (orNull == true) value() else null
}

internal inline fun <reified T : Any> ObjectFactory.property(convention: T) =
    property<T>().convention(convention)

internal inline fun <reified T : Any> ObjectFactory.property(convention: Provider<T>) =
    property<T>().convention(convention)

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
internal val providers
    get() = project.providers

context(_: HasProject)
internal fun dependencies(configuration: DependencyHandlerScope.() -> Unit) =
    project.dependencies(configuration)

context(_: HasProject)
internal fun files(vararg paths: Any?) = project.files(*paths)

// TODO make it lazy? Fails with some decoding error at runtime otherwise
context(_: HasProject)
internal operator fun ConfigurableFileCollection.minusAssign(collection: FileCollection) =
    setFrom((files(from) - collection).toList())

internal val DevKitCompilation.java
    get() = javaSourceSet.java

internal fun lowerCamelCaseName(vararg nameParts: String?): String {
    val nonEmptyParts = nameParts.mapNotNull { it?.takeIf(String::isNotEmpty) }
    return nonEmptyParts
        .drop(1)
        .joinToString(
            separator = "",
            prefix = nonEmptyParts.firstOrNull().orEmpty(),
            transform = String::capitalizeAsciiOnly,
        )
}

private fun String.capitalizeAsciiOnly(): String {
    if (isEmpty()) return this
    val c = this[0]
    return if (c in 'a'..'z')
        buildString(length) {
            append(c.uppercaseChar())
            append(this@capitalizeAsciiOnly, 1, this@capitalizeAsciiOnly.length)
        }
    else this
}
