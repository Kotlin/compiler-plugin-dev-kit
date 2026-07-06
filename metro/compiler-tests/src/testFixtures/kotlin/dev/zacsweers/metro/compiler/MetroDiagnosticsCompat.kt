// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler

import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.WrappedException
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor.SuppressionChecker
import org.jetbrains.kotlin.test.backend.handlers.IrTextDumpHandler
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.AnalysisHandler
import org.jetbrains.kotlin.test.model.BackendKind
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.services.SourceFileProvider
import org.jetbrains.kotlin.test.services.TestServices

private fun stub(): Nothing = error("overridden in version-specific sources")

fun SourceFileProvider.getContentOfSourceFileCompat(file: TestFile): String = stub()

fun irTextDumpHandlerCtor(
  testServices: TestServices,
  backendKind: BackendKind<IrBackendInput>,
): IrTextDumpHandler = stub()

fun mainFirFilesCompat(info: FirOutputArtifact): Map<TestFile, FirFile> = stub()

fun severityToStringCompat(severity: Severity): String = stub()

fun irDiagnosticsForFileCompat(
  info: IrBackendInput,
  file: TestFile,
  testServices: TestServices,
): List<KtDiagnostic>? = stub()

val noIrCompilationErrorsHandlerCtor: Constructor<AnalysisHandler<IrBackendInput>> = stub()

val suppressionCheckerCtor: Constructor<SuppressionChecker> = stub()

val tagsGeneratorCheckerHandler: Constructor<AnalysisHandler<FirOutputArtifact>>? = stub()

val tagsGeneratorCheckerAfterAnalysis: Constructor<AfterAnalysisChecker>? = stub()

fun TestConfigurationBuilder.useIrDumpFailureSuppressorsCompat() {
  stub()
}

fun TestConfigurationBuilder.usePhasedPipelineFailureSuppressorCompat() {
  stub()
}

fun RegisteredDirectivesBuilder.firIdenticalCompat() {
  stub()
}

fun createKotlinJsBoxTestBase(): AbstractKotlinCompilerTest = stub()
