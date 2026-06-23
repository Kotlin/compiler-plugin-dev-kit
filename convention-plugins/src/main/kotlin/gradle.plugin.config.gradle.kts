plugins {
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
  `java-gradle-plugin`
  `java-test-fixtures`
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

buildConfig {
  packageName(project.group.toString())

  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")

  val pluginProject = configExtension.compilerPlugin
  buildConfigField("KOTLIN_PLUGIN_GROUP") {
    type(String::class)
    expression(pluginProject.map { "\"${it.group}\"" })
  }
  buildConfigField("KOTLIN_PLUGIN_NAME") {
    type(String::class)
    expression(pluginProject.map { "\"${it.name}\"" })
  }
  buildConfigField("KOTLIN_PLUGIN_VERSION") {
    type(String::class)
    expression(pluginProject.map { "\"${it.version}\"" })
  }

  buildConfigField("ANNOTATIONS_LIBRARY_COORDINATES") {
    type("String")
    expression(configExtension.pluginAnnotations.map { "\"${it.group}:${it.name}:${it.version}\"" })
  }
}