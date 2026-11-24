package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.services.TestPhase

public actual val backendTestPhase: TestPhase
    get() = TestPhase.BACKEND
