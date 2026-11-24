package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.TestInfrastructureException

public actual fun TestInfrastructureException(
    message: String,
    cause: Throwable?,
): RuntimeException = TestInfrastructureException(message, cause)
