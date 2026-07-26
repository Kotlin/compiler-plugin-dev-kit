import tapmoc.Severity

plugins {
    `java-gradle-plugin`
    kotlin("jvm")
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.tapmoc)
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

dependencies {
    compileOnlyApi(libs.gradle.public.api) {
        capabilities {
            requireCapability("org.gradle.experimental:gradle-public-api-internal")
        }
    }
}

gradlePlugin {
    plugins {
        create("settingsPlugin") {
            id = group.toString()
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSettingsPlugin"
        }
    }
}

kotlin { explicitApi() }

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
    checkKotlinStdlibs(Severity.ERROR)
}
