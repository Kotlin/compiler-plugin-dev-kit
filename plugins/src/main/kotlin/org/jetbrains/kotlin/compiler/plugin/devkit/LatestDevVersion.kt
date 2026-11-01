package org.jetbrains.kotlin.compiler.plugin.devkit

import java.net.URI
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal const val DEV_REPOSITORY = "https://redirector.kotlinlang.org/maven/dev"
internal const val BOOTSTRAP_REPOSITORY = "https://redirector.kotlinlang.org/maven/bootstrap"

private const val METADATA_PATH = "org/jetbrains/kotlin/kotlin-compiler/maven-metadata.xml"

// https://xkcd.com/1171/
private val VERSION_ELEMENT = Regex("<version>([^<]+)</version>")

/**
 * The newest `dev` build of the Kotlin compiler there is, read straight out of the dev repository's
 * `maven-metadata.xml`.
 *
 * A [ValueSource] rather than a plain read, so that the configuration cache re-checks it instead of
 * pinning whatever build was newest when the entry was stored - which for a canary would defeat the
 * point. It only runs when [DevKitCompilerConfigExtension.useLatestDev] asks for it.
 */
internal abstract class LatestDevVersion : ValueSource<String, LatestDevVersion.Parameters> {
    interface Parameters : ValueSourceParameters {
        val repository: Property<String>
    }

    override fun obtain(): String {
        val url = "${parameters.repository.get()}/$METADATA_PATH"
        val metadata = runCatching {
            URI(url).toURL().readText()
        }
            .getOrElse {
                error("$USE_LATEST_DEV_PROPERTY/useLatestBootstrap could not read $url: $it")
            }
        val latest =
            VERSION_ELEMENT.findAll(metadata)
                .map { KotlinToolingVersion(it.groupValues[1]) }
                .filter { it.classifier?.startsWith("dev") == true }
                .maxOrNull()
        return requireNotNull(latest) { "$url lists no dev build" }.versionString
    }
}
