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
    test {
        java.setSrcDirs(listOf("test", pluginConfig.testGenDirectory))
        resources.setSrcDirs(listOf("testData"))
    }
}