plugins {
    `embedded-kotlin`
    `java-gradle-plugin`
}

dependencies {
    implementation(kotlin("gradle-plugin-api"))
    testImplementation(kotlin("test-junit5"))
}
