package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

private val KotlinToolingVersion.capitalizedVersionName
    get() = "K$baseVersionName"

context(_: HasProject)
internal fun NamedDomainObjectProvider<out Configuration>.withKotlinVersion(
    version: KotlinToolingVersion,
    action: ResolvableConfiguration.() -> Unit = {},
) =
    configurations.resolvable("$name${version.capitalizedVersionName}") {
        extendsFrom(this@withKotlinVersion)
        resolutionStrategy.forceKotlinVersion(version)
        action()
    }

context(_: HasProject)
internal fun SourceSet.withCompilerVersionAttribute(version: KotlinToolingVersion) =
    configurations {
        named(compileClasspathConfigurationName) {
            attributes.attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                project.objects.named(LibraryElements.JAR),
            )
            attributes.attribute(
                KotlinCompilerVersion.attribute,
                KotlinCompilerVersion(version),
            )
        }
        named(runtimeClasspathConfigurationName) {
            attributes.attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                project.objects.named(LibraryElements.JAR),
            )
            attributes.attribute(
                KotlinCompilerVersion.attribute,
                KotlinCompilerVersion(version),
            )
        }
    }

internal fun ResolutionStrategy.forceKotlinVersion(version: KotlinToolingVersion) = eachDependency {
    if (requested.group == "org.jetbrains.kotlin") useVersion(version.versionString)
}

context(_: HasProject)
internal fun SourceSet.forceKotlinVersion(version: KotlinToolingVersion) {
    // TODO which of these are actually needed?
    configurations.named(implementationConfigurationName) {
        resolutionStrategy.forceKotlinVersion(version)
    }
    configurations.named(compileOnlyConfigurationName) {
        resolutionStrategy.forceKotlinVersion(version)
    }
    configurations.named(compileClasspathConfigurationName) {
        resolutionStrategy.forceKotlinVersion(version)
    }
    configurations.named(runtimeClasspathConfigurationName) {
        resolutionStrategy.forceKotlinVersion(version)
    }
    withCompilerVersionAttribute(version)
}

context(_: HasProject)
internal fun SourceSet.extendsFrom(
    otherSourceSet: SourceSet,
    extractName: (SourceSet) -> String,
) {
    configurations.named(extractName(this)) {
        extendsFrom(configurations.named(extractName(otherSourceSet)))
    }
}
