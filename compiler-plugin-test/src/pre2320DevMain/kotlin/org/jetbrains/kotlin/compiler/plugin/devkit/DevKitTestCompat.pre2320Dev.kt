package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.fir.backend.utils.createFilesWithGeneratedDeclarations
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.FirOutputPartForDependsOnModule
import org.jetbrains.kotlin.test.model.TestModule

public actual fun collectFilesForRenderingCompat(
    module: TestModule,
    info: FirOutputArtifact,
    part: FirOutputPartForDependsOnModule,
): List<FirFile> {
    val firFiles = info.mainFirFiles

    return buildList {
        addAll(firFiles.values)
        addAll(part.session.createFilesWithGeneratedDeclarations())
    }
}

public actual val IrBackendInput.irBuiltInsCompat: IrBuiltIns
    get() = irPluginContext.irBuiltIns
