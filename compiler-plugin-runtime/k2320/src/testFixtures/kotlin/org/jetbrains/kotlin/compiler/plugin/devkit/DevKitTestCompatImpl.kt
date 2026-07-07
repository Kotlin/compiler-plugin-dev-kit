package org.jetbrains.kotlin.compiler.plugin.devkit // Copyright (C) 2026 Zac Sweers

// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.test.WrappedException
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.services.TestServices

class DevKitTestCompatImpl : DevKitTestCompat {
    override fun afterAnalysisChecker(
        testServices: TestServices,
        directives: List<DirectivesContainer>?,
        check: context(TestServices) (thereWereFailures: Boolean) -> Unit,
    ): AfterAnalysisChecker =
        object : AfterAnalysisChecker(testServices) {
            override fun check(failedAssertions: List<WrappedException>) =
                check(testServices, failedAssertions.isNotEmpty())
        }

    override fun RegisteredDirectivesBuilder.versionDumpClassifier() {}
}
