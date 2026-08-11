package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.configuration.setupJvmPipelineSteps as realSetupJvmPipelineSteps
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.ServicesAndDirectivesContainer
import org.jetbrains.kotlin.test.model.TestFailureSuppressor
import org.jetbrains.kotlin.test.services.TestServices

public actual fun afterAnalysisChecker(
    testServices: TestServices,
    directives: List<DirectivesContainer>?,
    check: context(TestServices) (thereWereFailures: Boolean) -> Unit,
): AfterAnalysisChecker =
    object : AfterAnalysisChecker(testServices) {
        override fun check(thereWereFailures: Boolean) = check(testServices, thereWereFailures)
    }

@Suppress("UNCHECKED_CAST", "KotlinConstantConditions")
public actual fun TestConfigurationBuilder.useFailureSuppressorsCompat(
    vararg checkers: Constructor<ServicesAndDirectivesContainer>,
    insertAtFirst: Boolean,
) {
    useFailureSuppressors(
        *(checkers as Array<out Constructor<TestFailureSuppressor>>),
        insertAtFirst = insertAtFirst,
    )
}

public actual fun RegisteredDirectivesBuilder.firIdenticalCompat() {}

public actual fun TestConfigurationBuilder.setupJvmPipelineSteps(parser: FirParser) {
    realSetupJvmPipelineSteps(parser)
}
