package org.jetbrains.kotlin.compiler.plugin.devkit

import com.autonomousapps.kit.AbstractGradleProject
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.gradle.BuildScript
import com.autonomousapps.kit.gradle.BuildscriptBlock
import com.autonomousapps.kit.gradle.Dependency
import com.autonomousapps.kit.gradle.Imports
import com.autonomousapps.kit.gradle.Repository
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.gradle.testkit.runner.GradleRunner

private const val DEVKIT_GROUP = "org.jetbrains.kotlin.compiler.plugin.devkit"

private const val PROBE_TASK = "probeIncludedBuildTree"

private const val PROBE_ENTRY = "included-build-tree"

class IncludedBuildsByPathTest : AbstractGradleProject() {
    // The fixtures declare included builds next to the root build by "../name" paths. This
    // override puts the root build in a subdirectory of the test's root directory, so those
    // builds stay inside it. Two concurrently running tests can't then write the same build
    // directory.
    override fun newGradleProjectBuilder(dslKind: GradleProject.DslKind): GradleProject.Builder =
        GradleProject.Builder(rootDir.resolve("root").toFile(), dslKind)

    override fun newGradleProjectBuilder(): GradleProject.Builder =
        newGradleProjectBuilder(GradleProject.DslKind.KOTLIN)

    @Test
    fun `finds sibling, nested, and flat builds of the tree`() {
        val project =
            newGradleProjectBuilder()
                .withRootProject {
                    withSettingsScript {
                        rootProjectName = "root"
                        additions =
                            """
                            |includeBuild("../lib")
                            |includeBuild("../outer")
                            """
                                .trimMargin()
                    }
                    buildScript = probeBuildScript()
                }
                .withSiblingBuild("lib") {}
                .withSiblingBuild("outer") {
                    withRootProject {
                            settingsScript.additions =
                                """
                                |includeBuild("inner")
                                |includeBuild("../flat")
                                """
                                    .trimMargin()
                        }
                        .withIncludedBuild("inner") {}
                        .withSiblingBuild("flat") {}
                }
                .write()

        val outer = project.getIncludedBuild("outer")
        assertEquals(
            mapOf(
                ":flat" to outer.getIncludedBuild("flat").rootDir.canonicalFile,
                ":lib" to project.getIncludedBuild("lib").rootDir.canonicalFile,
                ":outer" to outer.rootDir.canonicalFile,
                ":outer:inner" to outer.getIncludedBuild("inner").rootDir.canonicalFile,
            ),
            probeTree(project.rootDir),
        )
    }

    @Test
    fun `distinguishes same-named builds by path`() {
        val project =
            newGradleProjectBuilder()
                .withRootProject {
                    withSettingsScript {
                        rootProjectName = "root"
                        additions =
                            """
                            |includeBuild("../x")
                            |includeBuild("../y")
                            """
                                .trimMargin()
                    }
                    buildScript = probeBuildScript()
                }
                .withSiblingBuild("x") {
                    withRootProject { settingsScript.additions = """includeBuild("dup")""" }
                        .withIncludedBuild("dup") {}
                }
                .withSiblingBuild("y") {
                    withRootProject { settingsScript.additions = """includeBuild("dup")""" }
                        .withIncludedBuild("dup") {}
                }
                .write()

        val x = project.getIncludedBuild("x")
        val y = project.getIncludedBuild("y")
        assertEquals(
            mapOf(
                ":x" to x.rootDir.canonicalFile,
                ":x:dup" to x.getIncludedBuild("dup").rootDir.canonicalFile,
                ":y" to y.rootDir.canonicalFile,
                ":y:dup" to y.getIncludedBuild("dup").rootDir.canonicalFile,
            ),
            probeTree(project.rootDir),
        )
    }

    @Test
    fun `terminates on cyclic includes`() {
        val project =
            newGradleProjectBuilder()
                .withRootProject {
                    withSettingsScript {
                        rootProjectName = "root"
                        additions = """includeBuild("../x")"""
                    }
                    buildScript = probeBuildScript()
                }
                .withSiblingBuild("x") {
                    withRootProject { settingsScript.additions = """includeBuild("../y")""" }
                        .withSiblingBuild("y") {
                            withRootProject {
                                settingsScript.additions = """includeBuild("../x")"""
                            }
                        }
                }
                .write()

        assertEquals(
            mapOf(
                ":x" to project.getIncludedBuild("x").rootDir.canonicalFile,
                ":y" to project.getIncludedBuild("x").getIncludedBuild("y").rootDir.canonicalFile,
            ),
            probeTree(project.rootDir),
        )
    }

