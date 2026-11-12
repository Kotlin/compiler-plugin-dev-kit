import tapmoc.Severity

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version libs.versions.kotlin
    kotlin("plugin.sam.with.receiver") version libs.versions.kotlin
    kotlin("plugin.assignment") version libs.versions.kotlin
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.tapmoc)
    alias(libs.plugins.testkit)
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

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
    website = "https://github.com/Kotlin/compiler-plugin-dev-kit"
    vcsUrl = "https://github.com/Kotlin/compiler-plugin-dev-kit.git"
    plugins {
        create("compilerPlugin") {
            id = "$group.compiler-plugin"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerPlugin"
            displayName = "Kotlin Compiler Plugin DevKit: compiler plugin"
            description =
                "Builds, tests, and publishes a Kotlin compiler plugin for many Kotlin compiler and IDE versions from one source tree."
            tags = listOf("kotlin", "compiler-plugin", "devkit")
        }
        create("compilerLibrary") {
            id = "$group.compiler-library"
            implementationClass =
                "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerLibrary"
            displayName = "Kotlin Compiler Plugin DevKit: compiler library"
            description =
                "Builds a library for Kotlin compiler plugins against many Kotlin compiler and IDE versions from one source tree."
            tags = listOf("kotlin", "compiler-plugin", "devkit")
        }
        create("gradlePlugin") {
            id = "$group.gradle-plugin"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitGradlePlugin"
            displayName = "Kotlin Compiler Plugin DevKit: Gradle support plugin"
            description =
                "Builds the Gradle support plugin of a Kotlin compiler plugin, and runs its functional tests against many Kotlin versions."
            tags = listOf("kotlin", "compiler-plugin", "devkit")
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

mavenPublishing {
    pom {
        name = "plugins"
        description = "The Gradle plugins of the Kotlin Compiler Plugin DevKit."
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
    checkDependencies(Severity.WARNING)
}
