package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

/**
 * How stable an IDE release was, normalized across the two vendors: Google's `beta` channel is
 * where Android Studio's RCs ship, and its `canary` channel is the counterpart of an IntelliJ EAP.
 */
internal enum class IdeStability {
    STABLE,
    RC,
    EAP,
}

/**
 * Every Kotlin release, IDE build and Kotlin module the dev kit knows of, read out of this jar from
 * the lists that `fetch-all-kotlin-versions.py`, `fetch-all-ide-versions.py` and
 * `fetch-all-kotlin-modules.py` generate.
 *
 * The version lists are only a catalogue of what exists; which of it a build actually tests against
 * is decided by the `minCliVersion`/`minIdeaVersion` family of properties.
 */
internal object KnownVersions {
    private const val RESOURCE_DIR = "META-INF/org/jetbrains/kotlin/compiler/plugin/devkit"

    private val stabilityByChannel =
        mapOf(
            "stable" to IdeStability.STABLE,
            "rc" to IdeStability.RC,
            "beta" to IdeStability.RC,
            "eap" to IdeStability.EAP,
            "canary" to IdeStability.EAP,
        )

    /** Every Kotlin release published to Maven Central, ascending. */
    val kotlinVersions: List<KotlinToolingVersion> =
        readLines("kotlin-versions.txt").map(::KotlinToolingVersion).sorted()

    /**
     * The platform build of every recorded IDE release, against the channels it shipped in - one
     * Android Studio platform build is reused across its stable, RC and canary lines.
     */
    val ideBuilds: Map<IdeBuildNumber, Set<IdeStability>> = buildMap {
        var stability: IdeStability? = null
        for (line in readLines("ide-versions.txt")) {
            if (line.startsWith('[') && line.endsWith(']')) {
                stability = stabilityByChannel[line.trim('[', ']').substringAfterLast('-')]
            } else if (stability != null) {
                merge(
                    IdeBuildNumber(line.substringBefore('=')),
                    setOf(stability),
                    Set<IdeStability>::plus,
                )
            }
        }
    }

    val kotlinModuleMappings: Map<String, String> =
        readLines("kotlin-modules.txt").associate { line ->
            require('=' in line) { "kotlin-modules.txt has a line that is not a mapping: $line" }
            line.substringBefore('=').trim() to line.substringAfter('=').trim()
        }

    private fun readLines(name: String): List<String> {
        val path = "$RESOURCE_DIR/$name"
        val resource =
            requireNotNull(javaClass.classLoader.getResourceAsStream(path)) {
                "$path is missing from the dev kit's own jar"
            }
        return resource
            .use { it.reader().readLines() }
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') }
    }
}
