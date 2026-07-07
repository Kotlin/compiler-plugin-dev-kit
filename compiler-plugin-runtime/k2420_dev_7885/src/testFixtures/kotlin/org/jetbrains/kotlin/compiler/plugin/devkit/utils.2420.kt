package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.TestFailureSuppressor

fun TestConfigurationBuilder.useFailureSuppressorsCompat(vararg checkers: Constructor<TestFailureSuppressor>, insertAtFirst: Boolean = false) =
    useFailureSuppressors(*checkers, insertAtFirst = insertAtFirst)