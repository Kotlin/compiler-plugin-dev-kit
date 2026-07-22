pluginManagement {
    includeBuild("../plugins")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://redirector.kotlinlang.org/maven/bootstrap")
        maven("https://redirector.kotlinlang.org/maven/dev/")
        // Publications used by IJ
        // https://kotlinlang.slack.com/archives/C7L3JB43G/p1757001642402909
        maven("https://redirector.kotlinlang.org/maven/intellij-dependencies/")
    }
}

dependencyResolutionManagement {
    includeBuild("..") { name = "compiler-plugin-dev-kit" }
    includeBuild("../artifact-transform")
    repositories {
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/bootstrap")
        maven("https://redirector.kotlinlang.org/maven/dev/")
        // Publications used by IJ
        // https://kotlinlang.slack.com/archives/C7L3JB43G/p1757001642402909
        maven("https://redirector.kotlinlang.org/maven/intellij-dependencies/")
    }
}

rootProject.name = "compiler-plugin-template"

include("compiler-plugin")

include("gradle-plugin")

include("plugin-annotations")
