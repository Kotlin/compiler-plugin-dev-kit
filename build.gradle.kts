plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.tapmoc) apply false
    alias(libs.plugins.spotless)
    id("devkit.dokka")
}

spotless {
    val excludes =
        arrayOf(
            "**/build/**",
            "**/.gradle/**",
            "**/.kotlin/**",
            "**/src/test/data/**",
        )
    kotlin {
        target("**/*.kt")
        targetExclude(*excludes)
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(*excludes)
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}

val devKitGroup = libs.versions.devkit.group.get()
val devKitVersion = libs.versions.devkit.version.get()

dependencies {
    dokka(project(":compiler-plugin-runtime"))
    dokka(project(":compiler-plugin-test"))
    dokka(project(":compiler-plugin-test-generator"))
    dokka(project(":gradle-plugin-runtime"))
    // These modules are in included builds, so the dependencies use their coordinates.
    dokka("$devKitGroup:plugins:$devKitVersion")
    dokka("$devKitGroup:settings:$devKitVersion")
    dokka("$devKitGroup:version-resolution:$devKitVersion")
}

dokka {
    moduleName = "Kotlin Compiler Plugin DevKit"
    moduleVersion = devKitVersion
    pluginsConfiguration.html {
        customAssets.from("docs/static/img/logo-icon.svg")
        footerMessage = "© JetBrains s.r.o. and contributors"
        homepageLink = "https://kotlin.github.io/compiler-plugin-dev-kit/"
    }
}
