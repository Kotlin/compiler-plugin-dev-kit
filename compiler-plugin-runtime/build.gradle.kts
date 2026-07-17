import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
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
    explicitApi()
    target.compilations.named("testFixtures") {
        tasks.named<KotlinCompile>(compileKotlinTaskName) {
            explicitApiMode = ExplicitApiMode.Strict
        }
    }
}

dependencies {
    api("org.jetbrains.kotlin.compiler.plugin.devkit:artifact-transform:0.0.1-SNAPSHOT")
}

pluginDevKit {
    // This *is* compiler-plugin-runtime, so it can't depend on itself.
    addRuntimeDependency = false

    testAgainst(kotlinToolingVersion)
    developForWithFixtures(KotlinToolingVersion("2.4.20-dev-7680"))

    developForWithFixtures(KotlinToolingVersion("2.4.0"))
    val k2320Target = developForWithFixtures(KotlinToolingVersion("2.3.20"))

    // 2.4.0-dev-2124 predates the TestConfigurationBuilder rename (like 2.3.0/2.3.20/2.4.0), so
    // reuse 2320's sources rather than a dedicated directory (240 doesn't fit:
    // its KotlinStandardLibrariesPathProvider impl assumes wasm stdlib APIs this dev build lacks.)
    developForWithFixtures(KotlinToolingVersion("2.4.0-dev-2124")) {
        testFixtures {
            kotlin.srcDirs(k2320Target.testFixtures.map { it.kotlin.sourceDirectories })
            java.srcDirs(k2320Target.testFixtures.map { it.java.sourceDirectories })
            resources.srcDirs(k2320Target.testFixtures.map { it.resources.sourceDirectories })
        }
    }
    val k2420Target = developForWithFixtures(KotlinToolingVersion("2.4.20-dev-6138"))

    val versions =
        listOf(
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
                        kotlin.srcDirs(k2420Target.testFixtures.map { it.kotlin.sourceDirectories })
                        java.srcDirs(k2420Target.testFixtures.map { it.java.sourceDirectories })
                        resources.srcDirs(
                            k2420Target.testFixtures.map { it.resources.sourceDirectories }
                        )
                    }
                }
            else testAgainst(it)
        }
}

tasks.generateTests { enabled = false }
