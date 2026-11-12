import tapmoc.Severity

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.tapmoc)
    alias(libs.plugins.testkit)
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

dependencies {
    api(kotlin("tooling-core"))
    testImplementation(libs.truth)
}

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xcontext-sensitive-resolution")
    explicitApi()
}

mavenPublishing {
    pom {
        name = "version-resolution"
        description =
            "Picks a compatible Kotlin version, maps IDE build numbers to Kotlin versions, and parses the DevKit jar format."
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
            url = uri(rootDir.resolve("../build/repo"))
        }
    }
}

tapmoc {
    gradle(libs.versions.gradle.get())
    java(8)
    checkDependencies(Severity.ERROR)
    checkKotlinStdlibs(Severity.ERROR)
}
