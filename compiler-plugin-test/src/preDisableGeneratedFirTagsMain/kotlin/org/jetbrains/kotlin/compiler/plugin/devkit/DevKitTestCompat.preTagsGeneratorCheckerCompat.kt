// This function used to live in `DevKitTestCompat.pre23Dev.kt`, and published bytecode references
// it through that file's facade class. Files that share a facade class must all declare
// `@JvmMultifileClass`.
@file:JvmName("DevKitTestCompat_pre23DevKt")
@file:JvmMultifileClass

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.WrappedException
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.frontend.fir.TagsGeneratorChecker
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.services.ServiceRegistrationData
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.moduleStructure

public actual fun TestConfigurationBuilder.registerTagsGeneratorChecker() {
    useAfterAnalysisCheckers(::TagsGeneratorCheckerCompat)
}

public class TagsGeneratorCheckerCompat(testServices: TestServices) :
    AfterAnalysisChecker(testServices) {
    public val underlying: TagsGeneratorChecker = TagsGeneratorChecker(testServices)

    override fun suppressIfNeeded(
        failedAssertions: List<WrappedException>
    ): List<WrappedException> = underlying.suppressIfNeeded(failedAssertions)

    override val order: Order
        get() = underlying.order

    override val additionalServices: List<ServiceRegistrationData>
        get() = underlying.additionalServices

    override val directiveContainers: List<DirectivesContainer>
        get() = underlying.directiveContainers

    override fun check(failedAssertions: List<WrappedException>) {
        if (DISABLE_GENERATED_FIR_TAGS in testServices.moduleStructure.allDirectives) return
        return underlying.check(failedAssertions)
    }
}
