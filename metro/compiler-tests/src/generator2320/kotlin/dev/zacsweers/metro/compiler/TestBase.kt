package dev.zacsweers.metro.compiler

import java.io.File
import org.jetbrains.kotlin.js.test.runners.commonConfigurationForJsTest
import org.jetbrains.kotlin.platform.wasm.WasmTarget
import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.ir.BackendCliJvmFacade
import org.jetbrains.kotlin.test.builders.DefaultsProviderBuilder
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder as TCBuilder
import org.jetbrains.kotlin.test.configuration.commonConfigurationForJvmTest
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.frontend.fir.Fir2IrCliJvmFacade
import org.jetbrains.kotlin.test.frontend.fir.FirCliJvmFacade
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.AnalysisHandler
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.model.ResultingArtifact
import org.jetbrains.kotlin.test.model.TestArtifactKind
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.services.AbstractEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.AdditionalMetaInfoProcessor
import org.jetbrains.kotlin.test.services.AdditionalSourceProvider
import org.jetbrains.kotlin.test.services.CompilationStage
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider as KTStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.MetaTestConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.ServiceRegistrationData
import org.jetbrains.kotlin.test.services.SourceFilePreprocessor

abstract class TestBase : AbstractKotlinCompilerTest() {
  open val targetBackend: TargetBackend? = null

  final override fun createKotlinStandardLibrariesPathProvider(): KTStandardLibrariesPathProvider =
    object :
      KTStandardLibrariesPathProvider,
      KotlinStandardLibrariesPathProvider by createStandardLibrariesPathProvider() {}

  open fun createStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider {
    val s = super.createKotlinStandardLibrariesPathProvider()
    return object : KTStandardLibrariesPathProvider by s, KotlinStandardLibrariesPathProvider {
      override fun fullWasmStdlib(target: WasmTarget): File = error("Not available in this version")

      override fun kotlinTestWasmKLib(target: WasmTarget): File =
        error("Not available in this version")
    }
  }

  override fun configure(builder: TCBuilder) =
    TestConfigurationBuilderImpl(builder).run {
      configure(this)
      postConfigure(this)
    }

  abstract fun configure(builder: TestConfigurationBuilder)
}

class TestConfigurationBuilderImpl(val builder: TCBuilder) : TestConfigurationBuilder {
  override fun configureUsing(testBase: AbstractKotlinCompilerTest) = testBase.configure(builder)

  override fun commonConfigurationForJsTest() = builder.commonConfigurationForJsTest()

  override fun setupJvmPipelineSteps(parser: FirParser) =
    builder.commonConfigurationForJvmTest(
      targetFrontend = FrontendKinds.FIR,
      frontendFacade = ::FirCliJvmFacade,
      frontendToBackendConverter = ::Fir2IrCliJvmFacade,
      backendFacade = ::BackendCliJvmFacade,
    )

  override fun defaultDirectives(init: RegisteredDirectivesBuilder.() -> Unit) =
    builder.defaultDirectives(init)

  override fun globalDefaults(init: DefaultsProviderBuilder.() -> Unit) =
    builder.globalDefaults(init)

  override fun useAfterAnalysisCheckers(
    vararg checkers: Constructor<AfterAnalysisChecker>,
    insertAtFirst: Boolean,
  ) = builder.useAfterAnalysisCheckers(checkers = checkers, insertAtFirst = insertAtFirst)

  override fun useMetaTestConfigurators(vararg configurators: Constructor<MetaTestConfigurator>) =
    builder.useMetaTestConfigurators(*configurators)

  override fun useSourcePreprocessor(
    vararg preprocessors: Constructor<SourceFilePreprocessor>,
    needToPrepend: Boolean,
  ) = builder.useSourcePreprocessor(preprocessors = preprocessors, needToPrepend = needToPrepend)

  override fun useDirectives(vararg directives: DirectivesContainer) =
    builder.useDirectives(directives = directives)

  override fun useConfigurators(
    vararg environmentConfigurators: Constructor<AbstractEnvironmentConfigurator>
  ) = builder.useConfigurators(environmentConfigurators = environmentConfigurators)

  override fun useCustomRuntimeClasspathProviders(
    vararg provider: Constructor<RuntimeClasspathProvider>
  ) = builder.useCustomRuntimeClasspathProviders(provider = provider)

  override fun useAdditionalSourceProviders(
    vararg providers: Constructor<AdditionalSourceProvider>
  ) = builder.useAdditionalSourceProviders(providers = providers)

  override fun useMetaInfoProcessors(vararg updaters: Constructor<AdditionalMetaInfoProcessor>) =
    builder.useMetaInfoProcessors(updaters = updaters)

  override fun useAdditionalServices(vararg serviceRegistrationData: ServiceRegistrationData) =
    builder.useAdditionalServices(serviceRegistrationData = serviceRegistrationData)

  override fun enableMetaInfoHandler() = builder.enableMetaInfoHandler()

  override fun <
    InputArtifact : ResultingArtifact<InputArtifact>,
    InputArtifactKind : TestArtifactKind<InputArtifact>,
  > namedHandlersStep(
    name: String,
    artifactKind: InputArtifactKind,
    compilationStage: CompilationStage,
    vararg constructor: Constructor<AnalysisHandler<InputArtifact>>,
  ) {
    builder.namedHandlersStep(name, artifactKind, compilationStage) {
      useHandlers(constructor = constructor)
    }
  }

  override fun <
    InputArtifact : ResultingArtifact<InputArtifact>,
    InputArtifactKind : TestArtifactKind<InputArtifact>,
  > configureNamedHandlersStep(
    name: String,
    artifactKind: InputArtifactKind,
    skipMissingStep: Boolean,
    isFirst: Boolean,
    vararg constructor: Constructor<AnalysisHandler<InputArtifact>>,
  ) =
    builder.configureNamedHandlersStep(name, artifactKind, skipMissingStep) {
      if (isFirst) useHandlersAtFirst(constructor = constructor)
      else useHandlers(constructor = constructor)
    }
}
