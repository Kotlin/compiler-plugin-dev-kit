// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.kotlin.compiler.plugin.devkit

import java.util.*
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.services.TestServices

public interface DevKitTestCompat {
    public fun afterAnalysisChecker(
        testServices: TestServices,
        directives: List<DirectivesContainer>?,
        check: context(TestServices) (thereWereFailures: Boolean) -> Unit,
    ): AfterAnalysisChecker

    public fun RegisteredDirectivesBuilder.versionDumpClassifier()

    public companion object :
        DevKitTestCompat by ServiceLoader.load(DevKitTestCompat::class.java).firstOrNull()
            as DevKitTestCompat
}
