package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.model.SimpleDirective
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

public actual val DISABLE_GENERATED_FIR_TAGS: SimpleDirective
    get() = FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS

public actual val DisableGeneratedFirTagsContainer: SimpleDirectivesContainer
    get() = FirDiagnosticsDirectives
