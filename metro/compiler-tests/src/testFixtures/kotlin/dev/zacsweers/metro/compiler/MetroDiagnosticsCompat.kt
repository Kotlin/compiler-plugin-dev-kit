// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler

import java.util.*
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.backend.handlers.findByPath
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.services.TestServices

interface MetroDiagnosticsCompat {
  fun RegisteredDirectivesBuilder.firIdenticalCompat()

  fun TestConfigurationBuilder.setupJvmPipelineSteps(parser: FirParser)

  companion object :
    MetroDiagnosticsCompat by ServiceLoader.load(MetroDiagnosticsCompat::class.java).firstOrNull()
      as MetroDiagnosticsCompat {

    // 2.3.21 dropped `diagnosticsByFilePath` for `diagnosticsByFile`. compileOnly is pinned to
    // 2.3.20
    // -- to also handle 2.3.21+ at runtime, resolve the getter reflectively rather than calling
    // either
    // property statically.
    private val diagnosticsByFileGetter: java.lang.reflect.Method? =
      org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector::class
        .java
        .methods
        .firstOrNull {
          it.name == "getDiagnosticsByFile"
        }

    private val diagnosticsByFilePathGetter: java.lang.reflect.Method? =
      org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector::class
        .java
        .methods
        .firstOrNull {
          it.name == "getDiagnosticsByFilePath"
        }

    fun irDiagnosticsForFileCompat(
      info: IrBackendInput,
      file: TestFile,
      testServices: TestServices,
    ): List<KtDiagnostic>? {
      val reporter = info.diagnosticReporter
      diagnosticsByFileGetter?.let { getter ->
        @Suppress("UNCHECKED_CAST")
        val byFile = getter.invoke(reporter) as Map<Any?, List<KtDiagnostic>>
        return file.findByPath(testServices) { path ->
          byFile.entries
            .firstOrNull { entry ->
              entry.key?.javaClass?.getMethod("getPath")?.invoke(entry.key) == path
            }
            ?.value
        }
      }
      val getter =
        diagnosticsByFilePathGetter
          ?: error(
            "Neither diagnosticsByFile nor diagnosticsByFilePath found on BaseDiagnosticsCollector"
          )

      @Suppress("UNCHECKED_CAST")
      val byPath = getter.invoke(reporter) as Map<String?, List<KtDiagnostic>>
      return file.findByPath(testServices) { byPath[it] }
    }
  }
}
