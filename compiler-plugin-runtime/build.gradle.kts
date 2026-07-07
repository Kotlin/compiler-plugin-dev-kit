import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

pluginDevKit {
    // This *is* compiler-plugin-runtime, so it can't depend on itself.
    addRuntimeDependency = false

    developForWithFixtures(kotlinToolingVersion)
    testAgainst(KotlinToolingVersion("2.4.20-dev-7680"))

    developForWithFixtures(KotlinToolingVersion("2.4.0"))
    val k2320Target = developForWithFixtures(KotlinToolingVersion("2.3.20"))

    // 2.4.0-dev-2124 predates the TestConfigurationBuilder rename (like 2.3.0/2.3.20/2.4.0), so
    // reuse 2320's sources rather than a dedicated directory (240 doesn't fit:
    // its KotlinStandardLibrariesPathProvider impl assumes wasm stdlib APIs this dev build lacks.)
    developForWithFixtures(KotlinToolingVersion("2.4.0-dev-2124")) {
        testFixtures {
            java.srcDirs(k2320Target.testFixtures.map { it.java.sourceDirectories })
            resources.srcDirs(k2320Target.testFixtures.map { it.resources.srcDirs })
        }
    }
    val k2420Target = developForWithFixtures(KotlinToolingVersion("2.4.20-dev-6138"))

    val versions =
        listOf(
                "2.3.10",
                "2.3.20",
                "2.3.21",
                "2.4.0-dev-2124",
                "2.4.0",
                "2.4.10-RC",
                "2.4.20-dev-3583",
                "2.4.20-dev-6138",
                "2.4.20-Beta1",
            )
            .map(::KotlinToolingVersion)

    versions
        .filterNot { version -> testAgainst.any { it.version == version } }
        .forEach {
            if (it.toKotlinVersion() == KotlinVersion(2, 4, 20))
                developForWithFixtures(it) {
                    testFixtures {
                        java.srcDirs(k2420Target.testFixtures.map { it.java.sourceDirectories })
                        resources.srcDirs(k2420Target.testFixtures.map { it.resources.srcDirs })
                    }
                }
            else testAgainst(it)
        }
}

tasks.generateTests { enabled = false }
