package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.fir.backend.utils.createFilesWithGeneratedDeclarations
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.FirOutputPartForDependsOnModule
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.model.TestModule

public actual fun collectFilesForRenderingCompat(
    module: TestModule,
    info: FirOutputArtifact,
    part: FirOutputPartForDependsOnModule,
): List<FirFile> {
    // CHANGED: exclude additional test files
    return buildList {
        // collect only files belonging to the specific parts and exclude additional test files
        info.allFirFiles.values.filterTo(this) { file ->
            file.moduleData == part.session.moduleData &&
                info.allFirFiles.entries.none {
                    it.value == file && it.key.isAdditional
                }
        }
        addAll(part.session.createFilesWithGeneratedDeclarations())
    }
}

public actual val IrBackendInput.irBuiltInsCompat: IrBuiltIns
    get() = irPluginContext.irBuiltIns

public actual val FirOutputArtifact.mainFirFilesByTestFileCompat: Map<TestFile, FirFile>
    get() = mainFirFiles
