package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isDev
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

public abstract class DevKitVersionsDsl internal constructor(final override val project: Project) :
    HasProject, DevKitVersionsProperties {

    /**
     * the lowest Idea build this plugin supports. This only affects the range reported in metadata
     * read by IDE. Importantly, it does not affect the versions you actually test against.
     *
     * @see ideaVersions
     */
    public val ideaSinceBuild: Property<IdeBuildNumber> = objects.property()

    /**
     * the highest Idea build this plugin supports. This only affects the range reported in metadata
     * read by IDE. Importantly, it does not affect the versions you actually test against.
     *
     * This gets interpreted as `2XX.*`, i.e. it's the highest supported major build.
     *
     * @see ideaVersions
     */
    public val ideaUntilBuild: Property<Int> = objects.property()

    override fun cliVersions(
        min: KotlinVersion,
        max: KotlinVersion?,
        betaAndRc: BetaAndRc,
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

    override fun cliVersions(
        min: String,
        max: String?,
        betaAndRc: BetaAndRc,
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
     * Only the newest build of each release line counts, so "IntelliJ IDEA 2026.2 (eap)" registers
     * one version rather than one per weekly EAP.
     *
     * @param max upper bound, infinite if null
     */
    public fun ideaVersions(
        min: IdeBuildNumber,
        max: Int? = null,
        includeRc: Boolean = false,
        includeEap: Boolean = false,
    ) {
        ideaSinceBuild.set(min)
        ideaUntilBuild.set(max)
        KnownVersions.ideTracksInRange(min, max, includeRc, includeEap)
            .values
            .distinct()
            .mapNotNull(CompilerVersionAliases::forIdeBuild)
            .forEach { registerVersion(it, forIde = true) }
    }

    override fun ideaVersions(
        min: String,
        max: Int?,
        includeRc: Boolean,
        includeEap: Boolean,
    ) {
        ideaVersions(IdeBuildNumber(min), max, includeRc, includeEap)
    }

    override fun useLatestDev() {
        val latestDev = providers.of(LatestDevVersion::class.java) {
            parameters { repository.set(DEV_REPOSITORY) }
        }.get()
        registerVersion(KotlinToolingVersion(latestDev), forIde = false)
    }

    override fun useLatestBootstrap() {
        val latestBootstrap = providers.of(LatestDevVersion::class.java) {
            parameters { repository.set(BOOTSTRAP_REPOSITORY) }
        }.get()
        registerVersion(KotlinToolingVersion(latestBootstrap), forIde = false)
    }

    /**
     * Registers one discovered version, whatever registering a version means for this module. Has
     * to be idempotent: a version can be discovered twice, and named in [TEST_AGAINST_PROPERTY] on
     * top of that.
     */
    protected abstract fun registerVersion(
        version: KotlinToolingVersion,
        forIde: Boolean = version.isDev,
    )

    /**
     * Registers everything the properties ask for, and freezes the ranges. Called once the module
     * can service a [registerVersion] call, which is later than this class can know about.
     */
    protected fun registerSupportedVersions() {
        listFromExtOrGradleProperty(TEST_AGAINST_PROPERTY).forEach {
            registerVersion(KotlinToolingVersion(it))
        }
        extOrGradleProperty(DEFAULT_TEST_VERSION_PROPERTY).orNull?.let {
            registerVersion(KotlinToolingVersion(it))
        }
        val minCliVersion = extOrGradleProperty(MIN_CLI_VERSION_PROPERTY).orNull
        val maxCliVersion = extOrGradleProperty(MAX_CLI_VERSION_PROPERTY).orNull
        val betaAndRc =
            extOrGradleProperty(INCLUDE_BETA_AND_RC_PROPERTY)
                .orNull
                ?.let(String::uppercase)
                ?.let(BetaAndRc::valueOf) ?: BetaAndRc.NONE
        if (minCliVersion != null) cliVersions(minCliVersion, maxCliVersion, betaAndRc)
        val minIdeaVersion =
            extOrGradleProperty(MIN_IDEA_VERSION_PROPERTY).orNull?.let(::IdeBuildNumber)
        val maxIdeaVersion =
            extOrGradleProperty(MAX_IDEA_VERSION_PROPERTY).orNull?.let(String::toIntOrNull)
        if (minIdeaVersion != null)
            ideaVersions(
                minIdeaVersion,
                maxIdeaVersion,
                extOrGradleProperty(INCLUDE_IDEA_RC_PROPERTY).isTrue(),
                extOrGradleProperty(INCLUDE_IDEA_EAP_PROPERTY).isTrue(),
            )
        if (extOrGradleProperty(USE_LATEST_DEV_PROPERTY).isTrue()) useLatestDev()
        if (extOrGradleProperty(USE_LATEST_BOOTSTRAP_PROPERTY).isTrue()) useLatestBootstrap()
    }
}
