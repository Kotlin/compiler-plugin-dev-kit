pluginManagement {
    includeBuild("../plugins")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://redirector.kotlinlang.org/maven/bootstrap")
        maven("https://redirector.kotlinlang.org/maven/dev/")
    }
}

dependencyResolutionManagement {
    includeBuild("..")
    repositories {
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/bootstrap")
        maven("https://redirector.kotlinlang.org/maven/dev/")
    }
}

rootProject.name = "compiler-plugin-template"

include("compiler-plugin")
include("gradle-plugin")
include("plugin-annotations")
