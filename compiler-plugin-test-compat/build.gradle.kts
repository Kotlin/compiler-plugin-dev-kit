plugins {
    kotlin("multiplatform")
    pluginDevKit("compiler-library")
    id("devkit.publishing")
    alias(libs.plugins.tapmoc)
}

description =
    "Test compatibility declarations for a Kotlin compiler plugin that supports many Kotlin versions."

tapmoc { java(8) }

kotlin {
    explicitApi()

    sourceSets.commonMain.dependencies {
        api(kotlin("compiler-internal-test-framework"))
    }
}

pluginDevKit {
    // Nothing here uses `compiler-plugin-runtime`, so the default dependency on it would be
    // unused.
    addRuntimeDependency = false

    // A trunk `dev` build has no for-IDE modules to resolve, unlike an `-ij` build, so the pinned
    // version is a CLI target.
    providers.gradleProperty("kotlin.compiler.plugin.devkit.pinnedDevVersion").orNull?.let {
        testAgainst(it, forIde = false)
    }

    versionHierarchy {
        splitDev(2, 4, 20, "NonGroupingStageTestConfigurationBuilder")
        preDev(2, 4, "preNonGroupingPhaseTestConfigurationBuilder")
    }
}
