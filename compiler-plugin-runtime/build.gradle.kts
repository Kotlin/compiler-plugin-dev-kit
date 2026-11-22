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
    sourceSets.commonMain.dependencies {
        api(pluginDevKit("version-resolution"))
        api(project(":compiler-plugin-runtime-compat"))
    }
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

    providers.gradleProperty("kotlin.compiler.plugin.devkit.pinnedDevVersion").orNull?.let {
        testAgainst(it, forIde = false)
    }
    versionHierarchy {
        post("2.5.0-dev-5651", "postFakeElementMember")
        groupVersions("preFakeElementMember") {
            it.toKotlinVersion() >= KotlinVersion(2, 3) &&
                it < KotlinToolingVersion("2.5.0-dev-5651")
        }
        split("2.4.20-dev-2793", "PluginGeneratedCustom")
        splitDev(2, 3, "KtSourceElementOffsetStrategy")
    }
}

kotlin.sourceSets {
    named("preFakeElementMemberMain") {
        dependsOn(getByName("postKtSourceElementOffsetStrategyMain"))
    }
    named("postFakeElementMemberMain") {
        dependsOn(getByName("postKtSourceElementOffsetStrategyMain"))
    }
}
