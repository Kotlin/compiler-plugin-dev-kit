import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    kotlin("multiplatform")
    pluginDevKit("compiler-library")
    id("com.vanniktech.maven.publish") version libs.versions.publish
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

java {
    targetCompatibility = JavaVersion.VERSION_17
}

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
        split("2.5.0-dev-5676")
        split("2420Dev7600") {
            it < KotlinToolingVersion("2.4.20-dev-7600") ||
                it == KotlinToolingVersion("2.4.20-Beta1")
        }
        splitDev(2, 4, 20)
        splitDev(2, 4)
    }
}

publishing {
    repositories {
        maven {
            name = "devKitSpace"
            url = uri("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
            credentials(PasswordCredentials::class)
        }
    }
}
