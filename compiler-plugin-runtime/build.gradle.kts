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
        splitDev(2, 3)
    }
}

kotlin.sourceSets {
    named("pre250Dev5651Main") { dependsOn(getByName("post23DevMain")) }
    named("post250Dev5651Main") { dependsOn(getByName("post23DevMain")) }
}

mavenPublishing {
    pom {
        name = "compiler-plugin-runtime"
        description =
            "Utilities and entry point classes for a Kotlin compiler plugin that supports many Kotlin versions."
        url = "https://github.com/Kotlin/compiler-plugin-dev-kit"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "JetBrains"
                name = "JetBrains Team"
                organization = "JetBrains"
                organizationUrl = "https://www.jetbrains.com"
            }
        }
        scm {
            url = "https://github.com/Kotlin/compiler-plugin-dev-kit"
            connection = "scm:git:https://github.com/Kotlin/compiler-plugin-dev-kit.git"
        }
    }
}

providers.gradleProperty("libs.sign.key.private").orNull?.let { key ->
    mavenPublishing { signAllPublications() }
    configure<SigningExtension> {
        useInMemoryPgpKeys(
            providers.gradleProperty("libs.sign.key.id").orNull,
            key,
            providers.gradleProperty("libs.sign.passphrase").orNull,
        )
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
