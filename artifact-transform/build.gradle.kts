import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `embedded-kotlin`
    `java-gradle-plugin`
}

group = "org.jetbrains.kotlin.compiler.plugin.devkit"

dependencies {
    implementation(kotlin("gradle-plugin-api"))
    testImplementation(kotlin("test-junit5"))
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}
