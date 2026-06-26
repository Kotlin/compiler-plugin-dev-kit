pluginManagement {
  includeBuild("plugins")
  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://redirector.kotlinlang.org/maven/bootstrap")
    maven("https://redirector.kotlinlang.org/maven/dev/")
  }
}

dependencyResolutionManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://redirector.kotlinlang.org/maven/bootstrap")
    maven("https://redirector.kotlinlang.org/maven/dev/")
  }
}

include("compiler-plugin-runtime")
include("gradle-plugin-runtime")