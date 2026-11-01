package org.gradle.kotlin.dsl

import org.jetbrains.kotlin.compiler.plugin.devkit.DEV_KIT_GROUP
import org.jetbrains.kotlin.compiler.plugin.devkit.DEV_KIT_VERSION
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

public fun KotlinDependencyHandler.pluginDevKit(module: String, version: String? = null): String =
    "$DEV_KIT_GROUP:$module:${version ?: DEV_KIT_VERSION}"
