import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion

plugins {
    id("com.gradleup.shadow")
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

pluginDevKit {
    developForWithFixtures(kotlinToolingVersion)
    developForWithFixtures("2.4.20-dev-7680")
}

dependencies {
    defaultRuntimeLibraries(project(":plugin-annotations"))
}
