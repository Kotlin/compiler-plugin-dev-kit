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
    versionHierarchy {
        split("2.5.0-dev-5651")
        split("2.4.20-dev-2793")
    }
}

tasks.generateTests { enabled = false }

tasks.generateTestsCli { enabled = false }

tasks.generateTestsIde { enabled = false }

publishing {
    repositories {
        maven {
            name = "devKitSpace"
            url = uri("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
            credentials(PasswordCredentials::class)
        }
    }
}
