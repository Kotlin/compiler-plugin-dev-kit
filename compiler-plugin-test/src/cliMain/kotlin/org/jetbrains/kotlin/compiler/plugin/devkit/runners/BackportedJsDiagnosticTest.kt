/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.libraryProviderCtor
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
import org.jetbrains.kotlin.test.services.PhasedPipelineChecker
import org.jetbrains.kotlin.test.services.TestPhase

public abstract class BackportedJsDiagnosticTestBase(
    vararg config: TestConfigurationBuilder.() -> Unit,
    parser: FirParser = FirParser.LightTree,
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
            libraryProviderCtor?.let(::useAdditionalService)
        },
        *config,
    )

public abstract class BackportedJsDiagnosticWithBackendTestBase(
    vararg config: TestConfigurationBuilder.() -> Unit,
    parser: FirParser = FirParser.LightTree,
) :
    BackportedJsDiagnosticTestBase(
        {
            klibArtifactsHandlersStep {
                useHandlers(::KlibBackendDiagnosticsHandler)
            }
            defaultDirectives {
                TestPhaseDirectives.LATEST_PHASE_IN_PIPELINE with TestPhase.entries.last()
            }
        },
        *config,
        parser = parser,
    )
