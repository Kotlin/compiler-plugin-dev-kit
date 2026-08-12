package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.File
import org.jetbrains.kotlin.generators.dsl.TestGroup
import org.jetbrains.kotlin.generators.dsl.TestGroup.TestClass
import org.jetbrains.kotlin.generators.dsl.getDefaultSuiteTestClassName
import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5
import org.jetbrains.kotlin.generators.model.AnnotationModel
import org.jetbrains.kotlin.generators.model.SimpleTestClassModel

public data class SourceSetName(val name: String)

public object DevKitTestGenerator {
    public fun generate(args: Array<String>, block: context(SourceSetName) TestGroup.() -> Unit) {
        generateTestGroupSuiteWithJUnit5 {
            args
                .toList()
                .chunked(2) { it.takeIf { it.size == 2 }?.toList().orEmpty() }
                .forEach { (testsRoot, dataRoots) ->
                    val sourceSetName =
                        File(testsRoot).name.removeSuffix("test").removeSuffix("Test")
                    dataRoots.split(',').forEachIndexed { i, dataRoot ->
                        context(SourceSetName(sourceSetName + if (i == 0) "" else "_$i")) {
                            testGroup(testsRoot = testsRoot, testDataRoot = dataRoot) {
                                block()
                            }
                        }
                    }
                }
        }
    }
}

context(name: SourceSetName)
public inline fun <reified T> TestGroup.sourceSetTestClass(
    suiteTestClassName: String = getDefaultSuiteTestClassName(T::class.java.simpleName),
    suiteTestClassPackage: String = suiteTestClassPackage(T::class.java, name.name),
    annotations: List<AnnotationModel> = emptyList(),
    noinline init: TestClass.() -> Unit,
) {
    testClass<T>(suiteTestClassName.withPackage(suiteTestClassPackage), annotations) {
        init()
        val model = testModels.lastOrNull() as? SimpleTestClassModel ?: return@testClass
        if (!model.rootFile.exists()) testModels.remove(model)
    }
}

public fun suiteTestClassPackage(klass: Class<*>, extra: String): String {
    val baseClassPackage = klass.name.substringBeforeLast('.', "")
    return if (baseClassPackage.isEmpty()) extra else "$baseClassPackage.$extra"
}

public fun String.withPackage(pkg: String): String = if (pkg.isEmpty()) this else "$pkg.$this"
