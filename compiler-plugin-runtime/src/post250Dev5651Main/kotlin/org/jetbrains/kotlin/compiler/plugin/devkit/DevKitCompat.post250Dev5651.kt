package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElementOffsetStrategy

public actual fun org.jetbrains.kotlin.KtSourceElement.fakeElementCompat(
    newKind: KtFakeSourceElementKind,
    offsetStrategy: KtSourceElementOffsetStrategy,
): org.jetbrains.kotlin.KtSourceElement = fakeElement(newKind, offsetStrategy)
