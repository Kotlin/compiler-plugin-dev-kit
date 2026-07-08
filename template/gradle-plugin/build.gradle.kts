plugins {
    kotlin("compiler.plugin.devkit.gradle-plugin")
}

pluginDevKit {
    pluginAnnotations = project(":plugin-annotations")
    compilerPlugin = project(":compiler-plugin")
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    test {
        java.setSrcDirs(listOf("test"))
        resources.setSrcDirs(listOf("testResources"))
    }
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
