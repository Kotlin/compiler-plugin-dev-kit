plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
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
