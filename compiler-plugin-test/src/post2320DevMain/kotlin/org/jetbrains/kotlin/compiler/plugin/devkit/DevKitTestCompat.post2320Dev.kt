package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.FirOutputPartForDependsOnModule
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirDumpHandler.Companion.collectFilesForRendering
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.model.TestModule

public actual fun collectFilesForRenderingCompat(
    module: TestModule,
    info: FirOutputArtifact,
    part: FirOutputPartForDependsOnModule,
): List<FirFile> = collectFilesForRendering(module, info, part)

public actual val IrBackendInput.irBuiltInsCompat: IrBuiltIns
    get() = irBuiltIns

public actual val FirOutputArtifact.mainFirFilesByTestFileCompat: Map<TestFile, FirFile>
    get() = mainFirFilesByTestFile
