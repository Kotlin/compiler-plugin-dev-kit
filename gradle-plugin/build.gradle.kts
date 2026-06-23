plugins {
    id("gradle.plugin.config")
}

pluginConfig {
    compilerPlugin = project(":compiler-plugin")
    pluginAnnotations = project(":plugin-annotations")
}

gradlePlugin {
    plugins {
        create("SimplePlugin") {
            id = rootProject.group.toString()
            displayName = "SimplePlugin"
            description = "SimplePlugin"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.template.SimpleGradlePlugin"
        }
    }
}
