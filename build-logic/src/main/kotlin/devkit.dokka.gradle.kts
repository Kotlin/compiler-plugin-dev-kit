plugins { id("org.jetbrains.dokka") }

// A release build links to the sources of its tag, so the line numbers match. AGENTS.md describes
// `DEVKIT_PUBLISH_VERSION`.
val sourceRef =
    providers.environmentVariable("DEVKIT_PUBLISH_VERSION").map { "v$it" }.getOrElse("main")

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory = repositoryRoot
            remoteUrl("https://github.com/Kotlin/compiler-plugin-dev-kit/blob/$sourceRef")
            remoteLineSuffix = "#L"
        }
    }
    dokkaPublications.configureEach {
        suppressObviousFunctions = true
        failOnWarning = true
    }
}
