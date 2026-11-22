package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fakeElement

public actual fun KtSourceElement.fakeElementCompat(
    newKind: KtFakeSourceElementKind,
    offsetStrategy: KtSourceElementOffsetStrategy,
): KtSourceElement = fakeElement(newKind, offsetStrategy.toCompilerStrategy())
