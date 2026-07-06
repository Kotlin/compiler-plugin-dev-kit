package dev.zacsweers.metro.compiler

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.Constructor2
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.backend.handlers.DxCheckerHandler
import org.jetbrains.kotlin.test.backend.handlers.JvmBackendDiagnosticsHandler
import org.jetbrains.kotlin.test.backend.handlers.NoFirCompilationErrorsHandler
import org.jetbrains.kotlin.test.backend.handlers.NoJvmSpecificCompilationErrorsHandler
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.CompilerStepsNames.FIR_HANDLERS_STEP_NAME
import org.jetbrains.kotlin.test.builders.CompilerStepsNames.JVM_ARTIFACTS_HANDLERS_STEP_NAME
import org.jetbrains.kotlin.test.builders.CompilerStepsNames.KLIB_ARTIFACTS_HANDLERS_STEP_NAME
import org.jetbrains.kotlin.test.builders.CompilerStepsNames.LOWERED_IR_HANDLERS_STEP_NAME
import org.jetbrains.kotlin.test.builders.CompilerStepsNames.RAW_IR_HANDLERS_STEP_NAME
import org.jetbrains.kotlin.test.builders.DefaultsProviderBuilder
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirCfgConsistencyHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirCfgDumpHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirDiagnosticsHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirDumpHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirInferenceLogsHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirResolvedTypesVerifier
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirScopeDumpHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirVFirDumpHandler
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.AnalysisHandler
import org.jetbrains.kotlin.test.model.ArtifactKinds
import org.jetbrains.kotlin.test.model.BackendKind
import org.jetbrains.kotlin.test.model.BackendKinds
import org.jetbrains.kotlin.test.model.BinaryArtifacts
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.model.ResultingArtifact
import org.jetbrains.kotlin.test.model.TestArtifactKind
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.services.AbstractEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.AdditionalMetaInfoProcessor
import org.jetbrains.kotlin.test.services.AdditionalSourceProvider
import org.jetbrains.kotlin.test.services.CompilationStage
import org.jetbrains.kotlin.test.services.DefaultsDsl
import org.jetbrains.kotlin.test.services.MetaTestConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.ServiceRegistrationData
import org.jetbrains.kotlin.test.services.SourceFilePreprocessor
import org.jetbrains.kotlin.test.services.TestService
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.service

@DefaultsDsl
interface TestConfigurationBuilder {
  fun configureUsing(testBase: AbstractKotlinCompilerTest)

  fun defaultDirectives(init: RegisteredDirectivesBuilder.() -> Unit)

  fun globalDefaults(init: DefaultsProviderBuilder.() -> Unit)

  fun useAfterAnalysisCheckers(
    vararg checkers: Constructor<AfterAnalysisChecker>,
    insertAtFirst: Boolean = false,
  )

  fun useMetaTestConfigurators(vararg configurators: Constructor<MetaTestConfigurator>)

  fun useSourcePreprocessor(
    vararg preprocessors: Constructor<SourceFilePreprocessor>,
    needToPrepend: Boolean = false,
  )

  fun useDirectives(vararg directives: DirectivesContainer)

  fun useConfigurators(
    vararg environmentConfigurators: Constructor<AbstractEnvironmentConfigurator>
  )

  fun useCustomRuntimeClasspathProviders(vararg provider: Constructor<RuntimeClasspathProvider>)

  fun useAdditionalSourceProviders(vararg providers: Constructor<AdditionalSourceProvider>)

  fun useMetaInfoProcessors(vararg updaters: Constructor<AdditionalMetaInfoProcessor>)

  fun useAdditionalServices(vararg serviceRegistrationData: ServiceRegistrationData)

  fun enableMetaInfoHandler()

  fun <InputArtifact, InputArtifactKind> namedHandlersStep(
    name: String,
    artifactKind: InputArtifactKind,
    compilationStage: CompilationStage,
    vararg constructor: Constructor<AnalysisHandler<InputArtifact>>,
  )
    where
      InputArtifact : ResultingArtifact<InputArtifact>,
      InputArtifactKind : TestArtifactKind<InputArtifact>

  fun <InputArtifact, InputArtifactKind> configureNamedHandlersStep(
    name: String,
    artifactKind: InputArtifactKind,
    skipMissingStep: Boolean = false,
    isFirst: Boolean = false,
    vararg constructor: Constructor<AnalysisHandler<InputArtifact>>,
  )
    where
      InputArtifact : ResultingArtifact<InputArtifact>,
      InputArtifactKind : TestArtifactKind<InputArtifact>
}

fun TestConfigurationBuilder.configureFirParser(firParser: FirParser) = defaultDirectives {
  FirDiagnosticsDirectives.FIR_PARSER with firParser
}

