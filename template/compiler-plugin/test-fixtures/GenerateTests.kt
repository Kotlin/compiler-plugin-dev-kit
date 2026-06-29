import org.jetbrains.kotlin.compiler.plugin.template.runners.AbstractJsBoxTest
import org.jetbrains.kotlin.compiler.plugin.template.runners.AbstractJvmBoxTest
import org.jetbrains.kotlin.compiler.plugin.template.runners.AbstractJvmDiagnosticTest
import org.jetbrains.kotlin.generators.dsl.TestGroup
import org.jetbrains.kotlin.generators.dsl.TestGroup.TestClass
import org.jetbrains.kotlin.generators.dsl.getDefaultSuiteTestClassName
import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5
import org.jetbrains.kotlin.generators.model.AnnotationModel
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import java.io.File

fun main(args: Array<String>) {
    generateTestGroupSuiteWithJUnit5 {
        args
            .toList()
            .chunked(2) { it.takeIf { it.size == 2 }?.toList().orEmpty() }
            .forEach { (testsRoot, dataRoots) ->
                val sourceSetName =
                    File(testsRoot)
                        .name
                        .capitalizeAsciiOnly()
                        .removeSuffix("test")
                        .removeSuffix("Test")
                for (dataRoot in dataRoots.split(',')) {
                    testGroup(testsRoot = testsRoot, testDataRoot = dataRoot) {
                        testClassWithPrefix<AbstractJvmDiagnosticTest>(sourceSetName) {
                            model("diagnostics")
                        }

                        testClassWithPrefix<AbstractJvmBoxTest>(sourceSetName) {
                            model("box")
                        }

                        testClassWithPrefix<AbstractJsBoxTest>(sourceSetName) {
                            model("box")
                        }
                    }
                }
            }
    }
}

private inline fun <reified T> TestGroup.testClassWithPrefix(
    prefix: String,
    suiteTestClassName: String = getDefaultSuiteTestClassName(T::class.java.simpleName),
    annotations: List<AnnotationModel> = emptyList(),
    noinline init: TestClass.() -> Unit,
) = testClass<T>(prefix + suiteTestClassName, annotations, init)
