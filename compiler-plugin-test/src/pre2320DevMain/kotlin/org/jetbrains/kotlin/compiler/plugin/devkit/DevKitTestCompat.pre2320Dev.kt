package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.js.test.fir.AbstractFirJsDiagnosticWithBackendTestBase
import org.jetbrains.kotlin.js.test.fir.AbstractFirJsTest
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest

public actual fun jsTestBase(
    pathToTestDir: String,
    testGroupOutputDirPrefix: String,
    targetBackend: TargetBackend,
    parser: FirParser,
): AbstractKotlinCompilerTest =
    object : AbstractFirJsTest(pathToTestDir, testGroupOutputDirPrefix, targetBackend, parser) {}

public actual fun jsDiagnosticWithBackendTestBase(parser: FirParser): AbstractKotlinCompilerTest =
    object : AbstractFirJsDiagnosticWithBackendTestBase(parser) {}
