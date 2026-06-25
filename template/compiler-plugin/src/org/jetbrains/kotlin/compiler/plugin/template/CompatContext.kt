package org.jetbrains.kotlin.compiler.plugin.template

import java.util.ServiceLoader

interface CompatContext {
    val isInBeta: Boolean

    companion object Default : CompatContext {
        override val isInBeta: Boolean
            get() = false
        val context by lazy {
            val contexts = ServiceLoader.load(CompatContext::class.java)
            contexts.firstOrNull() ?: Default
        }
    }
}