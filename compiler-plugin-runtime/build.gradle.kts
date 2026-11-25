import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

plugins {
    kotlin("multiplatform")
    pluginDevKit("compiler-library")
    id("devkit.publishing")
    id("devkit.dokka")
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
        post("2.5.0-dev-5651")
        groupVersions("pre250Dev5651") {
            it.toKotlinVersion() >= KotlinVersion(2, 3) &&
                it < KotlinToolingVersion("2.5.0-dev-5651")
        }
        split("2.4.20-dev-2793")
        splitDev(2, 3)
    }
}

kotlin.sourceSets {
    named("pre250Dev5651Main") { dependsOn(getByName("post23DevMain")) }
    named("post250Dev5651Main") { dependsOn(getByName("post23DevMain")) }
}

// Dokka documents the API that the newest CLI version sees. The shared source sets get the compile
// classpath of that version, because their own metadata classpath has no compiler.
val documentedCompilation = provider {
    pluginDevKit.testAgainst.filter { !it.forIde }.maxBy { it.version }.mainCompilation
}

dokka.dokkaSourceSets.configureEach {
    suppress = documentedCompilation.map { c -> c.allKotlinSourceSets.none { it.name == name } }
    classpath.from(documentedCompilation.map { it.compileDependencyFiles })
}
