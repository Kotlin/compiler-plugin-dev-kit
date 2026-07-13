// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler

fun main(args: Array<String>) {
  generateTests<
    AbstractBoxTest,
    AbstractFastInitBoxTest,
    AbstractContributionProvidersBoxTest,
    AbstractJsBoxTest,
    AbstractJsFastInitBoxTest,
    AbstractJsContributionProvidersBoxTest,
    AbstractIrOnlyClassesBoxTest,
    AbstractDiagnosticTest,
    AbstractJsDiagnosticTest,
    AbstractFirDumpTest,
    AbstractIrDumpTest,
    AbstractReportsTest,
  >(
    args,
    null,
  )
}
