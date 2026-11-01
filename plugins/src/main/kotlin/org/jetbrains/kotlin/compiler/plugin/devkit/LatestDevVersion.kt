package org.jetbrains.kotlin.compiler.plugin.devkit

import java.net.URI
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isDev

private const val DEV_REPOSITORY = "https://redirector.kotlinlang.org/maven/dev"

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
internal abstract class LatestDevVersion : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String {
        val url = "$DEV_REPOSITORY/$METADATA_PATH"
        val metadata = runCatching {
            URI(url).toURL().readText()
        }
            .getOrElse { error("$USE_LATEST_DEV_PROPERTY could not read $url: $it") }
        val latest =
            VERSION_ELEMENT.findAll(metadata)
                .map { KotlinToolingVersion(it.groupValues[1]) }
                .filter { it.classifier?.startsWith("dev") == true }
                .maxOrNull()
        return requireNotNull(latest) { "$url lists no dev build" }.versionString
    }
}
