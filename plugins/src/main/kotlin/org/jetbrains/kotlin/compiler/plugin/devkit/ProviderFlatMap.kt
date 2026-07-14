package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.provider.Provider

/**
 * Indicates that calling Provider.get() is safe within this context likely because this is a
 * provider itself
 */
internal interface ProviderFlatMap {
    operator fun <T : Any> Provider<out T>.invoke(): T = get()

    object Unsafe : ProviderFlatMap
}
