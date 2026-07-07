package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker

fun TestConfigurationBuilder.useFailureSuppressorsCompat(vararg checkers: Constructor<AfterAnalysisChecker>, insertAtFirst: Boolean = false) =
    useAfterAnalysisCheckers(*checkers, insertAtFirst = insertAtFirst)