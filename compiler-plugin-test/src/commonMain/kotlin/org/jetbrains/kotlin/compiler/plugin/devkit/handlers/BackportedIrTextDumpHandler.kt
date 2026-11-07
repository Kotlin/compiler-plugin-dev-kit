/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.compiler.plugin.devkit.handlers

import org.jetbrains.kotlin.compiler.plugin.devkit.TestDumpDirectives
import org.jetbrains.kotlin.compiler.plugin.devkit.TestInfrastructureException
import org.jetbrains.kotlin.compiler.plugin.devkit.assertEqualsToDump
import org.jetbrains.kotlin.compiler.plugin.devkit.irBuiltInsCompat
import org.jetbrains.kotlin.ir.InternalSymbolFinderAPI
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.util.DumpIrTreeOptions
import org.jetbrains.kotlin.ir.util.DumpIrTreeOptions.FlagsFilter
import org.jetbrains.kotlin.ir.util.DumpIrTreeVisitor
import org.jetbrains.kotlin.ir.util.dumpTreesFromLineNumber
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.test.backend.handlers.AbstractIrHandler
import org.jetbrains.kotlin.test.backend.handlers.IrTextDumpHandler.Companion.DUMP_EXTENSION
import org.jetbrains.kotlin.test.backend.handlers.IrTextDumpHandler.Companion.groupWithTestFiles
import org.jetbrains.kotlin.test.backend.handlers.IrTextDumpHandler.Companion.isHiddenDeclaration
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.DUMP_EXTERNAL_CLASS
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.DUMP_IR
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.EXTERNAL_FILE
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.SimpleDirective
import org.jetbrains.kotlin.test.model.BackendKind
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.independentSourceDirectoryPath
import org.jetbrains.kotlin.test.services.moduleStructure
import org.jetbrains.kotlin.test.services.transitiveDependsOnDependencies
import org.jetbrains.kotlin.test.services.transitiveFriendDependencies
import org.jetbrains.kotlin.test.services.transitiveRegularDependencies
import org.jetbrains.kotlin.test.utils.MultiModuleInfoDumper
import org.jetbrains.kotlin.test.utils.withSuffixAndExtension
import org.jetbrains.kotlin.utils.addToStdlib.runIf

public class BackportedIrTextDumpHandler(
    testServices: TestServices,
    artifactKind: BackendKind<IrBackendInput>,
    public val customExtension: String? = null,
    public val directive: SimpleDirective = DUMP_IR,
    public val showOffsets: Boolean = false,
) : AbstractIrHandler(testServices, artifactKind) {
    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(TestDumpDirectives, CodegenTestDirectives)

    private val pathRelativizer = IrFileEntryPathRelativizer(testServices)

    private val baseDumper = MultiModuleInfoDumper()

    override fun processModule(module: TestModule, info: IrBackendInput) {
        if (directive !in module.directives) return

        pathRelativizer.addModule(module)

        val dumpOptions =
            DumpIrTreeOptions(
                normalizeNames = true,
                printFacadeClassInFqNames = false,
                declarationFlagsFilter =
                    FlagsFilter { declaration, isReference, flags ->
                        // By coincidence, there is a huge number of cases in IR text test data
                        // files
                        // when flags are still rendered for references to fields and classes.
                        val filteredFlags =
                            flags
                                .takeIf {
                                    !isReference || declaration is IrField || declaration is IrClass
                                }
                                .orEmpty()
                        filteredFlags
                    },
                isHiddenDeclaration = { isHiddenDeclaration(it, info.irBuiltInsCompat) },
                stableOrder = true,
                filePathRenderer = { _, fullPath ->
                    pathRelativizer.getRelativePath(fullPath)
                },
                printSourceOffsets = showOffsets,
            )
        val builder = baseDumper.builderForModule(module.name)

        for ((moduleAndFile, irFile) in
            info.irModuleFragment.files.groupWithTestFiles(testServices, ordered = true)) {
            if (moduleAndFile?.second?.directives?.contains(EXTERNAL_FILE) == true) continue
            val actualDump = irFile.dumpTreesFromLineNumber(lineNumber = 0, dumpOptions)
            builder.append(actualDump)
        }

        compareDumpsOfExternalClasses(module, info)
    }

    private fun compareDumpsOfExternalClasses(module: TestModule, info: IrBackendInput) {
        val externalClassIds = module.directives[DUMP_EXTERNAL_CLASS]
        if (externalClassIds.isEmpty()) return
        val dumpOptions = DumpIrTreeOptions(stableOrder = true, printFilePath = false)
        val baseFile = testServices.moduleStructure.originalTestDataFiles.first()
        assertions.assertAll(
            externalClassIds.map { externalClassId ->
                {
                    val classDump = info.findExternalClass(externalClassId).dumpOrFail(dumpOptions)
                    val suffix = ".__${externalClassId.replace("/", ".")}"
                    val expectedFile = baseFile.withSuffixAndExtension(suffix, getDumpExtension())
                    assertions.assertEqualsToFile(expectedFile, classDump)
                }
            }
        )
    }

    private fun IrBackendInput.findExternalClass(externalClassId: String): IrClass {
        val classId = ClassId.fromString(externalClassId)

        @OptIn(InternalSymbolFinderAPI::class)
        return irBuiltInsCompat.symbolFinder.findClass(classId)?.owner
            ?: throw TestInfrastructureException(
                "Can't find a class in external dependencies: $externalClassId"
            )
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) {
        val actualDump =
            if (baseDumper.isEmpty()) null else baseDumper.generateResultingDump().ifEmpty { null }
        val baseDumpExtension = getBaseDumpExtension()

        assertEqualsToDump(extension = baseDumpExtension, actualDump = actualDump)
    }

    private fun getBaseDumpExtension(): String {
        return customExtension ?: DUMP_EXTENSION
    }

    private fun getDumpExtension(): String {
        return customExtension ?: getBaseDumpExtension()
    }
}

private class IrFileEntryPathRelativizer(private val testServices: TestServices) {
    private val absolutePathPrefixes = linkedSetOf<String>()
    private val relativizedPathsCache = mutableMapOf<String, String>()

    fun addModule(module: TestModule) {
        absolutePathPrefixes.addAll(module.independentSourceDirectoryPathsTransitive(testServices))
    }

    fun getRelativePath(fullPath: String): String =
        relativizedPathsCache.getOrPut(fullPath) {
            absolutePathPrefixes.firstNotNullOfOrNull { absolutePathPrefix ->
                runIf(fullPath.startsWith(absolutePathPrefix)) {
                    fullPath.removePrefix(absolutePathPrefix)
                }
            } ?: fullPath
        }
}

private fun TestModule.independentSourceDirectoryPathsTransitive(
    testServices: TestServices
): List<String> {
    return buildList {
        addAll(transitiveRegularDependencies(includeSelf = true))
        addAll(transitiveFriendDependencies(includeSelf = false))
        addAll(transitiveDependsOnDependencies(includeSelf = false))
    }
        .map {
            it.independentSourceDirectoryPath(testServices)
        }
}

private fun IrElement.dumpOrFail(options: DumpIrTreeOptions = DumpIrTreeOptions()): String =
    StringBuilder()
        .also { sb ->
            accept(DumpIrTreeVisitor(sb, options), "")
        }
        .toString()
