package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import java.lang.Boolean.getBoolean
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitTestCompat.Companion.versionDumpClassifier
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.js.test.handlers.JsAstHandler
import org.jetbrains.kotlin.js.test.handlers.JsBoxRunner
import org.jetbrains.kotlin.js.test.handlers.JsIrRecompiledArtifactsIdentityHandler
import org.jetbrains.kotlin.js.test.handlers.JsSourceMapPathRewriter
import org.jetbrains.kotlin.js.test.handlers.NodeJsGeneratorHandler
import org.jetbrains.kotlin.js.test.runners.AbstractJsBlackBoxCodegenTestBase.JsBackendFacades
import org.jetbrains.kotlin.js.test.runners.commonConfigurationForJsBackendFirstStageTest
import org.jetbrains.kotlin.js.test.runners.setUpDefaultDirectivesForJsBoxTest
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.builders.configureJsArtifactsHandlersStep
import org.jetbrains.kotlin.test.builders.configureLoweredIrHandlersStep
import org.jetbrains.kotlin.test.builders.deserializedIrHandlersStep
import org.jetbrains.kotlin.test.builders.jsArtifactsHandlersStep
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.DiagnosticsDirectives.DIAGNOSTICS
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.JsEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives.LANGUAGE
import org.jetbrains.kotlin.test.directives.model.ValueDirective
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirCfgConsistencyHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirCfgDumpHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirDumpHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirResolvedTypesVerifier
import org.jetbrains.kotlin.test.model.ResultingArtifact
import org.jetbrains.kotlin.test.runners.AbstractFirBlackBoxCodegenTestSpecBase
import org.jetbrains.kotlin.utils.addToStdlib.runIf

open class DevKitJvmBoxTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    parser: FirParser = FirParser.LightTree,
) :
    DevKitTest(
        object : AbstractFirBlackBoxCodegenTestSpecBase(parser) {},
        {
            defaultDirectives {
                versionDumpClassifier()
                boxTestDefaults()
                jvmDefaults()
            }
        },
        *config,
    )

open class DevKitJsBoxTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    targetBackend: TargetBackend = TargetBackend.JS_IR,
    val parser: FirParser = FirParser.LightTree,
) : DevKitTest(targetBackend = targetBackend) {
    init {
        configure {
            // From AbstractJsBlackBoxCodegenTestBase
            commonConfigurationForJsBlackBoxCodegenTest()
            if (enableBoxHandlers) configureJsBoxHandlersWithoutTs()
            // From AbstractJsTest
            setUpDefaultDirectivesForJsBoxTest(parser)
            configureFirHandlersStep {
                useHandlers(
                    ::FirDumpHandler,
                    ::FirCfgDumpHandler,
                    ::FirCfgConsistencyHandler,
                    ::FirResolvedTypesVerifier,
                )
            }
            defaultDirectives {
                LANGUAGE with
                    listOf(
                        "-${LanguageFeature.IrIntraModuleInlinerBeforeKlibSerialization.name}",
                        "-${LanguageFeature.IrCrossModuleInlinerBeforeKlibSerialization.name}",
                    )
            }

            configureJsArtifactsHandlersStep {
                useHandlers(::JsIrRecompiledArtifactsIdentityHandler)
            }
            defaultDirectives {
                versionDumpClassifier()
                boxTestDefaults()
            }
        }
        configure(*config)
    }

    open val backendFacades: JsBackendFacades
        get() = JsBackendFacades.WithRecompilation

    protected open val customIgnoreDirective: ValueDirective<TargetBackend>?
        get() = null

    protected open val additionalIgnoreDirectives: List<ValueDirective<TargetBackend>>?
        get() = null

    protected open val enableBoxHandlers: Boolean
        get() = true

    protected fun TestConfigurationBuilder.commonConfigurationForJsBlackBoxCodegenTest() {
        commonConfigurationForJsBackendFirstStageTest(
            customIgnoreDirective = customIgnoreDirective,
            additionalIgnoreDirectives = additionalIgnoreDirectives,
        )
        commonConfigurationForJsBackendSecondStageTestWithoutTestDir(backendFacades)

        defaultDirectives {
            DIAGNOSTICS with "-warnings"
        }
    }
}

private fun RegisteredDirectivesBuilder.boxTestDefaults() {
    +CodegenTestDirectives.DUMP_IR
    +FirDiagnosticsDirectives.FIR_DUMP
}

/**
 * Sets up configuration for JS second compilation phase (compilation from KLib to JS code). First
 * compilation phase should be set up separately with
 * [commonConfigurationForJsBackendFirstStageTest]. The configuration includes all minimally
 * required services, handlers and defaults for such tests.
 */
private fun <FO : ResultingArtifact.FrontendOutput<FO>> TestConfigurationBuilder
    .commonConfigurationForJsBackendSecondStageTestWithoutTestDir(
    backendFacades: JsBackendFacades
) {
    val pathToRootOutputDir =
        System.getProperty("kotlin.js.test.root.out.dir")
            ?: error("'kotlin.js.test.root.out.dir' is not set")
    defaultDirectives {
        JsEnvironmentConfigurationDirectives.PATH_TO_ROOT_OUTPUT_DIR with pathToRootOutputDir
        +JsEnvironmentConfigurationDirectives.GENERATE_NODE_JS_RUNNER
        if (getBoolean("kotlin.js.ir.skipRegularMode"))
            +JsEnvironmentConfigurationDirectives.SKIP_REGULAR_MODE
        LANGUAGE with "+JsAllowValueClassesInExternals"
    }

    when (val backendFacades = backendFacades) {
        is JsBackendFacades.WithRecompilation -> {
            facadeStep(backendFacades.deserializerAndLoweringFacade)
            facadeStep(backendFacades.recompileFacade)
        }

        is JsBackendFacades.WithSeparatedDeserialization -> {
            configureLoweredIrHandlersStep { useHandlers(backendFacades.preSerializationHandler) }
            facadeStep(backendFacades.deserializerFacade)
            deserializedIrHandlersStep { useHandlers(backendFacades.postDeserializationHandler) }
        }
    }

    jsArtifactsHandlersStep {
        useHandlers(::JsSourceMapPathRewriter)
    }
}

/** Configures handlers for JS box testing */
private fun TestConfigurationBuilder.configureJsBoxHandlersWithoutTs(verifyJsAst: Boolean = true) {
    configureJsArtifactsHandlersStep {
        useHandlers(
            ::NodeJsGeneratorHandler,
            ::JsBoxRunner,
        )
        runIf(verifyJsAst) {
            useHandlers(::JsAstHandler)
        }
    }
}
