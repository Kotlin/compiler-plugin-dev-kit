package org.jetbrains.kotlin.compiler.plugin.template

class CompatContextBeta: CompatContext {
    override val generateBar: Boolean
        get() = true
}