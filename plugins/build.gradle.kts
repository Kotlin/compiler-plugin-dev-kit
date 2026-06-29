plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.jvm)
    implementation(libs.buildconfig)
    implementation(libs.node.gradle)
    implementation(libs.animal.sniffer)
}
