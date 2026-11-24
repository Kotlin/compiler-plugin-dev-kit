package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.compiler.plugin.devkit.handlers.BackportedFirDumpHandler
import org.jetbrains.kotlin.test.backend.handlers.NoFir2IrCompilationErrorsHandler
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.directives.model.SimpleDirective
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer
import org.jetbrains.kotlin.test.model.BackendInputHandler
import org.jetbrains.kotlin.test.services.TestServices

public object DisableFirDumpHandlerDirectives : SimpleDirectivesContainer() {
    public val DISABLE_FIR_DUMP_HANDLER: SimpleDirective by
        directive(
            description =
                """
            Completely disables ${BackportedFirDumpHandler::class}.
            It could be useful, for example, if there are JVM and JS tests configured, and dumps between are
            slightly different due to differences in stdlib actualization.
        """
                    .trimIndent()
        )
}

public actual val DISABLE_FIR_DUMP_HANDLER: SimpleDirective
    get() = DisableFirDumpHandlerDirectives.DISABLE_FIR_DUMP_HANDLER

public actual val DisableFirDumpHandlerContainer: SimpleDirectivesContainer
    get() = DisableFirDumpHandlerDirectives

public actual fun NoIrCompilationErrorsHandlerCompat(
    testServices: TestServices
): BackendInputHandler<IrBackendInput> = NoFir2IrCompilationErrorsHandler(testServices)
