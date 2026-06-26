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

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    testFixtures {
        java.setSrcDirs(listOf("test-fixtures"))
    }
    test {
        java.setSrcDirs(listOf("test", pluginConfig.testGenDirectory.dir(name)))
        resources.setSrcDirs(listOf("testData"))
    }

    all {
        if (name.endsWith("Test")) return@all
        if (this in listOf(main, testFixtures, test).map { it.get() }) return@all
        java.setSrcDirs(listOf("$name/src"))
        resources.setSrcDirs(listOf("${name}/resources"))
    }
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        withType<JvmTestSuite> {
            sources {
                if (this == sourceSets.test.get()) return@sources
                val folder = name.removeSuffix("Test")
                java.setSrcDirs(listOf("$folder/test", pluginConfig.testGenDirectory.dir(name)))
                resources.setSrcDirs(listOf("$folder/testData"))
            }
        }
    }
}