import org.jetbrains.kotlin.compiler.plugin.template.runners.AbstractJsBoxTest
import org.jetbrains.kotlin.compiler.plugin.template.runners.AbstractJvmBoxTest
import org.jetbrains.kotlin.compiler.plugin.template.runners.AbstractJvmDiagnosticTest
import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5

fun main(args: Array<String>) {
    generateTestGroupSuiteWithJUnit5 {
        for (dataRoot in args[1].split(',')) {
            testGroup(testsRoot = args[0], testDataRoot = dataRoot) {
                testClass<AbstractJvmDiagnosticTest> {
                    model("diagnostics")
                }

                testClass<AbstractJvmBoxTest> {
                    model("box")
                }

                testClass<AbstractJsBoxTest> {
                    model("box")
                }
            }
        }
    }
}
