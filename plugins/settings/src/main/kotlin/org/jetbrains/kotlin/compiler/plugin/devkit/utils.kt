@file:JvmName("Utils2Kt")

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.initialization.Settings

internal val String.isIncludedBuildPath: Boolean
    get() = startsWith('.') || startsWith('/')

private val entrySeparators = Regex("[,\\s]+")

internal fun Settings.listFromGradleProperty(property: String): List<String> =
    providers.gradleProperty(property).getOrElse("").split(entrySeparators).filter {
        it.isNotEmpty()
    }
