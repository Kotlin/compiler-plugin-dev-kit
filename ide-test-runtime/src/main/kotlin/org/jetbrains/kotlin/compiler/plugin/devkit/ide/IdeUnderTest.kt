package org.jetbrains.kotlin.compiler.plugin.devkit.ide

/** How stable an IntelliJ IDEA build is, which is also what decides how it is downloaded. */
public enum class IdeBuildType {
    RELEASE,
    RC,
    EAP;

    internal val starterBuildType: String
        get() = name.lowercase()
}

/**
 * One IDE that an [AbstractDevKitIdeTest] launches.
 *
 * The [spec] form is what the Gradle side hands to the test task, and [parse] is its only reader,
 * so the two stay in step without the plugin depending on this module.
 */
public sealed interface IdeUnderTest {
    /**
     * Identifier unique among the IDEs of one build, and safe in a file name.
     *
     * Spelled the same way the Gradle side spells the `<id>IdeTest` task, so an IDE Starter
     * workspace can be traced back to the task that produced it.
     */
    public val id: String

    public val displayName: String

    /** `<product code>:<version>[:<extra>]`, the wire format of [parse]. */
    public val spec: String

    public companion object {
        /**
         * Reads a `<product code>:<version>[:<extra>]` spec.
         *
         * `IU`/`IC` take a marketing version (`2026.1.1`) for releases and a platform build number
         * (`261.27258.27`) for an `rc`/`eap` extra. `AS` takes an Android Studio version
         * (`2026.1.1.8`) and, as its extra, a comma-separated list of installer URLs - hence the
         * three-way split, which leaves the `:` in those URLs alone.
         */
        public fun parse(spec: String): IdeUnderTest {
            val parts = spec.trim().split(':', limit = 3)
            require(parts.size in 2..3) { "Not an IDE spec: '$spec'" }
            val (product, version) = parts
            val extra = parts.getOrElse(2) { "" }.takeIf(String::isNotEmpty)
            return when (product) {
                "IU",
                "IC" ->
                    IntelliJIdea(
                        edition =
                            if (product == "IU") IntelliJIdea.Edition.ULTIMATE
                            else IntelliJIdea.Edition.COMMUNITY,
                        version = version,
                        buildType =
                            extra?.let { IdeBuildType.valueOf(it.uppercase()) }
                                ?: IdeBuildType.RELEASE,
                    )

                "AS" -> AndroidStudio(version = version)
                else -> error("Unknown IDE product code '$product' in spec '$spec'")
            }
        }
    }
}

/** An IntelliJ IDEA build, either edition. */
public data class IntelliJIdea(
    public val edition: Edition,
    /** Marketing version for a [IdeBuildType.RELEASE], platform build number otherwise. */
    public val version: String,
    public val buildType: IdeBuildType = IdeBuildType.RELEASE,
) : IdeUnderTest {
    public enum class Edition(
        public val productCode: String,
        public val displayName: String,
        internal val idPrefix: String,
    ) {
        ULTIMATE("IU", "IntelliJ IDEA Ultimate", "ideaUltimate"),
        COMMUNITY("IC", "IntelliJ IDEA Community", "ideaCommunity"),
    }

    override val id: String
        get() =
            edition.idPrefix +
                version.replace('.', '_') +
                if (buildType == IdeBuildType.RELEASE) ""
                else buildType.name.lowercase().replaceFirstChar(Char::titlecase)

    override val displayName: String
        get() =
            "${edition.displayName} $version" +
                if (buildType == IdeBuildType.RELEASE) "" else " ${buildType.name}"

    override val spec: String
        get() = "${edition.productCode}:$version:${buildType.starterBuildType}"
}

/** An Android Studio build, identified by its four-component version. */
public data class AndroidStudio(
    public val version: String,
) : IdeUnderTest {
    override val id: String
        get() = "androidStudio" + version.replace('.', '_')

    override val displayName: String
        get() = "Android Studio $version"

    override val spec: String
        get() = "AS:$version"
}
