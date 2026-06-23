plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.github.gmazzo.buildconfig")
  id("java-gradle-plugin")
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

dependencies {
  implementation(kotlin("gradle-plugin-api"))
  testImplementation(kotlin("test-junit5"))
}

interface PluginConfigExtension {
  val compilerPlugin: Property<Project>
  val pluginAnnotations: Property<Project>
}

val configExtension = project.extensions.create<PluginConfigExtension>("pluginConfig")

afterEvaluate {
  buildConfig {
    packageName(project.group.toString())

    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")

    val pluginProject = configExtension.compilerPlugin.get()
    buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${pluginProject.group}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${pluginProject.name}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${pluginProject.version}\"")

    val annotationsProject = configExtension.pluginAnnotations.get()
    buildConfigField(
      type = "String",
      name = "ANNOTATIONS_LIBRARY_COORDINATES",
      expression = "\"${annotationsProject.group}:${annotationsProject.name}:${annotationsProject.version}\""
    )
  }
}