plugins {
    kotlin("compiler.plugin.devkit.gradle-plugin")
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
