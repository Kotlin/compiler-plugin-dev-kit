package org.jetbrains.kotlin.compiler.plugin.devkit

internal const val DEV_KIT_PROPERTY_PREFIX = "kotlin.compiler.plugin.devkit"

/**
 * Gradle property listing the Kotlin versions to test against, separated by commas or whitespace.
 * Versions declared in the `pluginDevKit { }` block are registered on top of these, so a version
 * that needs an overlay or any other customization can still be named there.
 */
public const val TEST_AGAINST_PROPERTY: String = "$DEV_KIT_PROPERTY_PREFIX.testAgainst"

/** @see DevKitVersionsDsl.cliVersions */
public const val MIN_CLI_VERSION_PROPERTY: String = "$DEV_KIT_PROPERTY_PREFIX.minCliVersion"

/** @see DevKitVersionsDsl.cliVersions */
public const val MAX_CLI_VERSION_PROPERTY: String = "$DEV_KIT_PROPERTY_PREFIX.maxCliVersion"

/** @see DevKitVersionsDsl.cliVersions */
public const val INCLUDE_BETA_AND_RC_PROPERTY: String = "$DEV_KIT_PROPERTY_PREFIX.includeBetaAndRc"

/** @see DevKitVersionsDsl.ideaVersions */
public const val MIN_IDEA_VERSION_PROPERTY: String = "$DEV_KIT_PROPERTY_PREFIX.minIdeaVersion"

/** @see DevKitVersionsDsl.ideaVersions */
public const val MAX_IDEA_VERSION_PROPERTY: String = "$DEV_KIT_PROPERTY_PREFIX.maxIdeaVersion"

/** @see DevKitVersionsDsl.ideaVersions */
public const val INCLUDE_IDEA_RC_PROPERTY: String = "$DEV_KIT_PROPERTY_PREFIX.includeIdeaRc"

/** @see DevKitVersionsDsl.ideaVersions */
public const val INCLUDE_IDEA_EAP_PROPERTY: String = "$DEV_KIT_PROPERTY_PREFIX.includeIdeaEap"

/** @see DevKitVersionsDsl.useLatestDev */
public const val USE_LATEST_DEV_PROPERTY: String = "$DEV_KIT_PROPERTY_PREFIX.useLatestDev"

/**
 * Gradle property holding the version that the generic `test`/`functionalTest` task delegates to.
 * The version is registered if [TEST_AGAINST_PROPERTY] didn't already list it.
 */
public const val DEFAULT_TEST_VERSION_PROPERTY: String =
    "$DEV_KIT_PROPERTY_PREFIX.defaultTestVersion"

/** Gradle property holding the plugin package, and with it the plugin ID. */
public const val PLUGIN_PACKAGE_PROPERTY: String = "$DEV_KIT_PROPERTY_PREFIX.pluginPackage"

/** Gradle property holding the path of the compiler-plugin project a Gradle plugin applies. */
public const val COMPILER_PLUGIN_PROPERTY: String = "$DEV_KIT_PROPERTY_PREFIX.compilerPlugin"

/**
 * Gradle property listing the libraries a plugin ships for its users, separated by commas or
 * whitespace. An entry starting with `:` is a project path in this build, published under that
 * project's own `group`, `name` and `version`; anything else is used as-is as a
 * `group:artifact:version` coordinate.
 */
public const val COMPANION_LIBRARIES_PROPERTY: String =
    "$DEV_KIT_PROPERTY_PREFIX.companionLibraries"
