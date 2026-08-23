package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.versionDumpClassifier
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives

public open class DevKitJvmDiagnosticTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    parser: FirParser = FirParser.LightTree,
) :
    BackportedFirPhasedDiagnosticTest(
        parser,
        {
            defaultDirectives {
                versionDumpClassifier()
                diagnosticTestDefaults()
                jvmDefaults()
            }
        },
        *config,
    )

public open class DevKitJsDiagnosticTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    parser: FirParser = FirParser.LightTree,
) :
    BackportedJsDiagnosticWithBackendTestBase(
        parser,
        {
            defaultDirectives {
                versionDumpClassifier()
                diagnosticTestDefaults()
            }
        },
        *config,
    )

private fun RegisteredDirectivesBuilder.diagnosticTestDefaults() {
    +FirDiagnosticsDirectives.FIR_DUMP
    +FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
}
