package org.jetbrains.kotlin.test.frontend.fir

import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.services.TestServices

// Tags generator did not exist before this version, and it's unlikely that compiler plugins would
// benefit from this, so this handler does nothing
public class TagsGeneratorChecker(testServices: TestServices) : AfterAnalysisChecker(testServices)
