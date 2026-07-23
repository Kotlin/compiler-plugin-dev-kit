import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `embedded-kotlin`
    alias(libs.plugins.buildconfig)
    id("com.vanniktech.maven.publish") version libs.versions.publish
}

group = "org.jetbrains.kotlin.compiler.plugin.devkit"

version = "0.0.1-SNAPSHOT"

dependencies {
    testImplementation(libs.truth)
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-sensitive-resolution")
        jvmTarget = JvmTarget.JVM_17
    }
    explicitApi()
}

buildConfig {
    packageName(group.toString())
    kotlin {
        useKotlinOutput {
            internalVisibility = true
            topLevelConstants = true
        }
    }
    buildConfigField(
        "kotlin.collections.Map<String, String>",
        "BUILT_IN_COMPILER_VERSION_ALIASES",
        providers
            .fileContents(layout.projectDirectory.file("ide-mappings.txt"))
            .asText
            // Known tags-to-real version mappings for IDE builds.
            // Android Studio canary builds report a fake version like "2.3.255-dev-255".
            // The real version can be found by checking the IntelliJ tag for the studio build
            // number:
            // https://github.com/JetBrains/intellij-community/blob/idea/<intellij-version>/.idea/libraries/kotlinc_kotlin_compiler_common.xml
            .map { text ->
                text
                    .lineSequence()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .joinToString(prefix = "mapOf(\n", postfix = "\n)", separator = "\n,") { line ->
                        val (from, to) = line.split('=', limit = 2)
                        "  \"$from\" to \"$to\""
                    }
            },
    )
}

publishing {
    repositories {
        maven {
            name = "devKitSpace"
            url = uri("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap")
            credentials(PasswordCredentials::class)
        }
    }
}
