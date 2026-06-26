pluginManagement {
  includeBuild("plugins")
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

include("compiler-plugin-runtime")
include("gradle-plugin-runtime")