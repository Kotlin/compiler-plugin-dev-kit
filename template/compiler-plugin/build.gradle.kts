import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

pluginDevKit {
    developForWithFixtures(kotlinToolingVersion)
    developForWithFixtures(KotlinToolingVersion(KotlinVersion(2, 4, 20), "dev-7680"))
    pluginAnnotations = project(":plugin-annotations")
    developFor.configureEach {
        main {
            java.setSrcDirs(listOf("$versionName/src"))
            resources.setSrcDirs(listOf("$versionName/resources"))
        }
    }
    withFixtures.configureEach {
        testFixtures {
            java.setSrcDirs(listOf("$versionName/test-fixtures"))
        }
    }
    testAgainst.configureEach {
        testSuite {
            sources {
                java.setSrcDirs(
                    listOf("$versionName/test", pluginDevKit.testGenDirectory.dir(name))
                )
                resources.setSrcDirs(listOf("$versionName/testData"))
            }
        }
    }
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
        java.setSrcDirs(listOf("test", pluginDevKit.testGenDirectory.dir(name)))
        resources.setSrcDirs(listOf("testData"))
    }
}
