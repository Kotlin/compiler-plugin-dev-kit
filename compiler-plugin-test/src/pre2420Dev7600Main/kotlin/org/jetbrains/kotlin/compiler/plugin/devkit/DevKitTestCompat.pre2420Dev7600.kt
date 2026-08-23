package org.jetbrains.kotlin.compiler.plugin.devkit

public actual fun TestInfrastructureException(
    message: String,
    cause: Throwable?,
): RuntimeException = IllegalStateException(message, cause)
