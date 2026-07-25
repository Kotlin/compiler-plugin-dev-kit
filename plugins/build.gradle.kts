import tapmoc.Severity

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version libs.versions.kotlin
    kotlin("plugin.sam.with.receiver") version libs.versions.kotlin
    kotlin("plugin.assignment") version libs.versions.kotlin
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.tapmoc)
}

samWithReceiver { annotation("org.gradle.api.HasImplicitReceiver") }

assignment { annotation("org.gradle.api.SupportsKotlinAssignmentOverloading") }

dependencies {
    compileOnlyApi(libs.gradle.public.api) {
        capabilities {
            requireCapability("org.gradle.experimental:gradle-public-api-internal")
        }
    }
    compileOnly(gradleKotlinDsl())

    compileOnly(kotlin("gradle-plugin"))
    implementation(libs.buildconfig)
    implementation(libs.node.gradle)
    implementation(libs.animal.sniffer)
    implementation(libs.testkit.plugin)
    compileOnly(libs.gradleup.shadow)
    api("org.jetbrains.kotlin.compiler.plugin.devkit:artifact-transform:0.0.1-SNAPSHOT")
}

gradlePlugin {
    plugins {
        create("compilerPlugin") {
            id = "org.jetbrains.kotlin.compiler.plugin.devkit.compiler-plugin"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerPlugin"
        }
        create("gradlePlugin") {
            id = "org.jetbrains.kotlin.compiler.plugin.devkit.gradle-plugin"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitGradlePlugin"
        }
        create("functionalTestPublishing") {
            id = "org.jetbrains.kotlin.compiler.plugin.devkit.functional-test-publishing"
            implementationClass =
                "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitFunctionalTestPublishingPlugin"
        }
    }
}

kotlin {
    explicitApi()
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-sensitive-resolution",
            "-Xcontext-parameters",
            "-Xallow-contracts-on-more-functions",
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
    }
}

tapmoc {
    gradle(libs.versions.gradle.get())
    checkDependencies(Severity.ERROR)
}
