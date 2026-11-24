package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.js.test.runners.AbstractJsBlackBoxCodegenTestBase
import org.jetbrains.kotlin.js.test.runners.commonConfigurationForJsTest
import org.jetbrains.kotlin.js.test.runners.setUpDefaultDirectivesForJsBoxTest
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest

public actual fun TestConfigurationBuilder.commonConfigurationForJsTestCompat() {
    commonConfigurationForJsTest()
}

public actual fun TestConfigurationBuilder.setUpDefaultDirectivesForJsBoxTestCompat(
    parser: FirParser
) {
    setUpDefaultDirectivesForJsBoxTest(parser)
}

public actual fun createJsBoxTestDelegate(
    targetBackend: TargetBackend,
    pathToTestDir: String,
    testGroupOutputDirPrefix: String,
): AbstractKotlinCompilerTest =
    object :
        AbstractJsBlackBoxCodegenTestBase(
            targetBackend,
            pathToTestDir,
            testGroupOutputDirPrefix,
        ) {
        override val backendFacades: JsBackendFacades
            get() = JsBackendFacades.WithRecompilation
    }
