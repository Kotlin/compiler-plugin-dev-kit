package org.gradle.kotlin.dsl

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec
import org.jetbrains.kotlin.compiler.plugin.devkit.DEV_KIT_GROUP
import org.jetbrains.kotlin.compiler.plugin.devkit.DEV_KIT_VERSION
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

public fun DependencyHandler.pluginDevKit(module: String, version: String? = null): String =
    "$DEV_KIT_GROUP:$module:${version ?: DEV_KIT_VERSION}"

public fun KotlinDependencyHandler.pluginDevKit(module: String, version: String? = null): String =
    "$DEV_KIT_GROUP:$module:${version ?: DEV_KIT_VERSION}"

public fun PluginDependenciesSpec.pluginDevKit(module: String): PluginDependencySpec =
    id("$DEV_KIT_GROUP.$module") version DEV_KIT_VERSION
