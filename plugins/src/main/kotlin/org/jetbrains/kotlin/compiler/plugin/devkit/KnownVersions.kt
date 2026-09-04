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
 * One IDE release line in one channel: the `idea-eap` section's `2026.2`, which its users would
 * call "IntelliJ IDEA 2026.2 (eap)".
 *
 * A line publishes build after build over its life - a 2026.2 EAP roughly every week - and each
 * build that bundles a different compiler would become a target of its own. Grouping by line is
 * what lets only the newest of them count.
 */
internal data class IdeTrack(
    val channel: String,
    val version: String,
    val stability: IdeStability,
) {
    /** The vendor half of [channel]: `idea` or `studio`. */
    val product: String
        get() = channel.substringBeforeLast('-')
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
     * The newest platform build of every recorded IDE release line - one Android Studio platform
     * build is reused across its stable, RC and canary lines, so the same build can be the newest
     * of several.
     *
     * Superseded builds of a line are dropped: nobody is expected to stay on last week's EAP, and
     * each one bundling a different compiler would otherwise cost a target of its own.
     */
    val ideTracks: Map<IdeTrack, IdeBuildNumber> = buildMap {
        var channel = ""
        var stability: IdeStability? = null
        for (line in readLines("ide-versions.txt")) {
            if (line.startsWith('[') && line.endsWith(']')) {
                channel = line.trim('[', ']')
                stability = stabilityByChannel[channel.substringAfterLast('-')]
            } else if (stability != null) {
                merge(
                    IdeTrack(channel, line.substringAfter('='), stability),
                    IdeBuildNumber(line.substringBefore('=')),
                    ::maxOf,
                )
            }
        }
    }

    /**
     * The [ideTracks] the `minIdeaVersion` family of settings selects, which is what both the
     * compiler versions a plugin is built against and the IDEs its IDE tests run in are worked out
     * from.
     *
     * @param max upper bound on the platform baseline, infinite if null
     */
    fun ideTracksInRange(
        min: IdeBuildNumber,
        max: Int? = null,
        includeRc: Boolean = false,
        includeEap: Boolean = false,
    ): Map<IdeTrack, IdeBuildNumber> {
        val stabilities = buildSet {
            add(IdeStability.STABLE)
            if (includeRc) add(IdeStability.RC)
            if (includeEap) add(IdeStability.EAP)
        }
        return ideTracks
            .filterKeys { it.stability in stabilities }
            .filterValues {
                it >= min && (max == null || it.baselineVersion <= max)
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
