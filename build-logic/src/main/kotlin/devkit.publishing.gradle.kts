plugins { id("com.vanniktech.maven.publish") }

val catalog = the<VersionCatalogsExtension>().named("libs")

group = catalog.findVersion("devkit-group").get().requiredVersion

version = catalog.findVersion("devkit-version").get().requiredVersion

val repositoryUrl = "https://github.com/Kotlin/compiler-plugin-dev-kit"
val projectDescription = provider { project.description }

mavenPublishing {
    pom {
        name = project.name
        description = projectDescription
        url = repositoryUrl
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
            url = repositoryUrl
            connection = "scm:git:$repositoryUrl.git"
        }
    }
}

// The `signing` plugin is applied only with a key. With that plugin applied,
// `com.gradle.plugin-publish` signs in every build, and a build that has no key fails.
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

// `plugins` and `version-resolution` are builds in subdirectories, so `rootDir` isn't always the
// root of the repository.
val repositoryRoot =
    generateSequence(rootDir) { it.parentFile }.first { it.resolve("build-logic").isDirectory }

publishing {
    repositories {
        maven {
            name = "devKitSpace"
            url = uri("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
            credentials(PasswordCredentials::class)
        }
        maven {
            name = "devKitLocal"
            url = uri(repositoryRoot.resolve("build/repo"))
        }
    }
}
