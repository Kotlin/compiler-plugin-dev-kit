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

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    testFixtures {
        java.setSrcDirs(listOf("test-fixtures"))
    }
    test { shortenTestDirs() }
    // TODO some programmatic way to identify these version source sets
    named("k240_beta1") {
        java.setSrcDirs(listOf(name))
        resources.setSrcDirs(listOf("${name}_resources"))
    }

    named("k240_beta1Test") { shortenTestDirs() }
}

fun SourceSet.shortenTestDirs() {
    java.setSrcDirs(listOf(name, pluginConfig.testGenDirectory.dir(name)))
    resources.setSrcDirs(listOf("${name}Data"))
}