package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.KtSourceElementOffsetStrategy

public expect fun KtSourceElement.fakeElementCompat(
    newKind: KtFakeSourceElementKind,
    offsetStrategy: KtSourceElementOffsetStrategy = KtSourceElementOffsetStrategy.Default,
): KtSourceElement