fun TestConfigurationBuilder.klibArtifactsHandlersStep(
  vararg constructor: Constructor<AnalysisHandler<BinaryArtifacts.KLib>>
) =
  namedHandlersStep(
    KLIB_ARTIFACTS_HANDLERS_STEP_NAME,
    ArtifactKinds.KLib,
    CompilationStage.FIRST,
    constructor = constructor,
  )

fun TestConfigurationBuilder.configureFirHandlersStep(
  vararg constructor: Constructor<AnalysisHandler<FirOutputArtifact>>,
  isFirst: Boolean = false,
) =
  configureNamedHandlersStep(
    FIR_HANDLERS_STEP_NAME,
    FrontendKinds.FIR,
    skipMissingStep = false,
    isFirst = isFirst,
    constructor = constructor,
  )

fun TestConfigurationBuilder.configureIrHandlersStep(
  vararg constructor: Constructor<AnalysisHandler<IrBackendInput>>,
  isFirst: Boolean = false,
) =
  configureNamedHandlersStep(
    RAW_IR_HANDLERS_STEP_NAME,
    BackendKinds.IrBackend,
    skipMissingStep = false,
    isFirst = isFirst,
    constructor = constructor,
  )

fun TestConfigurationBuilder.configureIrHandlersStep(
  vararg constructor: Constructor2<BackendKind<IrBackendInput>, AnalysisHandler<IrBackendInput>>,
  isFirst: Boolean = false,
) =
  configureNamedHandlersStep(
    RAW_IR_HANDLERS_STEP_NAME,
    BackendKinds.IrBackend,
    skipMissingStep = false,
    isFirst = isFirst,
    constructor =
      constructor
        .map<_, Constructor<AnalysisHandler<IrBackendInput>>> { ctor ->
          { ctor(it, BackendKinds.IrBackend) }
        }
        .toTypedArray(),
  )

fun TestConfigurationBuilder.configureLoweredIrHandlersStep(
  vararg constructor: Constructor<AnalysisHandler<IrBackendInput>>,
  isFirst: Boolean = false,
) =
  configureNamedHandlersStep(
    LOWERED_IR_HANDLERS_STEP_NAME,
    BackendKinds.IrBackend,
    skipMissingStep = false,
    isFirst = isFirst,
    constructor = constructor,
  )

fun TestConfigurationBuilder.configureJvmArtifactsHandlersStep(
  vararg constructor: Constructor<AnalysisHandler<BinaryArtifacts.Jvm>>,
  isFirst: Boolean = false,
) =
  configureNamedHandlersStep(
    JVM_ARTIFACTS_HANDLERS_STEP_NAME,
    ArtifactKinds.Jvm,
    skipMissingStep = false,
    isFirst = isFirst,
    constructor = constructor,
  )

/** Add all handlers usually used in codegen tests */
fun TestConfigurationBuilder.commonHandlersForCodegenTest() {
  commonFirHandlersForCodegenTest()
  commonBackendHandlersForCodegenTest()
}

/**
 * Adds a handler which checks that there are no compilation errors reported at the K2 frontend step
 */
fun TestConfigurationBuilder.commonFirHandlersForCodegenTest() =
  configureFirHandlersStep(::NoFirCompilationErrorsHandler)

/** Add JVM artifact handlers usually used in codegen tests */
fun TestConfigurationBuilder.commonBackendHandlersForCodegenTest(
  includeNoCompilationErrorsHandler: Boolean = true
) {
  configureJvmArtifactsHandlersStep(::JvmBackendDiagnosticsHandler, ::DxCheckerHandler)
  if (includeNoCompilationErrorsHandler) {
    configureJvmArtifactsHandlersStep(::NoJvmSpecificCompilationErrorsHandler)
  }
}

fun TestConfigurationBuilder.additionalK2ConfigurationForIrTextTest(parser: FirParser) {
  configureFirParser(parser)

  configureFirHandlersStep(
    ::FirDumpHandler,
    ::FirScopeDumpHandler,
    isFirst = true,
  )
}

inline fun <reified T : TestService> TestConfigurationBuilder.useAdditionalService(
  noinline serviceConstructor: (TestServices) -> T
) {
  useAdditionalServices(service(serviceConstructor))
}

fun TestConfigurationBuilder.setupHandlersForDiagnosticTest() =
  configureFirHandlersStep(
    ::FirDiagnosticsHandler,
    ::FirDumpHandler,
    ::FirCfgDumpHandler,
    ::FirVFirDumpHandler,
    ::FirInferenceLogsHandler,
    ::FirCfgConsistencyHandler,
    ::FirResolvedTypesVerifier,
    ::FirScopeDumpHandler,
  )
