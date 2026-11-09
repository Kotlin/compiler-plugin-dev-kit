// Adapted from
// https://github.com/ZacSweers/metro/blob/009c44dad36e2151266bf6c6cff4fe187fc2b9db/gradle-plugin/src/functionalTest/kotlin/dev/zacsweers/metro/gradle/incremental/BaseIncrementalCompilationTest.kt
package org.jetbrains.kotlin.compiler.plugin.devkit.test

import com.autonomousapps.kit.GradleBuilder.build
import com.autonomousapps.kit.GradleBuilder.buildAndFail
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.Subproject
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.junit.Assume.assumeTrue
import org.junit.Before

/**
 * Base for JUnit4 [Parameterized][org.junit.runners.Parameterized] incremental-compilation tests:
 * it builds/rebuilds a generated multiplatform project for one [KmpTarget] and edits its sources
 * between compiles. Subclasses drive the parameter matrix via [KmpTarget.selectedTargets].
 */
public abstract class AbstractIncrementalCompilationTest(
    protected val target: KmpTarget,
    private val requiresMultiplatformIc: Boolean = true,
) {

    /** Imports re-applied to a [Source] when [modify] rewrites it. */
    protected open val defaultImports: List<String> = emptyList()

    @Before
    public fun assumeMultiplatformIcSupported() {
        if (!requiresMultiplatformIc) return
        assumeTrue(
            "KMP incremental compilation requires Kotlin $MULTIPLATFORM_IC_MIN_VERSION+",
            TEST_COMPILER_VERSION >= MULTIPLATFORM_IC_MIN_VERSION,
        )
    }

    @Before
    public fun assumeKlibCompatibility() {
        if (target == KmpTarget.JVM) return
        assumeTrue(
            "KLIBs are not forward compatible. The build uses $BUILD_COMPILER_VERSION " +
                "which is greater than $TEST_COMPILER_VERSION",
            TEST_COMPILER_VERSION >= BUILD_COMPILER_VERSION,
        )
    }

    protected val targetCompileTaskName: String
        get() = target.compileTaskName

    /**
     * The Gradle task path for [target]'s compile task. Empty [projectPath] targets the root
     * project, else a name like `"lib"` / `":lib"` targets that subproject.
     */
    protected fun compileTaskFor(projectPath: String = ""): String {
        val normalized = projectPath.trim(':')
        return if (normalized.isEmpty()) ":$targetCompileTaskName"
        else ":$normalized:$targetCompileTaskName"
    }

    protected inline fun ifJvmTarget(block: () -> Unit) {
        if (target == KmpTarget.JVM) block()
    }

    protected fun GradleProject.delete(source: Source) {
        rootDir.resolve("src/${source.sourceSet}/kotlin/${source.path}/${source.name}.kt").delete()
    }

    protected fun GradleProject.modify(source: Source, @Language("kotlin") content: String) {
        val newSource = source.copy(content, defaultImports)
        rootDir
            .resolve("src/${source.sourceSet}/kotlin/${newSource.path}/${newSource.name}.kt")
            .writeText(newSource.source)
    }

    protected fun Subproject.modify(
        rootDir: File,
        source: Source,
        @Language("kotlin") content: String,
        includeDefaultImports: Boolean = true,
    ) {
        val newSource =
            source.copy(content, if (includeDefaultImports) defaultImports else emptyList())
        val filePath = "src/${source.sourceSet}/kotlin/${newSource.path}/${newSource.name}.kt"
        subprojectDir(rootDir).resolve(filePath).writeText(newSource.source)
    }

    protected fun Subproject.delete(rootDir: File, source: Source) {
        val filePath = "src/${source.sourceSet}/kotlin/${source.path}/${source.name}.kt"
        subprojectDir(rootDir).resolve(filePath).delete()
    }

    private fun Subproject.subprojectDir(rootDir: File): File =
        rootDir.resolve(name.removePrefix(":").replace(":", "/"))

    protected fun modifyKotlinFile(
        rootDir: File,
        packageName: String,
        fileName: String,
        @Language("kotlin") content: String,
        sourceSet: String = "commonMain",
    ) {
        val packageDir = packageName.replace('.', '/')
        rootDir.resolve("src/$sourceSet/kotlin/$packageDir/$fileName").writeText(content)
    }

    protected fun GradleProject.compileKotlin(
        task: String = compileTaskFor(),
        debug: Boolean = false,
        vararg args: String,
    ): BuildResult = compileKotlin(rootDir, task, debug, *args)

    protected fun GradleProject.compileKotlinAndFail(
        task: String = compileTaskFor(),
        debug: Boolean = false,
        vararg args: String,
    ): BuildResult = compileKotlinAndFail(rootDir, task, debug, *args)

    protected fun compileKotlin(
        projectDir: File,
        task: String = compileTaskFor(),
        enableDebugger: Boolean = false,
        vararg args: String,
    ): BuildResult = build(projectDir, *buildArgs(task, enableDebugger, quiet = true, *args))

    protected fun compileKotlinAndFail(
        projectDir: File,
        task: String = compileTaskFor(),
        enableDebugger: Boolean = false,
        vararg args: String,
    ): BuildResult = buildAndFail(projectDir, *buildArgs(task, enableDebugger, quiet = true, *args))

    private fun buildArgs(
        task: String,
        enableDebugger: Boolean,
        quiet: Boolean,
        vararg args: String,
    ): Array<String> = buildList {
        add(task)
        // These tests assert raw diagnostic text; plain console keeps ANSI codes out of the output.
        add("--console=plain")
        if (enableDebugger) {
            add(GRADLE_DEBUG_ARGS)
            add(KOTLIN_DEBUG_ARGS)
        }
        if (quiet) add("--quiet")
        addAll(args)
    }
        .toTypedArray()

    private companion object {
        private val MULTIPLATFORM_IC_MIN_VERSION = KotlinToolingVersion("2.3.21")

        private const val GRADLE_DEBUG_ARGS = "-Dorg.gradle.debug=true"
        private const val KOTLIN_DEBUG_ARGS =
            """-Dkotlin.daemon.jvm.options="-agentlib:jdwp=transport=dt_socket\,server=n\,suspend=y\,address=5005""""
    }
}
