package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.frontend.fir.TagsGeneratorChecker

public actual fun TestConfigurationBuilder.registerTagsGeneratorChecker() {
    useAfterAnalysisCheckers(::TagsGeneratorChecker)
}
