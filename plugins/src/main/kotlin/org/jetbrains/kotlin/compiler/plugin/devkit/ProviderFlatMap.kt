package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.HasProject

/**
 * Indicates that calling Provider.get() is safe within this context likely because this is a
 * provider itself
 */
internal interface ProviderFlatMap {
    operator fun <T : Any> Provider<out T>.invoke(): T = get()

    object Unsafe : ProviderFlatMap
}

internal fun <T : Any> Project.providerFlatMap(provider: ProviderFlatMap.() -> T?): Provider<T> =
    provider {
        ProviderFlatMap.Unsafe.provider()
    }

context(_: HasProject)
internal fun <T : Any> provider(provider: ProviderFlatMap.() -> T?): Provider<T> =
    project.providerFlatMap(provider)
