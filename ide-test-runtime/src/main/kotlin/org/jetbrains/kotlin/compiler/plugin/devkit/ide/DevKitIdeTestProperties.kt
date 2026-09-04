package org.jetbrains.kotlin.compiler.plugin.devkit.ide

private const val PREFIX = "kotlin.compiler.plugin.devkit.ide"

/**
 * System properties the `ideTest { }` configuration sets on every IDE test task. They are the only
 * channel between the two: the plugin knows nothing of the classes here, and the test knows nothing
 * of the build.
 */
public object DevKitIdeTestProperties {
    /** The IDE the current task runs against, in [IdeUnderTest.spec] form. */
    public const val IDE_SPEC: String = "$PREFIX.spec"

    /** @see AbstractDevKitIdeTest.pluginUnderTest */
    public const val PLUGIN_ID: String = "$PREFIX.pluginId"

    /** @see AbstractDevKitIdeTest.errorPatterns */
    public const val ERROR_PATTERNS: String = "$PREFIX.errorPatterns"

    /** @see AbstractDevKitIdeTest.forbiddenLogPatterns */
    public const val FORBIDDEN_LOG_PATTERNS: String = "$PREFIX.forbiddenLogPatterns"

    /** Distribution the generated project's Gradle wrapper points the IDE at. */
    public const val GRADLE_DISTRIBUTION_URL: String = "$PREFIX.gradleDistributionUrl"
}

internal fun systemPropertyList(name: String): List<String> =
    System.getProperty(name).orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
