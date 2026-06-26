package org.jetbrains.kotlin.compiler.plugin.template

import java.util.ServiceLoader

interface TestCompatContext {
    companion object Default : TestCompatContext {
        val context by lazy {
            val contexts = ServiceLoader.load(TestCompatContext::class.java)
            contexts.firstOrNull() ?: Default
        }
    }
}