    @Test
    fun `covers the composite from an included build, and only its own tree when it runs alone`() {
        val project =
            newGradleProjectBuilder()
                .withRootProject {
                    withSettingsScript {
                        rootProjectName = "root"
                        additions = """includeBuild("../b")"""
                    }
                    buildScript =
                        probeBuildScript(
                            """
                            |tasks.register("probeFromIncludedBuild") {
                            |    dependsOn(gradle.includedBuild("b").task(":$PROBE_TASK"))
                            |}
                            """
                                .trimMargin()
                        )
                }
                .withSiblingBuild("b") {
                    withRootProject {
                            settingsScript.additions = """includeBuild("inner")"""
                            buildScript = probeBuildScript()
                        }
                        .withIncludedBuild("inner") {}
                }
                .write()

        val b = project.getIncludedBuild("b")
        val inner = b.getIncludedBuild("inner")
        assertEquals(
            mapOf(
                ":b" to b.rootDir.canonicalFile,
                ":b:inner" to inner.rootDir.canonicalFile,
            ),
            probeTree(project.rootDir, "probeFromIncludedBuild"),
        )
        assertEquals(mapOf(":inner" to inner.rootDir.canonicalFile), probeTree(b.rootDir))
    }

    private fun probeTree(
        projectDir: File,
        task: String = PROBE_TASK,
    ): Map<String, File> =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(task)
            .build()
            .output
            .lineSequence()
            .filter { it.startsWith("$PROBE_ENTRY ") }
            .associate { line ->
                val (_, path, dir) = line.split(" ", limit = 3)
                path to File(dir).canonicalFile
            }

    private fun probeBuildScript(additions: String = ""): BuildScript =
        BuildScript.Builder()
            .apply {
                imports =
                    Imports.of(
                        "org.gradle.internal.composite.IncludedBuildInternal",
                        "org.gradle.util.Path",
                    )
                buildscript =
                    BuildscriptBlock(
                        (Repository.FUNC_TEST_INCLUDED_BUILDS + Repository.DEFAULT_PLUGINS)
                            .toMutableList(),
                        mutableListOf(
                            Dependency(
                                "classpath",
                                "$DEVKIT_GROUP:plugins:$PLUGIN_UNDER_TEST_VERSION",
                            )
                        ),
                    )
                withKotlin(
                    $$"""
                    internal val IncludedBuild.gradle: Gradle?
                        get() = (this as? IncludedBuildInternal)?.target?.mutableModel
                    
                    internal tailrec fun Gradle.root(): Gradle =
                        when (val parent = parent) {
                            null -> this
                            else -> parent.root()
                        }
                    
                    internal val IncludedBuild.buildPath: Path?
                        get() = (this as? IncludedBuildInternal)?.target?.identityPath
                    
                    internal fun Gradle.includedBuildsByPath(): Map<Path, IncludedBuild> = buildMap {
                        val visited = HashSet<Gradle>()
                        val visitBuildTree = DeepRecursiveFunction { build ->
                            if (!visited.add(build)) return@DeepRecursiveFunction
                            for (included in build.includedBuilds) {
                                put(included.buildPath ?: continue, included)
                                included.gradle?.let { callRecursive(it) }
                            }
                        }
                        visitBuildTree(this@includedBuildsByPath)
                    }

                    tasks.register("$$PROBE_TASK") {
                        doLast {
                            for ((path, includedBuild) in gradle.root().includedBuildsByPath()) {
                                println("$$PROBE_ENTRY $path ${includedBuild.projectDir}")
                            }
                        }
                    }
                    $$additions
                    """
                )
            }
            .build()

    // The testkit names an included build after its include path. For a build next to the including
    // build, that path starts with "../", so this restores the real build name.
    private fun GradleProject.Builder.withSiblingBuild(
        name: String,
        block: GradleProject.Builder.() -> Unit = {},
    ): GradleProject.Builder =
        withIncludedBuild("../$name") {
            withRootProject { settingsScript.rootProjectName = name }
            block()
        }
}
