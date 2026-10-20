/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.handlers.BackportedFirDumpHandler
import org.jetbrains.kotlin.compiler.plugin.devkit.handlers.BackportedIrTextDumpHandler
import org.jetbrains.kotlin.js.test.handlers.JsIrRecompiledArtifactsIdentityHandler
import org.jetbrains.kotlin.js.test.runners.AbstractJsBlackBoxCodegenTestBase
import org.jetbrains.kotlin.js.test.runners.setUpDefaultDirectivesForJsBoxTest
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.handlers.IrPrettyKotlinDumpHandler
import org.jetbrains.kotlin.test.backend.handlers.IrSourceRangesDumpHandler
import org.jetbrains.kotlin.test.backend.handlers.IrTreeVerifierHandler
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.builders.configureIrHandlersStep
import org.jetbrains.kotlin.test.builders.configureJsArtifactsHandlersStep
import org.jetbrains.kotlin.test.directives.JsEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.directives.KlibAbiConsistencyDirectives.CHECK_SAME_ABI_AFTER_INLINING
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirCfgConsistencyHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirCfgDumpHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirResolvedTypesVerifier
import org.jetbrains.kotlin.test.services.configuration.JsEnvironmentConfigurator

public open class BackportedJsTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    pathToTestDir: String = "${JsEnvironmentConfigurator.TEST_DATA_DIR_PATH}/box/",
    testGroupOutputDirPrefix: String,
    targetBackend: TargetBackend = TargetBackend.JS_IR,
    parser: FirParser = FirParser.Psi,
) :
    DevKitTest(
        object :
            AbstractJsBlackBoxCodegenTestBase(
                targetBackend,
                pathToTestDir,
                testGroupOutputDirPrefix,
            ) {
            override val backendFacades: JsBackendFacades
                get() = JsBackendFacades.WithRecompilation
        },
        {
            setUpDefaultDirectivesForJsBoxTest(parser)
            defaultDirectives {
                +CHECK_SAME_ABI_AFTER_INLINING
            }
            configureFirHandlersStep {
                useHandlers(
                    ::BackportedFirDumpHandler,
                    ::FirCfgDumpHandler,
                    ::FirCfgConsistencyHandler,
                    ::FirResolvedTypesVerifier,
                )
            }
            setupIrTextDumpHandlers()
            configureJsArtifactsHandlersStep {
                useHandlers(
                    ::JsIrRecompiledArtifactsIdentityHandler
                    // CHANGED: doesn't seem relevant for plugin devs. Added in 2.4.20-Beta2
                    // ::JsSizeHandler,
                )
            }

            forTestsMatching(
                "${JsEnvironmentConfigurator.TEST_DATA_DIR_PATH}/box/closure/inlineAnonymousFunctions/*"
            ) {
                defaultDirectives {
                    +JsEnvironmentConfigurationDirectives.GENERATE_INLINE_ANONYMOUS_FUNCTIONS
                }
            }
        },
        *config,
    )

public fun TestConfigurationBuilder.setupIrTextDumpHandlers() {
    configureIrHandlersStep {
        useHandlers(
            ::BackportedIrTextDumpHandler,
            ::IrTreeVerifierHandler,
            ::IrPrettyKotlinDumpHandler,
            ::IrSourceRangesDumpHandler,
        )
    }
}
