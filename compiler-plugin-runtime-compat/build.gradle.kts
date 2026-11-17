import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    kotlin("multiplatform")
    pluginDevKit("compiler-library")
    id("devkit.publishing")
    alias(libs.plugins.tapmoc)
}

description =
    "Compatiblity declarations for a Kotlin compiler plugin that supports many Kotlin versions."

tapmoc { java(8) }

kotlin { explicitApi() }

pluginDevKit {
    // `compiler-plugin-runtime` depends on this library, so the default dependency on it would be
    // a cycle.
    addRuntimeDependency = false

    providers.gradleProperty("kotlin.compiler.plugin.devkit.pinnedDevVersion").orNull?.let {
        testAgainst(it, forIde = false)
    }
    versionHierarchy {
        splitDev(2, 3, 20)
        splitDev(2, 3)
        groupVersions("preSourcelessDiagnostics") {
            it < KotlinToolingVersion("2.2.20-dev-6624") ||
                it == KotlinToolingVersion("2.2.20-Beta1")
        }
    }
}
