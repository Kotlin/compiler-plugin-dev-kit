/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.handlers.BackportedFirDumpHandler
import org.jetbrains.kotlin.compiler.plugin.devkit.handlers.BackportedIrTextDumpHandler
import org.jetbrains.kotlin.compiler.plugin.devkit.setupJvmPipelineSteps
import org.jetbrains.kotlin.compiler.plugin.devkit.useFailureSuppressorsCompat
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor
import org.jetbrains.kotlin.test.backend.handlers.BytecodeListingHandler
import org.jetbrains.kotlin.test.backend.handlers.IrTreeVerifierHandler
import org.jetbrains.kotlin.test.backend.ir.IrConstCheckerHandler
import org.jetbrains.kotlin.test.backend.ir.IrDiagnosticsHandler
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.builders.configureIrHandlersStep
import org.jetbrains.kotlin.test.builders.configureJvmArtifactsHandlersStep
import org.jetbrains.kotlin.test.configuration.configureBlackBoxTestSettings
import org.jetbrains.kotlin.test.configuration.configureCommonHandlersForBoxTest
import org.jetbrains.kotlin.test.configuration.configureJvmBoxCodegenSettings
import org.jetbrains.kotlin.test.directives.configureFirParser
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirCfgDumpHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirDiagnosticsHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirResolvedTypesVerifier
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirScopeDumpHandler
import org.jetbrains.kotlin.test.services.sourceProviders.MainFunctionForBlackBoxTestsSourceProvider

public abstract class BackportedJvmBlackBoxCodegenTestBase(
    vararg config: TestConfigurationBuilder.() -> Unit,
    parser: FirParser = FirParser.LightTree,
) :
    DevKitTest(
        {
            setupJvmPipelineSteps(parser)
            configureFirParser(parser)

            configureFirHandlersStep {
                useHandlersAtFirst(
                    ::BackportedFirDumpHandler,
                    ::FirCfgDumpHandler,
                    ::FirResolvedTypesVerifier,
                    ::FirScopeDumpHandler,
                    ::FirDiagnosticsHandler,
                )
            }

            configureIrHandlersStep {
                useHandlers(
                    ::IrDiagnosticsHandler,
                    ::IrConstCheckerHandler,
                )
            }

            forTestsMatching("compiler/testData/codegen/*") {
                // CHANGED: ignored for now
                // useAfterAnalysisCheckers(::PureJvmCodegenBoxTestChecker)
                // useAfterAnalysisCheckers(::JdkKindBoxTestChecker)
            }

            configureCommonHandlersForBoxTest()
            configureDumpHandlersForCodegenTestDevKit()
            configureBlackBoxTestSettings()

            useAdditionalSourceProviders(::MainFunctionForBlackBoxTestsSourceProvider)
            useFailureSuppressorsCompat(::BlackBoxCodegenSuppressor)

            configureJvmBoxCodegenSettings(includeAllDumpHandlers = true)
            enableMetaInfoHandler()
        },
        *config,
        targetBackend = TargetBackend.JVM_IR,
    )

/** Adds IR and Bytecode dump handlers to the test */
public fun TestConfigurationBuilder.configureDumpHandlersForCodegenTestDevKit(
    includeAllDumpHandlers: Boolean = true
) {
    configureIrHandlersStep {
        useHandlers(
            ::IrTreeVerifierHandler,
            ::BackportedIrTextDumpHandler,
        )
    }
    configureJvmArtifactsHandlersStep {
        if (includeAllDumpHandlers) {
            useHandlers(::BytecodeListingHandler)
        }
    }
}
