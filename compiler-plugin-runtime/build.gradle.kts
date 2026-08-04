import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    kotlin("compiler.plugin.devkit.compiler-plugin")
    id("com.vanniktech.maven.publish") version libs.versions.publish
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

java {
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    jvm {
        compilerOptions.jvmTarget = JvmTarget.JVM_17
        compilations.named("testFixtures") {
            tasks.named<KotlinCompile>(compileKotlinTaskName) {
                explicitApiMode = ExplicitApiMode.Strict
            }
        }
    }

    sourceSets {
        jvmMain.dependencies { api(pluginDevKit("version-resolution")) }
        jvmTestFixtures.dependencies { api(pluginDevKit("version-resolution")) }
    }
}

pluginDevKit {
    // This *is* compiler-plugin-runtime, so it can't depend on itself.
    addRuntimeDependency = false

    // Plain versions live in gradle.properties; only the ones needing fixtures are named here.
    testAgainstWithFixtures("2.3.20")

    // lowest 2.4.20 dev version we support
    val k2420Target = testAgainstWithFixtures("2.4.20-dev-3583")
    testAgainst("2.4.20-Beta1") { sourceFixturesFrom(k2420Target) }
    // version where DUMP_CLASSIFIER was added
    val testDumpDirectiveTarget = testAgainstWithFixtures("2.4.20-dev-7885")
    testAgainst("2.4.20-Beta2") { sourceFixturesFrom(testDumpDirectiveTarget) }
}

tasks.generateTests { enabled = false }

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin.publishing.adhocSoftwareComponent {
    val testFixtures = java.sourceSets.testFixtures.get()
    this.addVariantsFromConfiguration(
        configurations.getByName(testFixtures.apiElementsConfigurationName),
        JavaConfigurationVariantMapping(
            "compile",
            true,
            configurations.getByName(testFixtures.compileClasspathConfigurationName),
        ),
    )
    this.addVariantsFromConfiguration(
        configurations.getByName(testFixtures.runtimeElementsConfigurationName),
        JavaConfigurationVariantMapping(
            "runtime",
            true,
            configurations.getByName(testFixtures.runtimeClasspathConfigurationName),
        ),
    )
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
