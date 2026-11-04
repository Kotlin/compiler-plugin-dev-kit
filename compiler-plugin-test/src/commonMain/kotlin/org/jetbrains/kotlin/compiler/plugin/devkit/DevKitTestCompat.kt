package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirAnalysisHandler
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.ServicesAndDirectivesContainer
import org.jetbrains.kotlin.test.services.TestPhase
import org.jetbrains.kotlin.test.services.TestService
import org.jetbrains.kotlin.test.services.TestServices

public expect fun afterAnalysisChecker(
    testServices: TestServices,
    directives: List<DirectivesContainer>?,
    check: context(TestServices) (thereWereFailures: Boolean) -> Unit,
): AfterAnalysisChecker

public expect fun TestConfigurationBuilder.useFailureSuppressorsCompat(
    vararg checkers: Constructor<ServicesAndDirectivesContainer>,
    insertAtFirst: Boolean = false,
)

public expect fun RegisteredDirectivesBuilder.firIdenticalCompat()

public expect fun TestConfigurationBuilder.setupJvmPipelineSteps(parser: FirParser)

/**
 * Signals a failure caused by the test infrastructure itself (a violated internal invariant of the
 * test runner, handlers, etc.) as opposed to a failure of the code under test (e.g. a compiler
 * bug).
 *
 * Failures wrapped around a [TestInfrastructureException] are intentionally **not** suppressed by
 * [org.jetbrains.kotlin.test.model.TestFailureSuppressor]s, even when the test carries a matching
 * mute directive such as `IGNORE_BACKEND`. This guarantees that an infrastructure problem is always
 * reported instead of being silently turned into a green test, which would mask the real (unknown)
 * test status.
 *
 * Use it instead of [check]/[error] for asserting test-infrastructure invariants when the resulting
 * failure could be processed by failure suppressors.
 */
public expect fun TestInfrastructureException(
    message: String,
    cause: Throwable? = null,
): RuntimeException

/**
 * Checks that the source elements of FIR declarations in the analyzed FIR files are distinct. See
 * [checkDistinctSourceElements] for more information.
 *
 * The handler checks FIR files *after* analysis, which means that FIR elements have already been
 * transformed. The tree might be different compared to its raw state, so the checker might not
 * catch all cases of duplicate source elements. To mitigate this, raw FIR files are covered by
 * `LightTreeDistinctSourceElementsTest` and `PsiDistinctSourceElementsTest`, which also check the
 * diagnostic test data.
 *
 * See `LLDistinctSourceElementsChecker` for the corresponding Analysis API checker.
 */
public expect fun FirDistinctSourceElementsHandler(testServices: TestServices): FirAnalysisHandler

public expect val libraryProviderCtor: Constructor<TestService>?

public expect val backendTestPhase: TestPhase
