import org.gradle.kotlin.dsl.pluginConfig
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

pluginConfig {
    extraKotlinVersionsToTestAgainst += listOf(
        KotlinToolingVersion(KotlinVersion(2, 4), "Beta1"),
        //KotlinToolingVersion(KotlinVersion(2, 4, 20), "dev-6138"),
    )
}