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
