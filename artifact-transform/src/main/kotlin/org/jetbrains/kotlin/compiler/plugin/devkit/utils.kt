package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

public val KotlinToolingVersion.versionString: String
    get() = toString()

// From Metro
// https://github.com/ZacSweers/metro/blob/f4fb09ea6b6894aa7816f02d52398d65f9be404d/compiler-compat/src/main/kotlin/dev/zacsweers/metro/compiler/compat/KotlinToolingVersion.kt
internal val KotlinToolingVersion.isDev: Boolean
    get() = this.maturity == DEV

internal val KotlinToolingVersion.isIdeBuild: Boolean
    get() = classifier?.startsWith("ij", ignoreCase = true) == true

internal fun KotlinToolingVersion.hasSameBaseVersionAs(other: KotlinToolingVersion) =
    major == other.major && minor == other.minor && patch == other.patch

// Uses same convention as Metro
// https://github.com/ZacSweers/metro/tree/7e38de5535d66ea5491e155711fd9830c564924a/compiler-compat#version-naming-convention
public val KotlinToolingVersion.baseVersionName: String
    get() = versionString.replace(".", "").replace("-", "_").lowercase()
public val KotlinToolingVersion.versionName: String
    get() = "k$baseVersionName"

public fun DependencyHandler.registerMultiReleasePluginTransform(
    version: Provider<KotlinToolingVersion>
) {
    registerTransform(MultiReleasePluginTransform::class.java) {
        parameters { kotlinVersion.set(version.map(KotlinToolingVersion::versionString)) }
        from.attribute(KotlinCompilerVersion.attribute, noVersion)
        to.attributeProvider(KotlinCompilerVersion.attribute, version.map(::KotlinCompilerVersion))
    }
}

public fun DependencyHandler.setupCompilerVersionAttribute() {
    artifactTypes {
        all {
            attributes.attribute(KotlinCompilerVersion.attribute, noVersion)
        }
    }
}

public fun ModuleDependency.kotlinCompilerVersion(version: KotlinToolingVersion): ModuleDependency =
    attributes {
        attribute(KotlinCompilerVersion.attribute, KotlinCompilerVersion(version))
    }

public fun ModuleDependency.kotlinCompilerVersion(
    version: Provider<KotlinToolingVersion>
): ModuleDependency = attributes {
    attributeProvider(KotlinCompilerVersion.attribute, version.map(::KotlinCompilerVersion))
}

public fun KotlinCompilation<*>.requireCompilerVersionForPluginClasspath(
    version: Provider<KotlinToolingVersion>
) {
    project.configurations.named(
        lowerCamelCaseName(
            PLUGIN_CLASSPATH_CONFIGURATION_NAME,
            target.disambiguationClassifier,
            name,
        )
    ) {
        attributes.attributeProvider(
            KotlinCompilerVersion.attribute,
            version.map(::KotlinCompilerVersion),
        )
    }
}

private fun lowerCamelCaseName(vararg nameParts: String?): String {
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

private const val PLUGIN_CLASSPATH_CONFIGURATION_NAME = "kotlinCompilerPluginClasspath"
