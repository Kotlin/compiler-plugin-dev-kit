plugins {
    kotlin("compiler.plugin.devkit.gradle-plugin")
}

pluginDevKit {
    pluginAnnotations = project(":plugin-annotations")
    compilerPlugin = project(":compiler-plugin")
}

gradlePlugin {
    plugins {
        create("SimplePlugin") {
            id = group.toString()
            displayName = "SimplePlugin"
            description = "SimplePlugin"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.template.SimpleGradlePlugin"
        }
    }
}
