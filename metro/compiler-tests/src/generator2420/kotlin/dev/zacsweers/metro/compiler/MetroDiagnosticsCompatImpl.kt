// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler

import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.js.test.runners.AbstractJsTest
import org.jetbrains.kotlin.js.test.runners.commonConfigurationForJsTest
import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor.SuppressionChecker
import org.jetbrains.kotlin.test.backend.handlers.IrTextDumpHandler
import org.jetbrains.kotlin.test.backend.handlers.NoIrCompilationErrorsHandler
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.configuration.setupJvmPipelineSteps
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.AnalysisHandler
import org.jetbrains.kotlin.test.model.BackendKind
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider as KTStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.PhasedPipelineChecker
import org.jetbrains.kotlin.test.services.SourceFileProvider
import org.jetbrains.kotlin.test.services.TestPhase
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.utils.bind

// TODO split MetroDiagnosticsCompat into 2 interfaces, and only provide service here for one
// Stubs: on Kotlin 2.4.20+ the framework natively produces post-KT-85292 file naming and
// `line:column` format, so [NEEDS_LEGACY_GOLDEN_NAMING] is false and `AbstractDiagnosticTest`
// just calls `super.configure`. None of these helpers are reachable at runtime here -- they
// only exist so the call sites in src/test resolve at compile time.

private const val UNREACHABLE = "Metro diagnostic shim should not run on Kotlin 2.4.20+."

class MetroDiagnosticsCompatImpl : MetroDiagnosticsCompat {
  override fun SourceFileProvider.getContentOfSourceFileCompat(file: TestFile): String =
    error(UNREACHABLE)

  override fun irTextDumpHandlerCtor(
    testServices: TestServices,
    backendKind: BackendKind<IrBackendInput>,
  ): IrTextDumpHandler = IrTextDumpHandler(testServices, backendKind)

  override fun mainFirFilesCompat(info: FirOutputArtifact): Map<TestFile, FirFile> =
    error(UNREACHABLE)

  override fun severityToStringCompat(severity: Severity): String = error(UNREACHABLE)

  override fun irDiagnosticsForFileCompat(
    info: IrBackendInput,
    file: TestFile,
    testServices: TestServices,
  ): List<KtDiagnostic>? = error(UNREACHABLE)

  override val noIrCompilationErrorsHandlerCtor: Constructor<AnalysisHandler<IrBackendInput>> =
    ::NoIrCompilationErrorsHandler

  override val suppressionCheckerCtor: Constructor<SuppressionChecker> =
    ::SuppressionChecker.bind(null, null)

  override val tagsGeneratorCheckerHandler: Constructor<AnalysisHandler<FirOutputArtifact>>? = null

  override val tagsGeneratorCheckerAfterAnalysis: Constructor<AfterAnalysisChecker>? = null

  override fun TestConfigurationBuilder.useIrDumpFailureSuppressorsCompat() =
    useVersionSpecificConfig {
      useFailureSuppressors(
        ::BlackBoxCodegenSuppressor,
        ::PhasedPipelineChecker.bind(TestPhase.BACKEND),
      )
    }

  override fun TestConfigurationBuilder.usePhasedPipelineFailureSuppressorCompat() =
    useVersionSpecificConfig {
      useFailureSuppressors(::PhasedPipelineChecker)
    }

  override fun RegisteredDirectivesBuilder.firIdenticalCompat() = Unit

  override fun createKotlinJsBoxTestBase(): AbstractKotlinCompilerTest =
    object :
      AbstractJsTest(
        pathToTestDir = "compiler-tests/src/test/data/box",
        testGroupOutputDirPrefix = "box/js/",
        parser = FirParser.LightTree,
      ) {}

  private fun TestConfigurationBuilder.useVersionSpecificConfig(
    config: org.jetbrains.kotlin.test.builders.TestConfigurationBuilder.() -> Unit
  ) =
    configureUsing(
      object : AbstractKotlinCompilerTest() {
        override fun configure(
          builder: org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
        ) = config(builder)
      }
    )

  override fun createKotlinStandardLibrariesPathProvider(
    from: KTStandardLibrariesPathProvider
  ): KotlinStandardLibrariesPathProvider =
    object : KTStandardLibrariesPathProvider by from, KotlinStandardLibrariesPathProvider {}

  override fun TestConfigurationBuilder.commonConfigurationForJsTest() = useVersionSpecificConfig {
    commonConfigurationForJsTest()
  }

  override fun TestConfigurationBuilder.setupJvmPipelineSteps(parser: FirParser) =
    useVersionSpecificConfig {
      setupJvmPipelineSteps(parser)
    }
}
