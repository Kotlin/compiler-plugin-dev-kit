package org.jetbrains.kotlin.compiler.plugin.devkit

/**
 * Gradle property listing the libraries a plugin ships for its users, separated by commas or
 * whitespace. An entry starting with `.` or `/` is considered an included build, which will
 * automatically be included if the settings plugin is applied
 */
public const val COMPANION_LIBRARIES_PROPERTY: String =
    "kotlin.compiler.plugin.devkit.companionLibraries"
