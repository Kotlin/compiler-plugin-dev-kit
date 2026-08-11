package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.WrappedException
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.ServicesAndDirectivesContainer
import org.jetbrains.kotlin.test.services.TestServices

// TODO hacky. JVM target should be removed
public actual fun afterAnalysisChecker(
    testServices: TestServices,
    directives: List<DirectivesContainer>?,
    check: context(TestServices) (thereWereFailures: Boolean) -> Unit,
): AfterAnalysisChecker =
    object : AfterAnalysisChecker(testServices) {
        override fun check(failedAssertions: List<WrappedException>) =
            check(testServices, failedAssertions.isNotEmpty())
    }

@Suppress("UNCHECKED_CAST", "KotlinConstantConditions")
public actual fun TestConfigurationBuilder.useFailureSuppressorsCompat(
    vararg checkers: Constructor<ServicesAndDirectivesContainer>,
    insertAtFirst: Boolean,
) {
    useAfterAnalysisCheckers(
        *(checkers as Array<out Constructor<AfterAnalysisChecker>>),
        insertAtFirst = insertAtFirst,
    )
}

public actual fun RegisteredDirectivesBuilder.versionDumpClassifier() {}
