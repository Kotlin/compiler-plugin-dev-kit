import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

plugins {
    kotlin("multiplatform")
    pluginDevKit("compiler-library")
    id("devkit.publishing")
    alias(libs.plugins.tapmoc)
}

description =
    "Utilities and entry point classes for a Kotlin compiler plugin that supports many Kotlin versions."

tapmoc { java(8) }

kotlin {
    explicitApi()
    sourceSets.commonMain.dependencies { api(pluginDevKit("version-resolution")) }
    sourceSets {
        entryPoint {
            dependencies { api(pluginDevKit("version-resolution")) }
            kotlin.srcDir("src/entryPoint/kotlin")
        }
    }
}

pluginDevKit {
    // This *is* compiler-plugin-runtime, so it can't depend on itself.
    addRuntimeDependency = false

    // A trunk `dev` build has no for-IDE modules to resolve, unlike an `-ij` build, so the pinned
    // version is a CLI target.
    providers.gradleProperty("kotlin.compiler.plugin.devkit.pinnedDevVersion").orNull?.let {
        testAgainst(it, forIde = false)
    }
    versionHierarchy {
        post("2.5.0-dev-5651")
        groupVersions("pre250Dev5651") {
            it.toKotlinVersion() >= KotlinVersion(2, 3) &&
                it < KotlinToolingVersion("2.5.0-dev-5651")
        }
        split("2.4.20-dev-2793")
        splitDev(2, 3, 20)
        splitDev(2, 3)
        groupVersions("preSourcelessDiagnostics") {
            it < KotlinToolingVersion("2.2.20-dev-6624") ||
                it == KotlinToolingVersion("2.2.20-Beta1")
        }
    }
}

kotlin.sourceSets {
    named("pre250Dev5651Main") { dependsOn(getByName("post23DevMain")) }
    named("post250Dev5651Main") { dependsOn(getByName("post23DevMain")) }
}
