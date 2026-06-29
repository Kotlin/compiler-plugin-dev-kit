package org.jetbrains.kotlin.compiler.plugin.template

import java.util.*

interface CompatContext {
    val generateBar: Boolean

    companion object Default : CompatContext {
        override val generateBar: Boolean
            get() = false

        val context by lazy {
            val contexts = ServiceLoader.load(CompatContext::class.java)
            contexts.firstOrNull() ?: Default
        }
    }
}
