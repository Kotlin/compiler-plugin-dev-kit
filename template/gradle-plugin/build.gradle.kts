plugins {
    kotlin("compiler.plugin.devkit.gradle-plugin")
}

pluginDevKit {
    extraLibrary(project(":plugin-annotations"))
    compilerPlugin = project(":compiler-plugin")
}

val testKitProjectsClasspath = configurations.dependencyScope("testKitProjectsClasspath")

dependencies {
    // TODO configure automatically
    testKitProjectsClasspath(project(":plugin-annotations"))
    testKitProjectsClasspath(project(":compiler-plugin"))
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
    // TODO this definitely violates Isolated Projects. DevKit should instead
    //  publish needed dependencies into functionalTestRepo itself
    withClasspaths(testKitProjectsClasspath.name)
    // This can go away once we're shadowing gradle plugins
    withIncludedBuildProjects("compiler-plugin-dev-kit:gradle-plugin-runtime")
}
