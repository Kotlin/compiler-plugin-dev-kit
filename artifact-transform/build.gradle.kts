import tapmoc.Severity

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.tapmoc)
}

group = libs.versions.devkit.group.get()

version = libs.versions.devkit.version.get()

dependencies {
    testImplementation(libs.truth)
}

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xcontext-sensitive-resolution")
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

tapmoc {
    gradle(libs.versions.gradle.get())
    checkDependencies(Severity.ERROR)
    checkKotlinStdlibs(Severity.ERROR)
}
