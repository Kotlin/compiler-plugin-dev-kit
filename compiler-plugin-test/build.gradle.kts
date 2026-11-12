import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilation
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.sources.AbstractKotlinSourceSet
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

plugins {
    kotlin("multiplatform")
    pluginDevKit("compiler-library")
    id("com.vanniktech.maven.publish") version libs.versions.publish
    alias(libs.plugins.tapmoc)
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

tapmoc { java(8) }

kotlin {
    explicitApi()

    sourceSets.commonMain.dependencies {
        api(pluginDevKit("version-resolution"))
        api(project(":compiler-plugin-runtime"))
        api(kotlin("test-junit5"))
        api(kotlin("compiler-internal-test-framework"))
        api(kotlin("compiler"))
        implementation(libs.diff.utils)
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
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
        split("2.5.20-dev-3434")
        split("250Dev8142") {
            it < KotlinToolingVersion("2.5.0-dev-8142") || it == KotlinToolingVersion("2.5.0-Beta1")
        }
        split("2.5.0-dev-5676")
        split("2420Dev7600") {
            it < KotlinToolingVersion("2.4.20-dev-7600") ||
                it == KotlinToolingVersion("2.4.20-Beta1")
        }
        splitDev(2, 4, 20)
        splitDev(2, 4)
        splitDev(2, 3, 20)
        groupVersionsCli("pre2320DevJs") {
            it.toKotlinVersion() >= KotlinVersion(2, 3) &&
                it.toKotlinVersion() < KotlinVersion(2, 3, 20)
        }
        groupVersionsCli("post2320DevJs") { it.toKotlinVersion() >= KotlinVersion(2, 3, 20) }
        splitDev(2, 3)
        split("2.2.20-RC")
    }
}

fun KotlinSourceSet.targetsMatch(condition: (DevKitTarget) -> Boolean) = compilationsMatch {
    condition(it.target)
}

fun KotlinSourceSet.compilationsMatch(condition: (DevKitCompilation) -> Boolean) =
    (this as AbstractKotlinSourceSet).compilations.all { it is DevKitCompilation && condition(it) }

kotlin.sourceSets {
    val jsMain = create("jsMain")
    jsMain.dependsOn(getByName("cliMain"))

    // TODO we could do this wiring automatically, but it'd look ugly in the "actual chooser" in IDE
    configureEach {
        if (!compilationsMatch { it.name == KotlinSourceSetTree.main.name }) return@configureEach
        if (this == jsMain) return@configureEach
        // stops the "redundant dependsOn" warning; has no other purpose.
        if (pluginDevKit.testAgainst.any { it.mainCompilation.defaultSourceSet == this })
            return@configureEach

        // This exists so that the `typealias` trick for `TestConfigurationBuilder` works within
        // this project
        if (targetsMatch { it.version.toKotlinVersion() < KotlinVersion(2, 4) }) {
            dependsOn(getByName("pre24DevMain"))
            dependsOn(getByName("pre2420DevMain"))
        }

        if (targetsMatch { !it.forIde && it.version.toKotlinVersion() >= KotlinVersion(2, 3) }) {
            dependsOn(getByName("jsMain"))
        }
    }
}

publishing {
    repositories {
        maven {
            name = "devKitSpace"
            url = uri("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
            credentials(PasswordCredentials::class)
        }
        maven {
            name = "devKitLocal"
            url = uri(rootDir.resolve("build/repo"))
        }
    }
}
