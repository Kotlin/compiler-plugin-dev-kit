import tapmoc.Severity

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("devkit.publishing")
    alias(libs.plugins.tapmoc)
    alias(libs.plugins.testkit)
}

description =
    "Picks a compatible Kotlin version, maps IDE build numbers to Kotlin versions, and parses the DevKit jar format."

dependencies {
    api(kotlin("tooling-core"))
    testImplementation(libs.truth)
}

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xcontext-sensitive-resolution")
    explicitApi()
}

tapmoc {
    gradle(libs.versions.gradle.get())
    java(8)
    checkDependencies(Severity.ERROR)
    checkKotlinStdlibs(Severity.ERROR)
}
