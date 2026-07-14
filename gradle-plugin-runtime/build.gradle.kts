import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `embedded-kotlin`
    `java-gradle-plugin`
    `java-test-fixtures`
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

dependencies {
    implementation(kotlin("gradle-plugin-api"))

    testFixturesApi(gradleTestKit())
    testFixturesApi(kotlin("test-junit5"))
    testFixturesApi("junit:junit:4.13.2") // TODO don't force junit version
    testFixturesApi(libs.testkit.support)
    testFixturesApi(libs.testkit.truth)
    testFixturesApi("org.jetbrains.kotlin.compiler.plugin.devkit:artifact-transform")
}
