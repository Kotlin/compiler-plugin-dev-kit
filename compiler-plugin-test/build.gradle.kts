import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
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
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
pluginDevKit {
    // This *is* compiler-plugin-runtime, so it can't depend on itself.
    addRuntimeDependency = false

    fun supportsDumpClassifier(version: KotlinToolingVersion) =
        version >= KotlinToolingVersion("2.4.20-dev-7600") &&
            version != KotlinToolingVersion("2.4.20-Beta1")

    kotlin.applyPluginDevKitHierarchyTemplate {
        groupVersions("pre2420Dev7600", { !supportsDumpClassifier(it) }) {
            preDev(2, 4, 20, "pre2420Dev") {
                preDev(2, 4, "pre24Dev") {
                    preDev(2, 3, 20, "pre2320Dev")
                }
            }
        }

        postDev(2, 3, 20, "post2320Dev") {
            postDev(2, 4, "post24Dev") {
                postDev(2, 4, 20, "post2420Dev") {
                    groupVersions("post2420Dev7600", ::supportsDumpClassifier)
                }
            }
        }
    }
}

tasks.generateTests { enabled = false }

publishing {
    repositories {
        maven {
            name = "devKitSpace"
            url = uri("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
            credentials(PasswordCredentials::class)
        }
    }
}
