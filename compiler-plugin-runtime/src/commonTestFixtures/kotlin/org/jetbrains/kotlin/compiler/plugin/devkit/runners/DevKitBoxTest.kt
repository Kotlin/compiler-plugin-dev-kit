package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.versionDumpClassifier
import org.jetbrains.kotlin.js.test.runners.AbstractJsTest
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.JsEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.runners.AbstractFirBlackBoxCodegenTestSpecBase

public open class DevKitJvmBoxTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    parser: FirParser = FirParser.LightTree,
) :
    DevKitTest(
        object : AbstractFirBlackBoxCodegenTestSpecBase(parser) {},
        {
            defaultDirectives {
                versionDumpClassifier()
                boxTestDefaults()
                jvmDefaults()
            }
        },
        *config,
    )

public open class DevKitJsBoxTestDumpless(
    vararg config: TestConfigurationBuilder.() -> Unit,
    targetBackend: TargetBackend = TargetBackend.JS_IR,
    public val parser: FirParser = FirParser.LightTree,
) :
    DevKitTest(
        object :
            AbstractJsTest(
                "",
                "box/js",
                targetBackend,
                parser,
            ) {},
        {
            // give each JS box test its own output directory, so no race conditions happen
            defaultDirectives {
                -JsEnvironmentConfigurationDirectives.TEST_GROUP_OUTPUT_DIR_PREFIX
                JsEnvironmentConfigurationDirectives.TEST_GROUP_OUTPUT_DIR_PREFIX with
                    "box/js/${testInfo.className}"
            }
        },
        *config,
    )

public open class DevKitJsBoxTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    targetBackend: TargetBackend = TargetBackend.JS_IR,
    parser: FirParser = FirParser.LightTree,
) :
    DevKitJsBoxTestDumpless(
        {
            defaultDirectives {
                versionDumpClassifier()
                boxTestDefaults()
            }
        },
        *config,
        targetBackend = targetBackend,
        parser = parser,
    )

private fun RegisteredDirectivesBuilder.boxTestDefaults() {
    +CodegenTestDirectives.DUMP_IR
    +FirDiagnosticsDirectives.FIR_DUMP
}
