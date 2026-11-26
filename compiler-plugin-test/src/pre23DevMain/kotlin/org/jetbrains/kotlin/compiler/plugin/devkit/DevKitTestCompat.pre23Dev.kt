package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.compiler.plugin.devkit.handlers.BackportedFirDumpHandler
import org.jetbrains.kotlin.test.WrappedException
import org.jetbrains.kotlin.test.backend.handlers.NoFir2IrCompilationErrorsHandler
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.SimpleDirective
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer
import org.jetbrains.kotlin.test.frontend.fir.TagsGeneratorChecker
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.BackendInputHandler
import org.jetbrains.kotlin.test.services.ServiceRegistrationData
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.moduleStructure

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

public actual fun TestConfigurationBuilder.registerTagsGeneratorChecker() {
    useAfterAnalysisCheckers(::TagsGeneratorCheckerCompat)
}

public class TagsGeneratorCheckerCompat(testServices: TestServices) :
    AfterAnalysisChecker(testServices) {
    public val underlying: TagsGeneratorChecker = TagsGeneratorChecker(testServices)

    override fun suppressIfNeeded(
        failedAssertions: List<WrappedException>
    ): List<WrappedException> = underlying.suppressIfNeeded(failedAssertions)

    override val order: Order
        get() = underlying.order

    override val additionalServices: List<ServiceRegistrationData>
        get() = underlying.additionalServices

    override val directiveContainers: List<DirectivesContainer>
        get() = underlying.directiveContainers

    override fun check(failedAssertions: List<WrappedException>) {
        if (DISABLE_GENERATED_FIR_TAGS in testServices.moduleStructure.allDirectives) return
        return underlying.check(failedAssertions)
    }
}

public actual fun NoIrCompilationErrorsHandlerCompat(
    testServices: TestServices
): BackendInputHandler<IrBackendInput> = NoFir2IrCompilationErrorsHandler(testServices)
