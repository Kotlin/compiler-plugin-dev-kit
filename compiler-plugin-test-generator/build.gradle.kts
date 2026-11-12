import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    kotlin("jvm")
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.tapmoc)
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

tapmoc { java(8) }

kotlin { explicitApi() }

// The `generateTests*` compilations run the generator at `KnownVersions.kotlinVersions.last()`, so
// this project compiles it against the test framework of that version.
// `fileContents` keeps the file a configuration cache input.
val kotlinVersionsFile = objects.fileProperty()

kotlinVersionsFile.set(
    rootDir.resolve(
        "plugins/src/main/resources/META-INF/org/jetbrains/kotlin/compiler/plugin/devkit/kotlin-versions.txt"
    )
)

val generatorKotlinVersion =
    providers
        .fileContents(kotlinVersionsFile.get())
        .asText
        .map { text ->
            text
                .lineSequence()
                .map(String::trim)
                .filterNot { it.isEmpty() || it.startsWith("#") }
                .maxOf { KotlinToolingVersion(it) }
                .toString()
        }
        .get()

dependencies {
    api(kotlin("compiler-internal-test-framework", generatorKotlinVersion))
    api(kotlin("compiler", generatorKotlinVersion))
}

mavenPublishing {
    pom {
        name = "compiler-plugin-test-generator"
        description = "The test generator API of the Kotlin Compiler Plugin DevKit."
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
