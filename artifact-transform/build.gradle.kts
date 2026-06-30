plugins {
    `embedded-kotlin`
    `java-gradle-plugin`
}

group = "org.jetbrains.kotlin.compiler.plugin.devkit"

dependencies {
    implementation(kotlin("gradle-plugin-api"))
    testImplementation(kotlin("test-junit5"))
}