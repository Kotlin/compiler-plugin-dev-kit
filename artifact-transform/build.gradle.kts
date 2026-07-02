import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `embedded-kotlin`
    embeddedKotlin("plugin.sam.with.receiver")
    embeddedKotlin("plugin.assignment")
    `java-gradle-plugin`
}

samWithReceiver { annotation("org.gradle.api.HasImplicitReceiver") }

assignment { annotation("org.gradle.api.SupportsKotlinAssignmentOverloading") }

group = "org.jetbrains.kotlin.compiler.plugin.devkit"

dependencies {
    implementation(gradleKotlinDsl())
    implementation(kotlin("gradle-plugin-api"))
    testImplementation(kotlin("test-junit5"))
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-sensitive-resolution")
        jvmTarget = JvmTarget.JVM_17
    }
    explicitApi()
}
