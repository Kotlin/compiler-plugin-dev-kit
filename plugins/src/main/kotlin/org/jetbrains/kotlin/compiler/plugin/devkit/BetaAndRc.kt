package org.jetbrains.kotlin.compiler.plugin.devkit

/**
 * Which `Beta` and `RC` releases to test against, on top of every stable one.
 *
 * @see DevKitPropertiesDsl.cliVersions
 */
public enum class BetaAndRc {
    NONE,
    LATEST,
    ALL,
}
