package org.jetbrains.kotlin.test.services.sourceProviders

import org.jetbrains.kotlin.test.services.TestServices

public fun SpecHelpersSourceFilesProvider(
    testServices: TestServices,
    testType: String,
): SpecHelpersSourceFilesProvider =
    SpecHelpersSourceFilesProvider(testServices, testType, baseDir = ".")
