plugins {
    `embedded-kotlin`
    embeddedKotlin("plugin.sam.with.receiver")
    embeddedKotlin("plugin.assignment")
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish") version libs.versions.publish
}

group = "org.jetbrains.kotlin.compiler.plugin.devkit"

version = "0.0.1-SNAPSHOT"

samWithReceiver { annotation("org.gradle.api.HasImplicitReceiver") }

assignment { annotation("org.gradle.api.SupportsKotlinAssignmentOverloading") }

dependencies {
    implementation(gradleKotlinDsl())
    compileOnly(embeddedKotlin("gradle-plugin"))
    implementation(libs.buildconfig)
    implementation(libs.node.gradle)
    implementation(libs.animal.sniffer)
    implementation(libs.testkit)
    /*
    Even though this is optional, it cannot be made compileOnly
    else, any project that uses this as includedBuild will fail with
    NoClassDefFoundError
    */
    implementation(libs.gradleup.shadow)
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
        freeCompilerArgs.addAll("-Xcontext-sensitive-resolution", "-Xcontext-parameters")
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
