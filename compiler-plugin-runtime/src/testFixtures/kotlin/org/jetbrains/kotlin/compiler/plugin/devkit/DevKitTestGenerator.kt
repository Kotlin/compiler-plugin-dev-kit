package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.File
import org.jetbrains.kotlin.generators.dsl.TestGroup
import org.jetbrains.kotlin.generators.dsl.TestGroup.TestClass
import org.jetbrains.kotlin.generators.dsl.getDefaultSuiteTestClassName
import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5
import org.jetbrains.kotlin.generators.model.AnnotationModel
import org.jetbrains.kotlin.generators.model.SimpleTestClassModel
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

data class SourceSetName(val name: String)

object DevKitTestGenerator {
    fun generate(args: Array<String>, block: context(SourceSetName) TestGroup.() -> Unit) =
        generateTestGroupSuiteWithJUnit5 {
            val testsRoots = mutableListOf<File>()
            args
                .toList()
                .chunked(2) { it.takeIf { it.size == 2 }?.toList().orEmpty() }
                .forEach { (testsRoot, dataRoots) ->
                    val sourceSetName =
                        File(testsRoot)
                            .also { testsRoots.add(it) }
                            .name
                            .capitalizeAsciiOnly()
                            .removeSuffix("test")
                            .removeSuffix("Test")
                    dataRoots.split(',').forEachIndexed { i, dataRoot ->
                        context(SourceSetName(sourceSetName + if (i == 0) "" else "_$i")) {
                            testGroup(testsRoot = testsRoot, testDataRoot = dataRoot) {
                                block()
                            }
                        }
                    }
                }
            val parentFile =
                testsRoots.firstOrNull()?.parentFile ?: return@generateTestGroupSuiteWithJUnit5
            if (testsRoots.any { it.parentFile != parentFile })
                return@generateTestGroupSuiteWithJUnit5
            parentFile.listFiles { it !in testsRoots }?.forEach { it.deleteRecursively() }
        }
}

context(name: SourceSetName)
inline fun <reified T> TestGroup.sourceSetTestClass(
    suiteTestClassName: String = getDefaultSuiteTestClassName(T::class.java.simpleName),
    annotations: List<AnnotationModel> = emptyList(),
    noinline init: TestClass.() -> Unit,
) {
    testClass<T>(name.name + suiteTestClassName, annotations) {
        init()
        val model = testModels.lastOrNull() as? SimpleTestClassModel ?: return@testClass
        if (!model.rootFile.exists()) testModels.remove(model)
    }
}
