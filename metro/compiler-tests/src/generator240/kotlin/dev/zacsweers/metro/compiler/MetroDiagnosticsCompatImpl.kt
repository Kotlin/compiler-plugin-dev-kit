// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler

import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.backend.ir.BackendCliJvmFacade
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.configuration.commonConfigurationForJvmTest
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives.FIR_IDENTICAL
import org.jetbrains.kotlin.test.frontend.fir.Fir2IrCliJvmFacade
import org.jetbrains.kotlin.test.frontend.fir.FirCliJvmFacade
import org.jetbrains.kotlin.test.model.FrontendKinds

class MetroDiagnosticsCompatImpl : MetroDiagnosticsCompat {
  override fun RegisteredDirectivesBuilder.firIdenticalCompat() {
    +FIR_IDENTICAL
  }

  override fun TestConfigurationBuilder.setupJvmPipelineSteps(parser: FirParser) =
    commonConfigurationForJvmTest(
      targetFrontend = FrontendKinds.FIR,
      frontendFacade = ::FirCliJvmFacade,
      frontendToBackendConverter = ::Fir2IrCliJvmFacade,
      backendFacade = ::BackendCliJvmFacade,
    )
}
