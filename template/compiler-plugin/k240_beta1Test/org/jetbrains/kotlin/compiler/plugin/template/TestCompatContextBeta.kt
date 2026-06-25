package org.jetbrains.kotlin.compiler.plugin.template

class TestCompatContextBeta: TestCompatContext {
    override val pathToTestDir: String get() = "compiler-plugin/k240_beta1TestData/box"
}