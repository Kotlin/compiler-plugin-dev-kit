import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

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
        commonMain.dependencies { api(pluginDevKit("version-resolution")) }
        commonTestFixtures.dependencies { api(pluginDevKit("version-resolution")) }
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
        groupVersions("nonJvm", { true }) {
            groupVersions("pre2420Dev7600", { !supportsDumpClassifier(it) }) {
                preDev(2, 4, 20, "pre2420Dev")
            }

            postDev(2, 4, 20, "post2420Dev") {
                groupVersions("post2420Dev7600", ::supportsDumpClassifier)
            }
        }
    }
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
