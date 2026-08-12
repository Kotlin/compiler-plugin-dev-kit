package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.directives.TestDumpDirectives

public actual fun RegisteredDirectivesBuilder.versionDumpClassifier() {
    TestDumpDirectives.DUMP_CLASSIFIER with
        KotlinCompilerVersion.VERSION.replace(".", "").replace("-", "_").lowercase()
}
