package org.jetbrains.kotlin.compiler.plugin.devkit.runners

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
            devKitDefaults()
            defaultDirectives {
                diagnosticTestDefaults()
                jvmDefaults()
            }
        },
        *config,
    )

internal fun RegisteredDirectivesBuilder.diagnosticTestDefaults() {
    +FirDiagnosticsDirectives.FIR_DUMP
    +FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
}
