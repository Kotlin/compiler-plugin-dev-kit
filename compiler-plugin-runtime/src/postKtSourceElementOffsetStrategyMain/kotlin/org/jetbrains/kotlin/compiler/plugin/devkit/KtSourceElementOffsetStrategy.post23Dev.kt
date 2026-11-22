package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.KtSourceElementOffsetStrategy as CompilerOffsetStrategy

/**
 * Converts the devkit's [KtSourceElementOffsetStrategy] to the compiler's counterpart, which only
 * exists since Kotlin 2.3.0.
 */
internal fun KtSourceElementOffsetStrategy.toCompilerStrategy(): CompilerOffsetStrategy =
    when (this) {
        KtSourceElementOffsetStrategy.Default -> CompilerOffsetStrategy.Default
        is KtSourceElementOffsetStrategy.Custom.Delegated ->
            CompilerOffsetStrategy.Custom.Delegated(startOffsetAnchor, endOffsetAnchor)

        is KtSourceElementOffsetStrategy.Custom.Initialized ->
            CompilerOffsetStrategy.Custom.Initialized(startOffset, endOffset)
    }
