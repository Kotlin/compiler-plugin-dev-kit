@file:JvmName("Utils2Kt")

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.extra

@RequiresOptIn(
    "This declaration is internal to the DevKit plugin. It's only public due to the lack of shared internals"
)
internal annotation class DevKitInternal

@DevKitInternal
public val String.isIncludedBuildPath: Boolean
    get() = startsWith('.') || startsWith('/')

private val entrySeparators = Regex("[,\\s]+")

@DevKitInternal
public fun ProviderFactory.listFromGradleProperty(property: String): List<String> =
    gradleProperty(property).getOrElse("").split(entrySeparators).filter {
        it.isNotEmpty()
    }

@DevKitInternal
public fun ExtraPropertiesExtension.list(property: String): List<String> =
    if (has(property))
        this[property].toString().split(entrySeparators).filter {
            it.isNotEmpty()
        }
    else emptyList()

internal fun ExtraPropertiesExtension.appendList(property: String, value: String) {
    val prefix = if (has(property)) "${this[property]}," else ""
    this[property] = prefix + value
}

internal fun Settings.listFromExtOrGradleProperty(property: String): List<String> =
    providers.listFromGradleProperty(property).ifEmpty { extra.list(property) }
