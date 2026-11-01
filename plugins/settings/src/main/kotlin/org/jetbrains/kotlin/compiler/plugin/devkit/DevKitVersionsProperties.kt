package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

/**
 * Which `Beta` and `RC` releases to test against, on top of every stable one.
 *
 * @see DevKitVersionsProperties.cliVersions
 */
public enum class BetaAndRc {
    NONE,
    LATEST,
    ALL,
}

public interface DevKitVersionsProperties {

    /**
     * Kotlin versions to test against, compared by base version so that a bound of `2.4.20` also
     * takes in `2.4.20-Beta1`.
     *
     * @param max upper bound, infinite if null
     * @param betaAndRc which `Beta` and `RC` releases to test against, on top of every stable one
     */
    public fun cliVersions(
        min: KotlinVersion,
        max: KotlinVersion? = null,
        betaAndRc: BetaAndRc = BetaAndRc.NONE,
    )

    /**
     * Kotlin versions to test against, compared by base version so that a bound of `2.4.20` also
     * takes in `2.4.20-Beta1`.
     *
     * @param max upper bound, infinite if null
     * @param betaAndRc which `Beta` and `RC` releases to test against, on top of every stable one
     */
    public fun cliVersions(
        min: String,
        max: String? = null,
        betaAndRc: BetaAndRc = BetaAndRc.NONE,
    ) {
        cliVersions(
            KotlinToolingVersion(min).toKotlinVersion(),
            max?.let(::KotlinToolingVersion)?.toKotlinVersion(),
            betaAndRc,
        )
    }

    /**
     * IDE builds to support, as a platform build number, spelled only as precisely as needed: `253`
     * is the whole 2025.3 series, `253.29346.138` one build of it
     *
     * @param max upper bound, infinite if null
     */
    public fun ideaVersions(
        min: String,
        max: Int? = null,
        includeRc: Boolean = false,
        includeEap: Boolean = false,
    )

    /** Adds the latest Kotlin dev version. This is useful for reacting early to breaking changes */
    public fun useLatestDev()

    /** Adds the latest Kotlin bootstrap version. This is useful for reacting early to breaking changes */
    public fun useLatestBootstrap()
}
