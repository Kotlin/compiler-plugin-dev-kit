plugins {
    `embedded-kotlin`
    `java-gradle-plugin`
}

dependencies {
    api("org.jetbrains.kotlin.compiler.plugin.devkit:artifact-transform")
    implementation(kotlin("gradle-plugin-api"))
    testImplementation(kotlin("test-junit5"))
}
