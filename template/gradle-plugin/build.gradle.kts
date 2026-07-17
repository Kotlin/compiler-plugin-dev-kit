plugins {
    kotlin("compiler.plugin.devkit.gradle-plugin")
}

pluginDevKit {
    extraLibrary(project(":plugin-annotations"))
    compilerPlugin = project(":compiler-plugin")
}

gradlePlugin {
    plugins {
        create("SimplePlugin") {
            id = group.toString()
            displayName = "SimplePlugin"
            description = "SimplePlugin"
            implementationClass =
                "org.jetbrains.kotlin.compiler.plugin.template.SimpleSupportPlugin"
        }
    }
}

gradleTestKitSupport {
    // These can go away once we're doing shadowing
    withIncludedBuildProjects(
        "compiler-plugin-dev-kit:compiler-plugin-runtime",
        "compiler-plugin-dev-kit:gradle-plugin-runtime",
        "artifact-transform:",
    )
}
