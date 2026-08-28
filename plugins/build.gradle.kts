import tapmoc.Severity

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version libs.versions.kotlin
    kotlin("plugin.sam.with.receiver") version libs.versions.kotlin
    kotlin("plugin.assignment") version libs.versions.kotlin
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.tapmoc)
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
    implementation(libs.buildconfig)
    implementation(libs.node.gradle)
    implementation(libs.animal.sniffer)
    implementation(libs.testkit.plugin)
    api("$group:version-resolution:$version")
}

gradlePlugin {
    plugins {
        create("compilerPlugin") {
            id = "$group.compiler-plugin"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerPlugin"
        }
        create("compilerLibrary") {
            id = "$group.compiler-library"
            implementationClass =
                "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerLibrary"
        }
        create("gradlePlugin") {
            id = "$group.gradle-plugin"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitGradlePlugin"
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
