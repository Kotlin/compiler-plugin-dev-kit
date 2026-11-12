import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import tapmoc.Severity

plugins {
    `java-library`
    `java-test-fixtures`
    kotlin("jvm")
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.tapmoc)
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

val minGradleVersion = libs.versions.gradle.get()
val minKotlinVersion = tapmoc.kotlinVersionForGradle(minGradleVersion)

kotlin {
    explicitApi()
    target.compilations.named("testFixtures") {
        tasks.named<KotlinCompile>(compileKotlinTaskName) {
            explicitApiMode = ExplicitApiMode.Strict
        }
    }
}

configurations.testFixturesApi {
    // KGP pins this to its own version, ignoring `coreLibrariesVersion`
    withDependencies {
        removeIf { it.group == "org.jetbrains.kotlin" && it.name == "kotlin-stdlib" }
    }
}

dependencies {
    compileOnly(libs.gradle.public.api) {
        capabilities { requireCapability("org.gradle.experimental:gradle-public-api-internal") }
    }
    compileOnly(kotlin("gradle-plugin-api"))

    testFixturesApi(gradleTestKit())
    testFixturesApi(kotlin("test-junit5", minKotlinVersion))
    testFixturesApi("junit:junit:4.13.2") // TODO don't force junit version
    testFixturesApi(libs.testkit.support)
    testFixturesApi(libs.testkit.truth)
    testFixturesApi(pluginDevKit("version-resolution"))
}

mavenPublishing {
    pom {
        name = "gradle-plugin-runtime"
        description =
            "Utilities for the Gradle support plugin of a Kotlin compiler plugin, and test fixtures for its functional tests."
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

tapmoc {
    gradle(minGradleVersion)
    checkDependencies(Severity.WARNING) // gradleTestKit sadly forces the local version
    checkKotlinStdlibs(Severity.WARNING)
}
