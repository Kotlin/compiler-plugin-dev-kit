// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler

import java.util.*
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor.SuppressionChecker
import org.jetbrains.kotlin.test.backend.handlers.IrTextDumpHandler
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.AnalysisHandler
import org.jetbrains.kotlin.test.model.BackendKind
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider as KTStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.SourceFileProvider
import org.jetbrains.kotlin.test.services.TestServices

interface MetroDiagnosticsCompat {
  fun SourceFileProvider.getContentOfSourceFileCompat(file: TestFile): String

  fun irTextDumpHandlerCtor(
    testServices: TestServices,
    backendKind: BackendKind<IrBackendInput>,
  ): IrTextDumpHandler

  fun mainFirFilesCompat(info: FirOutputArtifact): Map<TestFile, FirFile>

  fun severityToStringCompat(severity: Severity): String

  fun irDiagnosticsForFileCompat(
    info: IrBackendInput,
    file: TestFile,
    testServices: TestServices,
  ): List<KtDiagnostic>?

  val noIrCompilationErrorsHandlerCtor: Constructor<AnalysisHandler<IrBackendInput>>

  val suppressionCheckerCtor: Constructor<SuppressionChecker>

  val tagsGeneratorCheckerHandler: Constructor<AnalysisHandler<FirOutputArtifact>>?

  val tagsGeneratorCheckerAfterAnalysis: Constructor<AfterAnalysisChecker>?

  fun TestConfigurationBuilder.useIrDumpFailureSuppressorsCompat()

  fun TestConfigurationBuilder.usePhasedPipelineFailureSuppressorCompat()

  fun RegisteredDirectivesBuilder.firIdenticalCompat()

  fun createKotlinJsBoxTestBase(): AbstractKotlinCompilerTest

  fun TestConfigurationBuilder.commonConfigurationForJsTest()

  fun TestConfigurationBuilder.setupJvmPipelineSteps(parser: FirParser)

  fun afterAnalysisChecker(
    testServices: TestServices,
    directives: List<DirectivesContainer>?,
    check: context(TestServices) (thereWereFailures: Boolean) -> Unit,
  ): AfterAnalysisChecker

  companion object :
    MetroDiagnosticsCompat by ServiceLoader.load(MetroDiagnosticsCompat::class.java).firstOrNull()
      as MetroDiagnosticsCompat
}
