package org.jetbrains.kotlin.compiler.plugin.template

import java.util.ServiceLoader

interface TestCompatContext {
    val pathToTestDir: String

    companion object Default : TestCompatContext {
        override val pathToTestDir: String
            get() = "compiler-plugin/testData/box"
        val context by lazy {
            val contexts = ServiceLoader.load(TestCompatContext::class.java)
            contexts.firstOrNull() ?: Default
        }
    }
}