import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

pluginConfig {
    kotlinVersionsToTestAgainst += listOf(KotlinToolingVersion(KotlinVersion(2, 4, 20), "dev-7680"))
}
