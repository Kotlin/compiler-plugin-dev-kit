package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest

public expect fun TestConfigurationBuilder.commonConfigurationForJsTestCompat()

public expect fun TestConfigurationBuilder.setUpDefaultDirectivesForJsBoxTestCompat(
    parser: FirParser
)

public expect fun createJsBoxTestDelegate(
    targetBackend: TargetBackend,
    pathToTestDir: String,
    testGroupOutputDirPrefix: String,
): AbstractKotlinCompilerTest
