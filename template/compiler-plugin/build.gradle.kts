import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

pluginDevKit {
    developForWithFixtures(kotlinToolingVersion)
    developForWithFixtures(KotlinToolingVersion(KotlinVersion(2, 4, 20), "dev-7680"))
    pluginAnnotations = project(":plugin-annotations")
}
