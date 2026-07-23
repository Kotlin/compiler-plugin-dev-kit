import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `embedded-kotlin`
    `java-gradle-plugin`
    `java-test-fixtures`
    kotlin("compiler.plugin.devkit.functional-test-publishing")
    id("com.vanniktech.maven.publish") version libs.versions.publish
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
    implementation(kotlin("gradle-plugin-api"))

    testFixturesApi(gradleTestKit())
    testFixturesApi(kotlin("test-junit5"))
    testFixturesApi("junit:junit:4.13.2") // TODO don't force junit version
    testFixturesApi(libs.testkit.support)
    testFixturesApi(libs.testkit.truth)
    testFixturesApi("org.jetbrains.kotlin.compiler.plugin.devkit:artifact-transform:0.0.1-SNAPSHOT")
}

publishing {
    repositories {
        maven {
            name = "devKitSpace"
            url = uri("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
            credentials(PasswordCredentials::class)
        }
    }
}
