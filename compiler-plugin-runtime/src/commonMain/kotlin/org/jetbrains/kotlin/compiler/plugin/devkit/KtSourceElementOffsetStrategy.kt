package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.KtSourceElement

/**
 * Compatibility counterpart of the compiler's `KtSourceElementOffsetStrategy`, which only exists
 * since Kotlin 2.3.0. Pre-2.3.0, where `fakeElement` takes raw offsets instead, [Custom] offsets
 * are passed to it directly, and on every version [Default] keeps the original element's offsets.
 */
public sealed class KtSourceElementOffsetStrategy {
    public object Default : KtSourceElementOffsetStrategy()

    public sealed class Custom : KtSourceElementOffsetStrategy() {
        public abstract val startOffset: Int
        public abstract val endOffset: Int

        public class Delegated(
            public val startOffsetAnchor: KtSourceElement,
            public val endOffsetAnchor: KtSourceElement,
        ) : Custom() {
            public override val startOffset: Int
                get() = startOffsetAnchor.startOffset

            public override val endOffset: Int
                get() = endOffsetAnchor.endOffset
        }

        public class Initialized(
            public override val startOffset: Int,
            public override val endOffset: Int,
        ) : Custom()
    }
}
