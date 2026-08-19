package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

/**
 * Which `Beta` and `RC` releases to test against, on top of every stable one.
 *
 * @see DevKitVersionsDsl.cliVersions
 */
public enum class BetaAndRc {
    NONE,
    LATEST,
    ALL,
}

public abstract class DevKitVersionsDsl internal constructor(final override val project: Project) :
    HasProject {
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
    ) {
        val inRange =
            KnownVersions.kotlinVersions.filter {
                it.toKotlinVersion() >= min && (max == null || it.toKotlinVersion() <= max)
            }
        // The release being prepared: no stable of it exists yet, so it is the highest either way.
        val latestBaseVersion = inRange.maxOfOrNull { it.toKotlinVersion() }
        for (version in inRange) {
            val included =
                when (version.maturity) {
                    KotlinToolingVersion.Maturity.STABLE -> true
                    KotlinToolingVersion.Maturity.BETA,
                    KotlinToolingVersion.Maturity.RC ->
                        when (betaAndRc) {
                            BetaAndRc.NONE -> false
                            BetaAndRc.LATEST -> version.toKotlinVersion() == latestBaseVersion
                            BetaAndRc.ALL -> true
                        }

                    else -> false
                }
            if (included) registerVersion(version, forIde = false)
        }
    }

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
        min: IdeBuildNumber,
        max: Int? = null,
        includeRc: Boolean = false,
        includeEap: Boolean = false,
    ) {
        val stabilities = buildSet {
            add(IdeStability.STABLE)
            if (includeRc) add(IdeStability.RC)
            if (includeEap) add(IdeStability.EAP)
        }
        KnownVersions.ideBuilds
            .filterValues { it.any(stabilities::contains) }
            .keys
            .filter { it >= min && (max == null || it.baselineVersion <= max) }
            .mapNotNull {
                CompilerVersionAliases.forIdeBuild(it)?.toString()?.let(::KotlinToolingVersion)
            }
            .forEach { registerVersion(it, forIde = true) }
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
    ) {
        ideaVersions(IdeBuildNumber(min), max, includeRc, includeEap)
    }

    /** Adds the latest Kotlin dev version. This is useful for reacting early to breaking changes */
    public fun useLatestDev() {
        val latestDev = providers.of(LatestDevVersion::class.java) {}.get()
        registerVersion(KotlinToolingVersion(latestDev), forIde = false)
    }

    /**
     * Registers one discovered version, whatever registering a version means for this module. Has
     * to be idempotent: a version can be discovered twice, and named in [TEST_AGAINST_PROPERTY] on
     * top of that.
     */
    protected abstract fun registerVersion(version: KotlinToolingVersion, forIde: Boolean)

    /**
     * Registers everything the properties ask for, and freezes the ranges. Called once the module
     * can service a [registerVersion] call, which is later than this class can know about.
     */
    protected fun registerSupportedVersions() {
        val minCliVersion = providers.gradleProperty(MIN_CLI_VERSION_PROPERTY).orNull
        val maxCliVersion = providers.gradleProperty(MAX_CLI_VERSION_PROPERTY).orNull
        val betaAndRc =
            providers
                .gradleProperty(INCLUDE_BETA_AND_RC_PROPERTY)
                .orNull
                ?.let(String::uppercase)
                ?.let(BetaAndRc::valueOf) ?: BetaAndRc.NONE
        if (minCliVersion != null) cliVersions(minCliVersion, maxCliVersion, betaAndRc)
        val minIdeaVersion =
            providers.gradleProperty(MIN_IDEA_VERSION_PROPERTY).orNull?.let(::IdeBuildNumber)
        val maxIdeaVersion =
            providers.gradleProperty(MAX_IDEA_VERSION_PROPERTY).orNull?.let(String::toIntOrNull)
        val includeIdeaRc =
            providers
                .gradleProperty(INCLUDE_IDEA_RC_PROPERTY)
                .orNull
                ?.let(String::toBooleanStrictOrNull) ?: false
        val includeIdeaEap =
            providers
                .gradleProperty(INCLUDE_IDEA_EAP_PROPERTY)
                .orNull
                ?.let(String::toBooleanStrictOrNull) ?: false
        if (minIdeaVersion != null)
            ideaVersions(minIdeaVersion, maxIdeaVersion, includeIdeaRc, includeIdeaEap)
        val useLatestDev =
            providers
                .gradleProperty(USE_LATEST_DEV_PROPERTY)
                .orNull
                ?.let(String::toBooleanStrictOrNull) ?: false
        if (useLatestDev) useLatestDev()
    }
}
