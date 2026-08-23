/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.FirDistinctSourceElementsHandler
import org.jetbrains.kotlin.compiler.plugin.devkit.handlers.BackportedFirDumpHandler
import org.jetbrains.kotlin.compiler.plugin.devkit.useFailureSuppressorsCompat
import org.jetbrains.kotlin.js.test.runners.commonConfigurationForJsTest
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor
import org.jetbrains.kotlin.test.backend.handlers.KlibBackendDiagnosticsHandler
import org.jetbrains.kotlin.test.backend.handlers.NoFirCompilationErrorsHandler
import org.jetbrains.kotlin.test.backend.ir.IrDiagnosticsHandler
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.builders.configureIrHandlersStep
import org.jetbrains.kotlin.test.builders.configureLoweredIrHandlersStep
import org.jetbrains.kotlin.test.builders.klibArtifactsHandlersStep
import org.jetbrains.kotlin.test.directives.ConfigurationDirectives
import org.jetbrains.kotlin.test.directives.DiagnosticsDirectives.DIAGNOSTICS
import org.jetbrains.kotlin.test.directives.TestPhaseDirectives
import org.jetbrains.kotlin.test.directives.configureFirParser
import org.jetbrains.kotlin.test.frontend.fir.FirFailingTestSuppressor
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirCfgConsistencyHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirCfgDumpHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirDiagnosticsHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirInferenceLogsHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirResolvedTypesVerifier
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirScopeDumpHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirVFirDumpHandler
import org.jetbrains.kotlin.test.services.LibraryProvider
import org.jetbrains.kotlin.test.services.PhasedPipelineChecker
import org.jetbrains.kotlin.test.services.TestPhase

public abstract class BackportedJsDiagnosticTestBase(
    parser: FirParser,
    vararg config: TestConfigurationBuilder.() -> Unit,
) :
    DevKitTest(
        {
            globalDefaults {
                targetBackend = TargetBackend.JS_IR
            }
            defaultDirectives {
                +ConfigurationDirectives.WITH_STDLIB
                DIAGNOSTICS with DEFAULT_UNUSED_DIAGNOSTICS.map { "-$it" }
            }

            commonConfigurationForJsTest()
            configureFirParser(parser)

            setupHandlersForDiagnosticTest()
            configureFirHandlersStep {
                useHandlers(::NoFirCompilationErrorsHandler)
            }
            configureIrHandlersStep {
                useHandlers(::IrDiagnosticsHandler)
            }
            configureLoweredIrHandlersStep {
                useHandlers(::IrDiagnosticsHandler)
            }

            useFailureSuppressorsCompat(
                ::PhasedPipelineChecker,
                ::BlackBoxCodegenSuppressor,
                ::FirFailingTestSuppressor,
            )
            enableMetaInfoHandler()
            useAdditionalService(::LibraryProvider)
        },
        *config,
    )

public abstract class BackportedJsDiagnosticWithBackendTestBase(
    parser: FirParser,
    vararg config: TestConfigurationBuilder.() -> Unit,
) :
    BackportedJsDiagnosticTestBase(
        parser,
        {
            klibArtifactsHandlersStep {
                useHandlers(::KlibBackendDiagnosticsHandler)
            }
            defaultDirectives {
                TestPhaseDirectives.LATEST_PHASE_IN_PIPELINE with TestPhase.BACKEND
            }
        },
        *config,
    )

/** The list of `UNUSED_*` diagnostics which are disabled by default within diagnostic tests. */
public val DEFAULT_UNUSED_DIAGNOSTICS: List<String> =
    listOf(
        "UNUSED_VARIABLE",
        "UNUSED_PARAMETER",
        "UNUSED_ANONYMOUS_PARAMETER",
        "UNUSED_DESTRUCTURED_PARAMETER_ENTRY",
        "UNUSED_TYPEALIAS_PARAMETER",
        "UNUSED_VALUE",
        "UNUSED_CHANGED_VALUE",
        "UNUSED_EXPRESSION",
        "UNUSED_LAMBDA_EXPRESSION",
    )

public fun TestConfigurationBuilder.setupHandlersForDiagnosticTest() {
    configureFirHandlersStep {
        useHandlers(
            ::FirDiagnosticsHandler,
            ::BackportedFirDumpHandler,
            ::FirCfgDumpHandler,
            ::FirVFirDumpHandler,
            ::FirInferenceLogsHandler,
            ::FirCfgConsistencyHandler,
            ::FirResolvedTypesVerifier,
            ::FirScopeDumpHandler,
            ::FirDistinctSourceElementsHandler,
        )
    }
}
