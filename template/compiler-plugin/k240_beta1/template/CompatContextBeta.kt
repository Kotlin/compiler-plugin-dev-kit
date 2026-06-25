package org.jetbrains.kotlin.compiler.plugin.template

class CompatContextBeta: CompatContext {
    override val isInBeta: Boolean
        get() = true
}