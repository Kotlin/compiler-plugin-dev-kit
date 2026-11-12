plugins {
    id("devkit.publishing")
    id("com.gradle.plugin-publish")
}

gradlePlugin {
    website = "https://github.com/Kotlin/compiler-plugin-dev-kit"
    vcsUrl = "https://github.com/Kotlin/compiler-plugin-dev-kit.git"
    plugins.configureEach { tags = listOf("kotlin", "compiler-plugin", "devkit") }
}
