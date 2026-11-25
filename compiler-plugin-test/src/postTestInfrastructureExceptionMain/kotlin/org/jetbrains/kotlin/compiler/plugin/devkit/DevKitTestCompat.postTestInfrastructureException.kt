// The facade class that a file's top-level declarations compile into is part of the published
// binary ABI. This file was renamed, so `@file:JvmName` keeps its pre-rename facade class.
@file:JvmName("DevKitTestCompat_post2420Dev7600Kt")

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.TestInfrastructureException

public actual fun TestInfrastructureException(
    message: String,
    cause: Throwable?,
): RuntimeException = TestInfrastructureException(message, cause)
