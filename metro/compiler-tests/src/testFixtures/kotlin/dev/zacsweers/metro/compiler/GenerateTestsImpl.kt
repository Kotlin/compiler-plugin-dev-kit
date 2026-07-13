// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler

import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitTestGenerator
import org.jetbrains.kotlin.compiler.plugin.devkit.sourceSetTestClass
import org.jetbrains.kotlin.generators.dsl.TestGroup

// API repackaged in kotlin 2.3.0
inline fun <
  reified Box,
  reified FastInitBox,
  reified ContributionProvidersBox,
  reified JsBox,
  reified JsFastInitBox,
  reified JsContributionProvidersBox,
  reified IrOnlyClassesBox,
  reified Diagnostic,
  reified JsDiagnostic,
  reified FirDump,
  reified IrDump,
  reified Reports,
> generateTests(args: Array<String>, exclusionPattern: String?) {
  DevKitTestGenerator.generate(args) {
    val commonModel: TestGroup.TestClass.(name: String) -> Unit = { name ->
      model(name, excludedPattern = exclusionPattern)
    }
    val nonJvmModel: TestGroup.TestClass.(name: String) -> Unit = { name ->
      model(
        name,
        excludedPattern = exclusionPattern,
        excludeDirsRecursively = listOf("interop", "circuit"),
      )
    }
    sourceSetTestClass<Box> { commonModel("box") }
    sourceSetTestClass<FastInitBox> { commonModel("box") }
    sourceSetTestClass<ContributionProvidersBox> { commonModel("box") }
    sourceSetTestClass<JsBox> { nonJvmModel("box") }
    sourceSetTestClass<JsFastInitBox> { nonJvmModel("box") }
    sourceSetTestClass<JsContributionProvidersBox> { nonJvmModel("box") }
    sourceSetTestClass<IrOnlyClassesBox> { commonModel("box") }
    sourceSetTestClass<Diagnostic> { commonModel("diagnostic") }
    sourceSetTestClass<JsDiagnostic> { nonJvmModel("diagnostic") }
    sourceSetTestClass<FirDump> { commonModel("dump/fir") }
    sourceSetTestClass<IrDump> { commonModel("dump/ir") }
    sourceSetTestClass<Reports> { commonModel("dump/reports") }
  }
}
