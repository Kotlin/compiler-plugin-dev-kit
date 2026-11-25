// The facade class that a file's top-level declarations compile into is part of the published
// binary ABI. This file was renamed, so `@file:JvmName` keeps its pre-rename facade class.
@file:JvmName("DevKitTestCompat_post2220RcKt")

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.model.SimpleDirective
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

public actual val DISABLE_GENERATED_FIR_TAGS: SimpleDirective
    get() = FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS

public actual val DisableGeneratedFirTagsContainer: SimpleDirectivesContainer
    get() = FirDiagnosticsDirectives
