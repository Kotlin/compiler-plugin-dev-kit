import tapmoc.Severity

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.tapmoc)
    alias(libs.plugins.testkit)
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

dependencies {
    api(kotlin("tooling-core"))
    testImplementation(libs.truth)
}

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xcontext-sensitive-resolution")
    explicitApi()
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
    java(8)
    checkDependencies(Severity.ERROR)
    checkKotlinStdlibs(Severity.ERROR)
}
