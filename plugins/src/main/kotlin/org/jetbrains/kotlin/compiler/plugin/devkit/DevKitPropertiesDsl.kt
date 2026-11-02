package org.jetbrains.kotlin.compiler.plugin.devkit

import kotlin.reflect.KClass
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isDev
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

public interface DevKitTestTarget {
    public val version: KotlinToolingVersion
}

public abstract class DevKitPropertiesDsl<Target : DevKitTestTarget>
internal constructor(final override val project: Project, targetClass: KClass<Target>) :
    HasProject {

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

    public abstract val testAgainst: NamedDomainObjectCollection<Target>

    /** Declares a version that the plugin should be tested against. This method is idempotent. */
    public abstract fun testAgainst(
        version: KotlinToolingVersion,
        forIde: Boolean = version.isDev,
        action: Target.() -> Unit = {},
    ): Target?

    /** Declares a version that the plugin should be tested against. This method is idempotent. */
    public fun testAgainst(
        version: String,
        forIde: Boolean = KotlinToolingVersion(version).isDev,
        action: Target.() -> Unit = {},
    ): Target? = testAgainst(KotlinToolingVersion(version), forIde, action)

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
        betaAndRc: BetaAndRc = BetaAndRc.LATEST,
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
            if (included) testAgainst(version, forIde = false)
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
        betaAndRc: BetaAndRc = BetaAndRc.LATEST,
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
        ideaSinceBuild.set(min)
        ideaUntilBuild.set(max)
        KnownVersions.ideTracksInRange(min, max, includeRc, includeEap)
            .values
            .distinct()
            .mapNotNull(CompilerVersionAliases::forIdeBuild)
            .forEach { testAgainst(it, forIde = true) }
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
        val latestDev =
            providers
                .of(LatestDevVersion::class.java) { parameters { repository.set(DEV_REPOSITORY) } }
                .get()
        testAgainst(KotlinToolingVersion(latestDev), forIde = false)
    }

    /**
     * Adds the latest Kotlin bootstrap version. This is useful for reacting early to breaking
     * changes
     */
    public fun useLatestBootstrap() {
        val latestBootstrap =
            providers
                .of(LatestDevVersion::class.java) {
                    parameters { repository.set(BOOTSTRAP_REPOSITORY) }
                }
                .get()
        testAgainst(KotlinToolingVersion(latestBootstrap), forIde = false)
    }

    /**
     * Which registered [testAgainst] version the `default(Functional)Test` task delegates to.
     * Defaults to [DEFAULT_TEST_VERSION_PROPERTY], the Kotlin version applied to the build, or an
     * arbitrary target otherwise
     *
     * @see [defaultTestVersion] to set this using a version instead
     */
    public val defaultTestTarget: Property<Target> = objects.property(targetClass.java)

    /**
     * Sets the [testAgainst] version the `default(Functional)Test` task delegates to. Defaults to
     * [DEFAULT_TEST_VERSION_PROPERTY], the Kotlin version applied to the build, or an arbitrary
     * target otherwise
     */
    public fun defaultTestVersion(version: KotlinToolingVersion) {
        defaultTestTarget.set(testAgainst(version))
    }

    public fun defaultTestVersion(version: String) {
        defaultTestVersion(KotlinToolingVersion(version))
    }

    /**
     * The package used by convention for BuildConfig generation. It's also assumed to be the plugin
     * ID. Defaults to [PLUGIN_PACKAGE_PROPERTY], else [Project.getGroup]
     */
    public val pluginPackage: Property<String> =
        objects.property(
            gradleProperty(PLUGIN_PACKAGE_PROPERTY).orElse(provider { project.group.toString() })
        )

    /**
     * Libraries that all test data files can use and the Gradle plugin should add; includes
     * [COMPANION_LIBRARIES_PROPERTY]
     */
    public abstract fun companionLibrary(dependencyNotationOrSiblingName: String)

    /**
     * Libraries that all test data files can use and the Gradle plugin should add; includes
     * [COMPANION_LIBRARIES_PROPERTY]
     */
    public abstract fun companionLibrary(project: Project, group: Any? = null, version: Any? = null)

    /**
     * The highest Kotlin version every companion library was built using. This is used to filter
     * KMP tests for lower versions because KLIBS are not forwards compatible.
     *
     * The default is the highest `compiler_version` the libraries' klib manifests record; if all
     * companion libraries are JVM only, the default is the minimum registered [testAgainst]
     * version.
     */
    public abstract val companionLibrariesKotlinVersion: Property<KotlinToolingVersion>

    protected fun registerSupportedVersions() {
        listFromGradleProperty(TEST_AGAINST_PROPERTY).forEach {
            testAgainst(KotlinToolingVersion(it))
        }
        gradleProperty(DEFAULT_TEST_VERSION_PROPERTY).orNull?.let {
            testAgainst(KotlinToolingVersion(it))
        }
        val minCliVersion = gradleProperty(MIN_CLI_VERSION_PROPERTY).orNull
        val maxCliVersion = gradleProperty(MAX_CLI_VERSION_PROPERTY).orNull
        val betaAndRc =
            gradleProperty(INCLUDE_BETA_AND_RC_PROPERTY)
                .orNull
                ?.let(String::uppercase)
                ?.let(BetaAndRc::valueOf) ?: BetaAndRc.NONE
        if (minCliVersion != null) cliVersions(minCliVersion, maxCliVersion, betaAndRc)
        val minIdeaVersion = gradleProperty(MIN_IDEA_VERSION_PROPERTY).orNull?.let(::IdeBuildNumber)
        val maxIdeaVersion =
            gradleProperty(MAX_IDEA_VERSION_PROPERTY).orNull?.let(String::toIntOrNull)
        if (minIdeaVersion != null)
            ideaVersions(
                minIdeaVersion,
                maxIdeaVersion,
                gradleProperty(INCLUDE_IDEA_RC_PROPERTY).isTrue(),
                gradleProperty(INCLUDE_IDEA_EAP_PROPERTY).isTrue(),
            )
        if (gradleProperty(USE_LATEST_DEV_PROPERTY).isTrue()) useLatestDev()
        if (gradleProperty(USE_LATEST_BOOTSTRAP_PROPERTY).isTrue()) useLatestBootstrap()

        if (testAgainst.isEmpty()) testAgainst(kotlinToolingVersion, forIde = false)

        defaultTestTarget.convention(
            gradleProperty(DEFAULT_TEST_VERSION_PROPERTY).orNull?.let(::testAgainst)
                ?: testAgainst.findByName(kotlinToolingVersion.versionName)
                ?: testAgainst.last()
        )
    }

    protected fun registerCompanionLibraries() {
        listFromGradleProperty(COMPANION_LIBRARIES_PROPERTY).forEach {
            when {
                it.isProjectPath -> companionLibrary(project.project(it))
                else -> companionLibrary(it)
            }
        }
    }
}
