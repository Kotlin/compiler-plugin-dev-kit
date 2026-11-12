import tapmoc.Severity

plugins {
    `java-gradle-plugin`
    kotlin("jvm")
    kotlin("plugin.sam.with.receiver")
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.tapmoc)
}

samWithReceiver { annotation("org.gradle.api.HasImplicitReceiver") }

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

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
    website = "https://github.com/Kotlin/compiler-plugin-dev-kit"
    vcsUrl = "https://github.com/Kotlin/compiler-plugin-dev-kit.git"
    plugins {
        create("settingsPlugin") {
            id = group.toString()
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSettingsPlugin"
            displayName = "Kotlin Compiler Plugin DevKit: settings plugin"
            description =
                "Adds the Kotlin dev, bootstrap, and IDE repositories to a Kotlin compiler plugin build, and has utilities for composite builds."
            tags = listOf("kotlin", "compiler-plugin", "devkit")
        }
        create("functionalTestPublishing") {
            id = "$group.functional-test-publishing"
            implementationClass =
                "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitFunctionalTestPublishingPlugin"
            displayName = "Kotlin Compiler Plugin DevKit: functional test publishing"
            description =
                "Applies com.autonomousapps.testkit without its default publication. The DevKit settings plugin applies it to every project."
            tags = listOf("kotlin", "compiler-plugin", "devkit")
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

mavenPublishing {
    pom {
        name = "settings"
        description = "The Gradle settings plugin of the Kotlin Compiler Plugin DevKit."
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
            url = uri(rootDir.resolve("../build/repo"))
        }
    }
}

tapmoc {
    gradle(libs.versions.gradle.get())
    checkDependencies(Severity.ERROR)
    checkKotlinStdlibs(Severity.ERROR)
}
