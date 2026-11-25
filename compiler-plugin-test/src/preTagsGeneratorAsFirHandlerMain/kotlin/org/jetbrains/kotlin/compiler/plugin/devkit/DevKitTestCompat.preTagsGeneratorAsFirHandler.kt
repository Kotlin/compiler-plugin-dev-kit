// This function used to live in `DevKitTestCompat.pre23Dev.kt`, and published bytecode references
// it through that file's facade class. Files that share a facade class must all declare
// `@JvmMultifileClass`.
@file:JvmName("DevKitTestCompat_pre23DevKt")
@file:JvmMultifileClass

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.frontend.fir.TagsGeneratorChecker

public actual fun TestConfigurationBuilder.registerTagsGeneratorChecker() {
    useAfterAnalysisCheckers(::TagsGeneratorChecker)
}
