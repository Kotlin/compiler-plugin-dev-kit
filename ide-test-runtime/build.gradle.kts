import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    kotlin("jvm")
    alias(libs.plugins.maven.publish)
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

kotlin {
    explicitApi()
    compilerOptions { jvmTarget = JvmTarget.JVM_21 }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // The IDE test project is described exactly like a functional test's.
    api(testFixtures(project(":gradle-plugin-runtime")))
    api(libs.junit.jupiter)

    implementation(libs.ide.starter.squashed)
    implementation(libs.ide.starter.junit5)
    implementation(libs.ide.starter.driver)
    implementation(libs.ide.driver.client)
    implementation(libs.ide.driver.sdk)
    implementation(libs.ide.driver.model)
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
