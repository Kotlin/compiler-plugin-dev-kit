import tapmoc.Severity

plugins {
    `java-gradle-plugin`
    kotlin("jvm")
    kotlin("plugin.sam.with.receiver")
    alias(libs.plugins.buildconfig)
    id("devkit.plugin-publishing")
    alias(libs.plugins.tapmoc)
}

samWithReceiver { annotation("org.gradle.api.HasImplicitReceiver") }

description = "The Gradle settings plugin of the Kotlin Compiler Plugin DevKit."

dependencies {
    compileOnly(libs.gradle.public.api) {
        capabilities {
            requireCapability("org.gradle.experimental:gradle-public-api-internal")
        }
    }
    compileOnly(kotlin("gradle-plugin"))
    implementation(libs.testkit.plugin)
    api(kotlin("tooling-core"))
}

gradlePlugin {
    plugins {
        create("settingsPlugin") {
            id = group.toString()
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSettingsPlugin"
            displayName = "Kotlin Compiler Plugin DevKit: settings plugin"
            description =
                "Adds the Kotlin dev, bootstrap, and IDE repositories to a Kotlin compiler plugin build, and has utilities for composite builds."
        }
        create("functionalTestPublishing") {
            id = "$group.functional-test-publishing"
            implementationClass =
                "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitFunctionalTestPublishingPlugin"
            displayName = "Kotlin Compiler Plugin DevKit: functional test publishing"
            description =
                "Applies com.autonomousapps.testkit without its default publication. The DevKit settings plugin applies it to every project."
        }
    }
}

kotlin {
    explicitApi()
}

buildConfig {
    packageName(group.toString())
    className("DevKitSettingsCoordinates")
    useKotlinOutput {
        internalVisibility = true
        topLevelConstants = true
    }
    buildConfigField("DEV_KIT_GROUP", group.toString())
    buildConfigField("DEV_KIT_VERSION", version.toString())
}

tapmoc {
    gradle(libs.versions.gradle.get())
    checkDependencies(Severity.ERROR)
    checkKotlinStdlibs(Severity.ERROR)
}
