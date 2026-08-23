/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.setupJvmPipelineSteps
import org.jetbrains.kotlin.compiler.plugin.devkit.useFailureSuppressorsCompat
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor.SuppressionChecker
import org.jetbrains.kotlin.test.backend.handlers.NoFirCompilationErrorsHandler
import org.jetbrains.kotlin.test.backend.handlers.NoIrCompilationErrorsHandler
import org.jetbrains.kotlin.test.backend.ir.IrDiagnosticsHandler
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.builders.configureIrHandlersStep
import org.jetbrains.kotlin.test.builders.configureJvmArtifactsHandlersStep
import org.jetbrains.kotlin.test.configuration.commonBackendHandlersForCodegenTest
import org.jetbrains.kotlin.test.configuration.configureCommonDiagnosticTestPaths
import org.jetbrains.kotlin.test.directives.DiagnosticsDirectives.DIAGNOSTICS
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives.LANGUAGE
import org.jetbrains.kotlin.test.directives.TestPhaseDirectives.LATEST_PHASE_IN_PIPELINE
import org.jetbrains.kotlin.test.directives.configureFirParser
import org.jetbrains.kotlin.test.frontend.fir.FirFailingTestSuppressor
import org.jetbrains.kotlin.test.frontend.fir.TagsGeneratorChecker
import org.jetbrains.kotlin.test.frontend.fir.handlers.NonSourceErrorMessagesHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.PsiLightTreeMetaInfoProcessor
import org.jetbrains.kotlin.test.services.PhasedPipelineChecker
import org.jetbrains.kotlin.test.services.TestPhase
import org.jetbrains.kotlin.utils.bind

public abstract class BackportedFirPhasedDiagnosticTest(
    parser: FirParser,
    vararg config: TestConfigurationBuilder.() -> Unit,
) :
    DevKitTest(
        {
            defaultDirectives {
                LATEST_PHASE_IN_PIPELINE with TestPhase.BACKEND
                LANGUAGE + "+EnableDfaWarningsInK2"
                DIAGNOSTICS with DEFAULT_UNUSED_DIAGNOSTICS.map { "-$it" }
            }

            // CHANGED: should be setupJvmPipelineStepsWithoutCompilationErrorHandlers
            setupJvmPipelineSteps(parser)
            configureFirParser(parser)
            configureCommonDiagnosticTestPaths()

            setupHandlersForDiagnosticTest()
            configureFirHandlersStep {
                useHandlers(::TagsGeneratorChecker, ::NoFirCompilationErrorsHandler)
            }

            configureIrHandlersStep {
                useHandlers(
                    ::IrDiagnosticsHandler,
                    ::NoIrCompilationErrorsHandler,
                )
            }

            configureJvmArtifactsHandlersStep {
                commonBackendHandlersForCodegenTest(includeNoCompilationErrorsHandler = false)
            }

            useMetaInfoProcessors(::PsiLightTreeMetaInfoProcessor)
            useAfterAnalysisCheckers(::NonSourceErrorMessagesHandler)
            useFailureSuppressorsCompat(::PhasedPipelineChecker, ::FirFailingTestSuppressor)
            enableMetaInfoHandler()
            useAdditionalService<SuppressionChecker>(::SuppressionChecker.bind(null, null))
        },
        *config,
        targetBackend = TargetBackend.JVM_IR,
    )
