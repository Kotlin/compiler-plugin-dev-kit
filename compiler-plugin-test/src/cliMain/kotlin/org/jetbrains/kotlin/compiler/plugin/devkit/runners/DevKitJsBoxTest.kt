package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.JsEnvironmentConfigurationDirectives

public open class MultiVersionJsTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    targetBackend: TargetBackend = TargetBackend.JS_IR,
    public val parser: FirParser = FirParser.LightTree,
) :
    BackportedJsTest(
        "",
        "box/js",
        targetBackend,
        parser,
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
    MultiVersionJsTest(
        {
            defaultDirectives {
                boxTestDefaults()
            }
        },
        *config,
        targetBackend = targetBackend,
        parser = parser,
    )
