package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.File
import kotlin.reflect.jvm.jvmName
import org.jetbrains.kotlin.generators.dsl.TestGroup
import org.jetbrains.kotlin.generators.dsl.TestGroup.TestClass
import org.jetbrains.kotlin.generators.dsl.getDefaultSuiteTestClassName
import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5
import org.jetbrains.kotlin.generators.model.AnnotationModel
import org.jetbrains.kotlin.generators.util.TestGeneratorUtil

public class DevKitTestGroup(public val underlying: TestGroup, public val sourceSetName: String) {
    public val testDataRoot: String
        get() = underlying.testDataRoot

    public val testRunnerMethodName: String
        get() = underlying.testRunnerMethodName

    public val testClasses: List<TestClass>
        get() = underlying.testClasses

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
            init,
        )
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
        arrayOf("allowGenerationOnTeamCity", "skipTestAllFilesCheck", "tolerateEmptyModels"),
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

public fun suiteTestClassPackage(fqName: String, extra: String): String {
    val baseClassPackage = fqName.substringBeforeLast('.', "")
    return if (baseClassPackage.isEmpty()) extra else "$baseClassPackage.$extra"
}

public fun String.withPackage(pkg: String): String = if (pkg.isEmpty()) this else "$pkg.$this"
