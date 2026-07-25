package org.gradle.kotlin.dsl

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

private const val pluginDevKitVersion = "0.0.1-SNAPSHOT"

public fun DependencyHandler.pluginDevKit(module: String, version: String? = null): String =
    "org.jetbrains.kotlin.compiler.plugin.devkit:$module:${version ?: pluginDevKitVersion }"

public fun PluginDependenciesSpec.pluginDevKit(module: String): PluginDependencySpec =
    kotlin("compiler.plugin.devkit.$module") version pluginDevKitVersion
