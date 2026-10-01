package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.File
import kotlin.reflect.jvm.jvmName
import org.jetbrains.kotlin.generators.dsl.TestGroup
import org.jetbrains.kotlin.generators.dsl.TestGroup.TestClass
import org.jetbrains.kotlin.generators.dsl.getDefaultSuiteTestClassName
import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5
import org.jetbrains.kotlin.generators.model.AnnotationModel
import org.jetbrains.kotlin.generators.model.SimpleTestClassModel
import org.jetbrains.kotlin.generators.util.TestGeneratorUtil

public class DevKitTestGroup(public val underlying: TestGroup, public val sourceSetName: String) {
    public val testDataRoot: String
        get() = underlying.testDataRoot

    public val testRunnerMethodName: String
        get() = underlying.testRunnerMethodName

    public val testClasses: List<TestClass>
        get() = underlying.testClasses

    @Deprecated(
        DEPRECATION_MESSAGE,
        replaceWith =
            ReplaceWith(
                "testClass<T>(suiteTestClassName, suiteTestClassPackage, annotations, init)"
            ),
    )
    public inline fun <reified T : Any> sourceSetTestClass(
        suiteTestClassName: String = getDefaultSuiteTestClassName(T::class.java.simpleName),
        suiteTestClassPackage: String = suiteTestClassPackage(T::class.jvmName, sourceSetName),
        annotations: List<AnnotationModel> = emptyList(),
        noinline init: TestClass.() -> Unit,
    ): Unit = testClass<T>(suiteTestClassName, suiteTestClassPackage, annotations, init)

    public inline fun <reified T : Any> testClass(
        suiteTestClassName: String = getDefaultSuiteTestClassName(T::class.java.simpleName),
        suiteTestClassPackage: String = suiteTestClassPackage(T::class.jvmName, sourceSetName),
        annotations: List<AnnotationModel> = emptyList(),
        noinline init: TestClass.() -> Unit,
    ) {
        val testKClass = T::class.java
        testClass(
            testKClass,
            testKClass.name,
            suiteTestClassName,
            suiteTestClassPackage,
            annotations,
            init,
        )
    }

    public fun testClass(
        testKClass: Class<*>,
        baseTestClassName: String = testKClass.name,
        suiteTestClassName: String =
            getDefaultSuiteTestClassName(baseTestClassName.substringAfterLast('.')),
        suiteTestClassPackage: String = suiteTestClassPackage(baseTestClassName, sourceSetName),
        annotations: List<AnnotationModel> = emptyList(),
        init: TestClass.() -> Unit,
    ) {
        underlying.testClass(
            testKClass,
            baseTestClassName,
            suiteTestClassName.withPackage(suiteTestClassPackage),
            annotations,
        ) {
            init()
            val model = testModels.lastOrNull() as? SimpleTestClassModel ?: return@testClass
            if (!model.rootFile.exists()) testModels.remove(model)
        }
    }
}

private fun parseRoots(roots: List<String>): List<Pair<String, List<String>>> =
    roots.windowed(2, 2, partialWindows = false) { (testsRoot, dataRoots) ->
        testsRoot to dataRoots.split(',')
    }

private const val rootsProp = "kotlin.compiler.plugin.devkit.roots"

public fun generateDevKitTestsWithJUnit5(
    mainClassName: String? = TestGeneratorUtil.getMainClassName(),
    init: DevKitTestGroup.() -> Unit,
) {
    generateTestGroupSuiteWithJUnit5(
        arrayOf("allowGenerationOnTeamCity", "skipTestAllFilesCheck"),
        mainClassName = mainClassName,
    ) {
        System.getProperty(rootsProp)?.split(';')?.let(::parseRoots)?.forEach {
            (testsRoot, dataRoots) ->
            val sourceSetName = File(testsRoot).name.removeSuffix("test").removeSuffix("Test")
            dataRoots.forEachIndexed { i, dataRoot ->
                testGroup(testsRoot = testsRoot, testDataRoot = dataRoot) {
                    DevKitTestGroup(this, sourceSetName + if (i == 0) "" else "_$i").init()
                }
            }
        }
    }
}

internal const val DEPRECATION_MESSAGE = "Use the generateDevKitTestsWithJUnit5 api instead"

@Deprecated(DEPRECATION_MESSAGE) public data class SourceSetName(val name: String)

public object DevKitTestGenerator {
    @Deprecated(
        DEPRECATION_MESSAGE,
        replaceWith = ReplaceWith("generateDevKitTestsWithJUnit5(block)"),
    )
    @Suppress("DEPRECATION")
    public fun generate(args: Array<String>, block: context(SourceSetName) TestGroup.() -> Unit) {
        generateDevKitTestsWithJUnit5 { block(SourceSetName(this.sourceSetName), underlying) }
    }
}

@Deprecated(DEPRECATION_MESSAGE)
@Suppress("DEPRECATION")
context(name: SourceSetName)
public inline fun <reified T : Any> TestGroup.sourceSetTestClass(
    suiteTestClassName: String = getDefaultSuiteTestClassName(T::class.java.simpleName),
    suiteTestClassPackage: String = suiteTestClassPackage(T::class.jvmName, name.name),
    annotations: List<AnnotationModel> = emptyList(),
    noinline init: TestClass.() -> Unit,
) {
    DevKitTestGroup(this, name.name)
        .testClass<T>(suiteTestClassName, suiteTestClassPackage, annotations, init)
}

public fun suiteTestClassPackage(fqName: String, extra: String): String {
    val baseClassPackage = fqName.substringBeforeLast('.', "")
    return if (baseClassPackage.isEmpty()) extra else "$baseClassPackage.$extra"
}

public fun String.withPackage(pkg: String): String = if (pkg.isEmpty()) this else "$pkg.$this"
