import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

pluginConfig {
    kotlinVersionsToTestAgainst +=
        listOf(KotlinToolingVersion(KotlinVersion(2, 4, 20), "dev-7680"))
}
