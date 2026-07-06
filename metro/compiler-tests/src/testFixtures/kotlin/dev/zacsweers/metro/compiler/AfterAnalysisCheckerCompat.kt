package dev.zacsweers.metro.compiler

import org.jetbrains.kotlin.test.WrappedException
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.services.TestServices

abstract class AfterAnalysisCheckerCompat(testServices: TestServices) :
    AfterAnalysisChecker(testServices) {

    final override fun check(failedAssertions: List<WrappedException>) {
        check(thereWereFailures = failedAssertions.isNotEmpty())
    }

    abstract fun check(thereWereFailures: Boolean)
}
