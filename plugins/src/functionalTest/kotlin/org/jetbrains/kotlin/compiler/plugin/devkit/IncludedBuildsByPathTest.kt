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
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner

private const val DEVKIT_GROUP = "org.jetbrains.kotlin.compiler.plugin.devkit"

private const val INCLUDED_BUILDS_CLASS = "$DEVKIT_GROUP.IncludedBuildsKt"

private const val PROBE_TASK = "probeIncludedBuildTree"

private const val PROBE_ENTRY = "included-build-tree"

private const val WIRING_TASK = "probeWiring"

private const val WIRING_ENTRY = "included-build-tree-wiring"

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
    fun `finds itself, sibling, nested, and flat builds of the tree`() {
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
                ":" to project.rootDir.canonicalFile,
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
                ":" to project.rootDir.canonicalFile,
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
                ":" to project.rootDir.canonicalFile,
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
                ":" to project.rootDir.canonicalFile,
                ":b" to b.rootDir.canonicalFile,
                ":b:inner" to inner.rootDir.canonicalFile,
            ),
            probeTree(project.rootDir, "probeFromIncludedBuild"),
        )
        assertEquals(
            mapOf(
                ":" to b.rootDir.canonicalFile,
                ":inner" to inner.rootDir.canonicalFile,
            ),
            probeTree(b.rootDir),
        )
    }

    @Test
    fun `registers itself in a build without included builds`() {
        val project =
            newGradleProjectBuilder()
                .withRootProject {
                    withSettingsScript { rootProjectName = "root" }
                    buildScript = probeBuildScript()
                }
                .write()

        assertEquals(mapOf(":" to project.rootDir.canonicalFile), probeTree(project.rootDir))
    }

    @Test
    fun `finds a same-build project's task through the self entry`() {
        val project =
            newGradleProjectBuilder()
                .withRootProject {
                    withSettingsScript { rootProjectName = "root" }
                    buildScript =
                        probeBuildScript(
                            """
                            |tasks.register("$WIRING_TASK") {
                            |    doLast {
                            |        val self =
                            |            gradle.rootBuild().includedBuildsByPathUnderTest().getValue(Path.path(":"))
                            |        self.task(":lib:installForFunctionalTest")
                            |        println("$WIRING_ENTRY reachable")
                            |    }
                            |}
                            """
                                .trimMargin()
                        )
                }
                .withSubproject("lib") {
                    withBuildScript {
                        withKotlin(
                            """
                            |tasks.register("installForFunctionalTest") {}
                            """
                                .trimMargin()
                        )
                    }
                }
                .write()

        val output = probeOutput(project.rootDir, WIRING_TASK)
        assertTrue(
            output.lineSequence().any { it == "$WIRING_ENTRY reachable" },
            "Expected '$WIRING_ENTRY reachable' in the probe output:\n$output",
        )
    }

    private fun probeTree(
        projectDir: File,
        task: String = PROBE_TASK,
    ): Map<String, File> =
        probeOutput(projectDir, task)
            .lineSequence()
            .filter { it.startsWith("$PROBE_ENTRY ") }
            .associate { line ->
                val (_, path, dir) = line.split(" ", limit = 3)
                path to File(dir).canonicalFile
            }

    private fun probeOutput(projectDir: File, task: String): String =
        GradleRunner.create().withProjectDir(projectDir).withArguments(task).build().output

    private fun probeBuildScript(additions: String = ""): BuildScript =
        BuildScript.Builder()
            .apply {
                imports = Imports.of("org.gradle.util.Path")
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
                    fun Gradle.rootBuild(): Gradle = parent?.rootBuild() ?: this

                    fun Gradle.includedBuildsByPathUnderTest(): Map<Path, IncludedBuild> {
                        @Suppress("UNCHECKED_CAST")
                        val result =
                            Class.forName("$$INCLUDED_BUILDS_CLASS")
                                .getDeclaredMethod("includedBuildsByPath", Gradle::class.java)
                                .invoke(null, this) as Map<Path, IncludedBuild>
                        return result
                    }

                    tasks.register("$$PROBE_TASK") {
                        doLast {
                            for ((path, includedBuild) in gradle.rootBuild().includedBuildsByPathUnderTest()) {
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
