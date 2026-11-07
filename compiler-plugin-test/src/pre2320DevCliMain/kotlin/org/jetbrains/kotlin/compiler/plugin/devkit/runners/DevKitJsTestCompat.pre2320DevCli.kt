package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.js.test.converters.Fir2IrCliWebFacade
import org.jetbrains.kotlin.js.test.converters.FirCliWebFacade
import org.jetbrains.kotlin.js.test.converters.FirKlibSerializerCliWebFacade
import org.jetbrains.kotlin.js.test.fir.setupDefaultDirectivesForFirJsBoxTest
import org.jetbrains.kotlin.js.test.ir.AbstractJsBlackBoxCodegenTestBase
import org.jetbrains.kotlin.js.test.ir.commonConfigurationForJsTest
import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.BackendFacade
import org.jetbrains.kotlin.test.model.BinaryArtifacts
import org.jetbrains.kotlin.test.model.Frontend2BackendConverter
import org.jetbrains.kotlin.test.model.FrontendFacade
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest

public actual fun TestConfigurationBuilder.commonConfigurationForJsTestCompat() {
    commonConfigurationForJsTest(
        targetFrontend = FrontendKinds.FIR,
        frontendFacade = ::FirCliWebFacade,
        frontendToIrConverter = ::Fir2IrCliWebFacade,
        serializerFacade = ::FirKlibSerializerCliWebFacade,
    )
}

public actual fun TestConfigurationBuilder.setUpDefaultDirectivesForJsBoxTestCompat(
    parser: FirParser
) {
    setupDefaultDirectivesForFirJsBoxTest(parser)
}

public actual fun createJsBoxTestDelegate(
    targetBackend: TargetBackend,
    pathToTestDir: String,
    testGroupOutputDirPrefix: String,
): AbstractKotlinCompilerTest =
    object :
        AbstractJsBlackBoxCodegenTestBase<FirOutputArtifact>(
            FrontendKinds.FIR,
            targetBackend,
            pathToTestDir,
            testGroupOutputDirPrefix,
        ) {
        override val frontendFacade: Constructor<FrontendFacade<FirOutputArtifact>>
            get() = ::FirCliWebFacade

        override val frontendToIrConverter:
            Constructor<Frontend2BackendConverter<FirOutputArtifact, IrBackendInput>>
            get() = ::Fir2IrCliWebFacade

        override val serializerFacade:
            Constructor<BackendFacade<IrBackendInput, BinaryArtifacts.KLib>>
            get() = ::FirKlibSerializerCliWebFacade

        override val backendFacades: JsBackendFacades
            get() = JsBackendFacades.WithRecompilation
    }
