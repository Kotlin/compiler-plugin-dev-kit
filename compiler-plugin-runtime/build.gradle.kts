import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    kotlin("jvm")
    kotlin("compiler.plugin.devkit.compiler-plugin")
    id("com.vanniktech.maven.publish") version libs.versions.publish
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

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
    api(pluginDevKit("artifact-transform"))
    testFixturesApi(pluginDevKit("artifact-transform"))
}

pluginDevKit {
    // This *is* compiler-plugin-runtime, so it can't depend on itself.
    addRuntimeDependency = false

    testAgainstWithFixtures("2.3.20")

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

    versions.forEach { testAgainst(it) }

    // lowest 2.4.20 dev version we support
    val k2420Target = testAgainstWithFixtures("2.4.20-dev-3583")
    testAgainst("2.4.20-Beta1") { sourceFixturesFrom(k2420Target) }
    // version where DUMP_CLASSIFIER was added
    val testDumpDirectiveTarget = testAgainstWithFixtures("2.4.20-dev-7885")
    testAgainst("2.4.20-Beta2") { sourceFixturesFrom(testDumpDirectiveTarget) }
}

tasks.generateTests { enabled = false }

publishing {
    repositories {
        maven {
            name = "devKitSpace"
            url = uri("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
            credentials(PasswordCredentials::class)
        }
    }
}
