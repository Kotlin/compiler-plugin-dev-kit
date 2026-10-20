package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder

public open class DevKitJsDiagnosticTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    parser: FirParser = FirParser.LightTree,
) :
    BackportedJsDiagnosticWithBackendTestBase(
        parser,
        {
            defaultDirectives {
                diagnosticTestDefaults()
            }
        },
        *config,
    )
