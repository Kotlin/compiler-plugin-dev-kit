package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.ServicesAndDirectivesContainer
import org.jetbrains.kotlin.test.services.TestServices

public expect fun afterAnalysisChecker(
    testServices: TestServices,
    directives: List<DirectivesContainer>?,
    check: context(TestServices) (thereWereFailures: Boolean) -> Unit,
): AfterAnalysisChecker

public expect fun RegisteredDirectivesBuilder.versionDumpClassifier()

public expect fun TestConfigurationBuilder.useFailureSuppressorsCompat(
    vararg checkers: Constructor<ServicesAndDirectivesContainer>,
    insertAtFirst: Boolean = false,
)

public expect fun RegisteredDirectivesBuilder.firIdenticalCompat()

public expect fun TestConfigurationBuilder.setupJvmPipelineSteps(parser: FirParser)
