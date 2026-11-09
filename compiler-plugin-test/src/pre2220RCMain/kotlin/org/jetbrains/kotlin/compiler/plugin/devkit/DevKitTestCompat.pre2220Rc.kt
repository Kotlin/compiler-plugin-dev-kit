package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.directives.model.SimpleDirective
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

public object GeneratedFirTagsDirectives : SimpleDirectivesContainer() {
    public val DISABLE_GENERATED_FIR_TAGS: SimpleDirective by
        directive(description = "Disables generating and checking for GENERATED_FIR_TAGS")
}

public actual val DisableGeneratedFirTagsContainer: SimpleDirectivesContainer =
    GeneratedFirTagsDirectives

public actual val DISABLE_GENERATED_FIR_TAGS: SimpleDirective
    get() = GeneratedFirTagsDirectives.DISABLE_GENERATED_FIR_TAGS
