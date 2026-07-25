import tapmoc.Severity

plugins {
    `java-gradle-plugin`
    kotlin("jvm")
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.tapmoc)
}

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
            id = "org.jetbrains.kotlin.compiler.plugin.devkit"
            implementationClass =
                "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSettingsPlugin"
        }
    }
}

kotlin { explicitApi() }

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
