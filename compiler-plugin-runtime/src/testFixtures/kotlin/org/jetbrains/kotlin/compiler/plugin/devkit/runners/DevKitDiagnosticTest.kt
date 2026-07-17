package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitTestCompat.Companion.versionDumpClassifier
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.runners.AbstractFirPhasedDiagnosticTest

public open class DevKitDiagnosticTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    parser: FirParser = FirParser.LightTree,
) :
    DevKitTest(
        object : AbstractFirPhasedDiagnosticTest(parser) {},
        {
            defaultDirectives {
                versionDumpClassifier()
                +FirDiagnosticsDirectives.FIR_DUMP
                +FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
            }
        },
        *config,
    )

public open class DevKitJvmDiagnosticTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    parser: FirParser = FirParser.LightTree,
) : DevKitDiagnosticTest({ defaultDirectives { jvmDefaults() } }, *config, parser = parser)
