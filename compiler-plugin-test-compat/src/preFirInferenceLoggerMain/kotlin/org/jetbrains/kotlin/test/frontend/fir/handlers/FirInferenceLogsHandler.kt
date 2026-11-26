package org.jetbrains.kotlin.test.frontend.fir.handlers

import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices

// Inference logs infrastructure does not exist below 2.2.20, so this handler does nothing
public class FirInferenceLogsHandler(testServices: TestServices) :
    FirAnalysisHandler(testServices) {
    override fun processAfterAllModules(someAssertionWasFailed: Boolean) {
        // do nothing
    }

    override fun processModule(module: TestModule, info: FirOutputArtifact) {
        // do nothing
    }
}
