// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler

import java.io.File
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.js.test.converters.Fir2IrCliWebFacade
import org.jetbrains.kotlin.js.test.converters.FirCliWebFacade
import org.jetbrains.kotlin.js.test.converters.FirKlibSerializerCliWebFacade
import org.jetbrains.kotlin.js.test.fir.AbstractFirJsTest
import org.jetbrains.kotlin.js.test.ir.commonConfigurationForJsTest
import org.jetbrains.kotlin.platform.wasm.WasmTarget
import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.WrappedException
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor.SuppressionChecker
import org.jetbrains.kotlin.test.backend.handlers.IrTextDumpHandler
import org.jetbrains.kotlin.test.backend.handlers.NoIrCompilationErrorsHandler
import org.jetbrains.kotlin.test.backend.handlers.findByPath
import org.jetbrains.kotlin.test.backend.ir.BackendCliJvmFacade
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.configuration.commonConfigurationForJvmTest
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives.FIR_IDENTICAL
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.frontend.fir.Fir2IrCliJvmFacade
import org.jetbrains.kotlin.test.frontend.fir.FirCliJvmFacade
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.TagsGeneratorChecker
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.AnalysisHandler
import org.jetbrains.kotlin.test.model.BackendKind
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider as KTStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.PhasedPipelineChecker
import org.jetbrains.kotlin.test.services.SourceFileProvider
import org.jetbrains.kotlin.test.services.TestPhase
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.kotlin.utils.bind

class MetroDiagnosticsCompatImpl : MetroDiagnosticsCompat {
  override fun SourceFileProvider.getContentOfSourceFileCompat(file: TestFile): String =
    getContentOfSourceFile(file)

  override fun irTextDumpHandlerCtor(
    testServices: TestServices,
    backendKind: BackendKind<IrBackendInput>,
  ): IrTextDumpHandler = IrTextDumpHandler(testServices, backendKind)

  override fun mainFirFilesCompat(info: FirOutputArtifact): Map<TestFile, FirFile> =
    info.mainFirFiles

  override fun severityToStringCompat(severity: Severity): String =
    AnalyzerWithCompilerReport.convertSeverity(severity).toString().toLowerCaseAsciiOnly()

  override fun irDiagnosticsForFileCompat(
    info: IrBackendInput,
    file: TestFile,
    testServices: TestServices,
  ): List<KtDiagnostic>? {
    val byPath = info.diagnosticReporter.diagnosticsByFilePath
    return file.findByPath(testServices) { byPath[it] }
  }

  override val noIrCompilationErrorsHandlerCtor: Constructor<AnalysisHandler<IrBackendInput>> =
    ::NoIrCompilationErrorsHandler

  override val suppressionCheckerCtor: Constructor<SuppressionChecker> =
    ::SuppressionChecker.bind(null, null)

  override val tagsGeneratorCheckerHandler: Constructor<AnalysisHandler<FirOutputArtifact>>? =
    ::TagsGeneratorChecker

  override val tagsGeneratorCheckerAfterAnalysis: Constructor<AfterAnalysisChecker>? = null

  override fun TestConfigurationBuilder.useIrDumpFailureSuppressorsCompat() {
    useAfterAnalysisCheckers(
      ::BlackBoxCodegenSuppressor,
      ::PhasedPipelineChecker.bind(TestPhase.BACKEND),
    )
  }

  override fun TestConfigurationBuilder.usePhasedPipelineFailureSuppressorCompat() {
    useAfterAnalysisCheckers(::PhasedPipelineChecker)
  }

  override fun RegisteredDirectivesBuilder.firIdenticalCompat() {
    +FIR_IDENTICAL
  }

  override fun createKotlinJsBoxTestBase(): AbstractKotlinCompilerTest =
    AbstractFirJsTest(
      pathToTestDir = "compiler-tests/src/test/data/box",
      testGroupOutputDirPrefix = "box/js/",
      parser = FirParser.LightTree,
    )

  override fun createStandardLibrariesPathProvider(
    from: KTStandardLibrariesPathProvider
  ): KotlinStandardLibrariesPathProvider =
    object : KotlinStandardLibrariesPathProvider {
      override fun runtimeJarForTests(): File = from.runtimeJarForTests()

      override fun runtimeJarForTestsWithJdk8(): File = from.runtimeJarForTestsWithJdk8()

      override fun minimalRuntimeJarForTests(): File = from.minimalRuntimeJarForTests()

      override fun reflectJarForTests(): File = from.reflectJarForTests()

      override fun kotlinTestJarForTests(): File = from.kotlinTestJarForTests()

      override fun scriptRuntimeJarForTests(): File = from.scriptRuntimeJarForTests()

      override fun jvmAnnotationsForTests(): File = from.jvmAnnotationsForTests()

      override fun getAnnotationsJar(): File = from.getAnnotationsJar()

      override fun fullJsStdlib(): File = from.fullJsStdlib()

      override fun defaultJsStdlib(): File = from.defaultJsStdlib()

      override fun kotlinTestJsKLib(): File = from.kotlinTestJsKLib()

      override fun commonStdlibForTests(): File = from.commonStdlibForTests()

      override fun scriptingPluginFilesForTests(): Collection<File> =
        from.scriptingPluginFilesForTests()

      override fun webStdlibForTests(): File = error("Unavailable in this version")

      override fun fullWasmStdlib(target: WasmTarget): File = error("Unavailable in this version")

      override fun kotlinTestWasmKLib(target: WasmTarget): File =
        error("Unavailable in this version")
    }

  override fun createKotlinStandardLibrariesPathProvider(
    from: KotlinStandardLibrariesPathProvider
  ): KTStandardLibrariesPathProvider =
    object : KTStandardLibrariesPathProvider(), KotlinStandardLibrariesPathProvider by from {}

  override fun TestConfigurationBuilder.commonConfigurationForJsTest() =
    commonConfigurationForJsTest(
      targetFrontend = FrontendKinds.FIR,
      frontendFacade = ::FirCliWebFacade,
      frontendToIrConverter = ::Fir2IrCliWebFacade,
      serializerFacade = ::FirKlibSerializerCliWebFacade,
    )

  override fun TestConfigurationBuilder.setupJvmPipelineSteps(parser: FirParser) =
    commonConfigurationForJvmTest(
      targetFrontend = FrontendKinds.FIR,
      frontendFacade = ::FirCliJvmFacade,
      frontendToBackendConverter = ::Fir2IrCliJvmFacade,
      backendFacade = ::BackendCliJvmFacade,
    )

  override fun afterAnalysisChecker(
    testServices: TestServices,
    directives: List<DirectivesContainer>?,
    check: context(TestServices) (thereWereFailures: Boolean) -> Unit,
  ): AfterAnalysisChecker =
    object : AfterAnalysisChecker(testServices) {
      override fun check(failedAssertions: List<WrappedException>) =
        check(testServices, failedAssertions.isNotEmpty())
    }
}
