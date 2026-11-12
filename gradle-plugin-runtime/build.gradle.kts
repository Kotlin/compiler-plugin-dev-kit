import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import tapmoc.Severity

plugins {
    `java-library`
    `java-test-fixtures`
    kotlin("jvm")
    id("devkit.publishing")
    alias(libs.plugins.tapmoc)
}

description =
    "Utilities for the Gradle support plugin of a Kotlin compiler plugin, and test fixtures for its functional tests."

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

tapmoc {
    gradle(minGradleVersion)
    checkDependencies(Severity.WARNING) // gradleTestKit sadly forces the local version
    checkKotlinStdlibs(Severity.WARNING)
}
