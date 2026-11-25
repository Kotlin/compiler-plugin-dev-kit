// The facade class that a file's top-level declarations compile into is part of the published
// binary ABI. This file was renamed, so `@file:JvmName` keeps its pre-rename facade class.
@file:JvmName("DevKitTestCompat_pre2420Dev7600Kt")

package org.jetbrains.kotlin.compiler.plugin.devkit

public actual fun TestInfrastructureException(
    message: String,
    cause: Throwable?,
): RuntimeException = IllegalStateException(message, cause)
