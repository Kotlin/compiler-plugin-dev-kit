import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

pluginDevKit {
    developForWithFixtures(kotlinToolingVersion)
    developForWithFixtures(KotlinToolingVersion(KotlinVersion(2, 4, 20), "dev-7680"))
}
