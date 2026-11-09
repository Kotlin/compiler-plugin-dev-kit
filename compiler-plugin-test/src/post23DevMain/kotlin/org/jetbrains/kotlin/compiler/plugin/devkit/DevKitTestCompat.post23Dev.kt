package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.backend.handlers.NoIrCompilationErrorsHandler
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.model.SimpleDirective
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer
import org.jetbrains.kotlin.test.frontend.fir.TagsGeneratorChecker
import org.jetbrains.kotlin.test.model.BackendInputHandler
import org.jetbrains.kotlin.test.services.TestServices

public actual val DISABLE_FIR_DUMP_HANDLER: SimpleDirective =
    FirDiagnosticsDirectives.DISABLE_FIR_DUMP_HANDLER

public actual val DisableFirDumpHandlerContainer: SimpleDirectivesContainer
    get() = FirDiagnosticsDirectives

public actual fun TestConfigurationBuilder.registerTagsGeneratorChecker() {
    configureFirHandlersStep {
        useHandlers(::TagsGeneratorChecker)
    }
}

public actual fun NoIrCompilationErrorsHandlerCompat(
    testServices: TestServices
): BackendInputHandler<IrBackendInput> = NoIrCompilationErrorsHandler(testServices)
