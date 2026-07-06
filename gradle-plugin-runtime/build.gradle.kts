import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `embedded-kotlin`
    `java-gradle-plugin`
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

dependencies {
    api("org.jetbrains.kotlin.compiler.plugin.devkit:artifact-transform")
    implementation(kotlin("gradle-plugin-api"))
    testImplementation(kotlin("test-junit5"))
}
