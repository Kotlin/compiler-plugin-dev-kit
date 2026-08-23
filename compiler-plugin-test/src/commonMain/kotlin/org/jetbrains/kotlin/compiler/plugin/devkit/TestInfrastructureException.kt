/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.compiler.plugin.devkit

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Throws a [TestInfrastructureException] if [value] is `false`.
 *
 * The drop-in replacement for [check] for test-infrastructure invariants whose violation must never
 * be masked by failure suppressors (see [TestInfrastructureException]).
 */
@OptIn(ExperimentalContracts::class)
public inline fun checkTestInfrastructure(value: Boolean, lazyMessage: () -> String) {
    contract {
        returns() implies value
    }
    if (!value) {
        testInfraError(lazyMessage())
    }
}

public fun testInfraError(message: String): Nothing = throw TestInfrastructureException(message)
