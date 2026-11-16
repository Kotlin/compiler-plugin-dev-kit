import tapmoc.Severity

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version libs.versions.kotlin
    kotlin("plugin.sam.with.receiver") version libs.versions.kotlin
    kotlin("plugin.assignment") version libs.versions.kotlin
    alias(libs.plugins.buildconfig)
    id("devkit.plugin-publishing")
    alias(libs.plugins.tapmoc)
    alias(libs.plugins.testkit)
}

description = "The Gradle plugins of the Kotlin Compiler Plugin DevKit."

samWithReceiver { annotation("org.gradle.api.HasImplicitReceiver") }

assignment { annotation("org.gradle.api.SupportsKotlinAssignmentOverloading") }

dependencies {
    compileOnly(libs.gradle.public.api) {
        capabilities {
            requireCapability("org.gradle.experimental:gradle-public-api-internal")
        }
    }
    compileOnly(gradleKotlinDsl())

    compileOnly(kotlin("gradle-plugin"))
    compileOnly(kotlin("gradle-plugin-idea"))
    implementation(libs.buildconfig)
    implementation(libs.node.gradle)
    implementation(libs.animal.sniffer)
    implementation(libs.testkit.plugin)
    api("$group:version-resolution:$version")

    testImplementation(kotlin("test"))

    functionalTestImplementation(kotlin("test-junit"))
}

gradleTestKitSupport {
    withSupportLibrary()
    withIncludedBuildProjects("version-resolution:")
}

gradlePlugin {
    plugins {
        create("compilerPlugin") {
            id = "$group.compiler-plugin"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerPlugin"
            displayName = "Kotlin Compiler Plugin DevKit: compiler plugin"
            description =
                "Builds, tests, and publishes a Kotlin compiler plugin for many Kotlin compiler and IDE versions from one source tree."
        }
        create("compilerLibrary") {
            id = "$group.compiler-library"
            implementationClass =
                "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerLibrary"
            displayName = "Kotlin Compiler Plugin DevKit: compiler library"
            description =
                "Builds a library for Kotlin compiler plugins against many Kotlin compiler and IDE versions from one source tree."
        }
        create("gradlePlugin") {
            id = "$group.gradle-plugin"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitGradlePlugin"
            displayName = "Kotlin Compiler Plugin DevKit: Gradle support plugin"
            description =
                "Builds the Gradle support plugin of a Kotlin compiler plugin, and runs its functional tests against many Kotlin versions."
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

// Not the default `BuildConfig`: artifact-transform already generates one into this same package.
buildConfig {
    packageName(group.toString())
    className("DevKitCoordinates")
    useKotlinOutput {
        internalVisibility = true
        topLevelConstants = true
    }
    buildConfigField("DEV_KIT_GROUP", group.toString())
    buildConfigField("DEV_KIT_VERSION", version.toString())
}

tapmoc {
    gradle(libs.versions.gradle.get())
    checkDependencies(Severity.WARNING)
}
