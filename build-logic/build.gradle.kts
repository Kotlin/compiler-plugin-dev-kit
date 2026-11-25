plugins { `kotlin-dsl` }

dependencies {
    implementation(libs.dokka)
    implementation(libs.maven.publish)
    implementation(libs.plugin.publish)
}
