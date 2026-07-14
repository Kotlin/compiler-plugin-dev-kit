// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.gradle.incremental

import com.autonomousapps.kit.GradleProject
import java.io.File
import org.jetbrains.kotlin.compiler.plugin.devkit.test.AbstractIncrementalCompilationTest
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KmpTarget
import org.jetbrains.kotlin.compiler.plugin.devkit.test.getTestCompilerVersion
import org.jetbrains.kotlin.compiler.plugin.devkit.test.resolveSafe
import org.junit.Assume.assumeFalse
import org.junit.Before


/**
 * Kotlin/JS and Kotlin/Wasm IC trip on top-level declaration generation in these specific Kotlin
 * builds (Metro uses top-level declarations by default for `enableTopLevelFunctionInjection` /
 * `generateContributionHints` / `generateContributionHintsInFir`).
 *
 * See https://youtrack.jetbrains.com/issue/KT-82395 and
 * https://youtrack.jetbrains.com/issue/KT-82989.
 */
private val JS_WASM_IC_TOP_LEVEL_BROKEN_VERSIONS = setOf("2.4.0-Beta1", "2.4.0-dev-2124")

abstract class BaseIncrementalCompilationTest(target: KmpTarget) :
  AbstractIncrementalCompilationTest(target) {

  override val defaultImports = listOf("dev.zacsweers.metro.*")

  @Before
  fun assumeJsAndWasmTopLevelDeclarationsSupported() {
    if (target != KmpTarget.JS && target != KmpTarget.WASM_JS) return
    assumeFalse(
      "Kotlin/$target IC cannot generate top-level declarations on " +
        "${getTestCompilerVersion()} (KT-82395, KT-82989)",
      getTestCompilerVersion() in JS_WASM_IC_TOP_LEVEL_BROKEN_VERSIONS,
    )
  }

  protected val GradleProject.asMetroProject: MetroGradleProject
    get() = MetroGradleProject(rootDir)

  protected fun GradleProject.metroProject(path: String): MetroGradleProject {
    return MetroGradleProject(rootDir.resolve(path))
  }

  @JvmInline protected value class MetroGradleProject(val rootDir: File)

  protected val MetroGradleProject.buildDir: File
    get() = rootDir.resolve("build")

  protected val MetroGradleProject.metroDir: File
    get() = buildDir.resolve("metro")

  protected fun MetroGradleProject.reports(compilation: String): Reports =
    metroDir.resolveSafe(compilation).let(::Reports)

  // Metro's reports layout is `{reportsDestination}/{targetName}/{compilationName}/`, so the
  // current parameterized target picks the `<target>/main` slice.
  protected val MetroGradleProject.mainReports: Reports
    get() = reports("${target.gradleTargetName}/main")

  protected val MetroGradleProject.appGraphReports: GraphReports
    get() = mainReports.forGraph("test/AppGraph/Impl")

  class Reports(val dir: File) {
    val expectActualReports
      get() = dir.resolveSafe("expectActualReports.csv").readText()

    val lookups
      get() = dir.resolveSafe("lookups.csv").readText()

    val log
      get() = dir.resolveSafe("log.txt").readText()

    val trace
      get() = dir.resolveSafe("trace").listFiles().single()

    fun irHintsForScope(scopeFqName: String): String {
      return dir.resolveSafe("discovered-hints-ir/$scopeFqName.txt").readText()
    }

    fun firHintsForScope(scopeFqName: String): String {
      return dir.resolveSafe("discovered-hints-fir/$scopeFqName.txt").readText()
    }

    fun unmatchedExclusionsIr(scopeFqName: String): String {
      return dir.resolveSafe("merging-unmatched-exclusions-ir/$scopeFqName.txt").readText()
    }

    fun unmatchedReplacementsIr(scopeFqName: String): String {
      return dir.resolveSafe("merging-unmatched-replacements-ir/$scopeFqName.txt").readText()
    }

    fun unmatchedRankReplacementsIr(scopeFqName: String): String {
      return dir.resolveSafe("merging-unmatched-rank-replacements-ir/$scopeFqName.txt").readText()
    }

    fun forGraph(implFqName: String): GraphReports {
      return GraphReports(dir, implFqName)
    }
  }

  // TODO shared model?
  class GraphReports(val reportsDir: File, val implFqName: String) {
    private fun readFileLines(path: String, extension: String = "txt"): List<String> {
      return reportsDir.resolveSafe("$path.$extension").readLines()
    }

    private fun readFile(pathWithExtension: String): String {
      return reportsDir.resolveSafe(pathWithExtension).readText()
    }

    val keysPopulated
      get() = readFileLines("keys-populated/$implFqName")

    val providerPropertyKeys
      get() = readFileLines("keys-providerProperties/$implFqName")

    val scopedProviderPropertyKeys
      get() = readFileLines("keys-scopedProviderProperties/$implFqName")

    val deferred
      get() = readFileLines("keys-deferred/$implFqName")

    val dumpKotlinLike
      get() = readFile("graph-dumpKotlin/$implFqName.kt")

    val dump
      get() = readFileLines("graph-dump/$implFqName")

    val bindingContainers
      get() = readFileLines("graph-dump/$implFqName")

    val keysValidated
      get() = readFileLines("keys-validated/$implFqName")

    val keysUnused
      get() = readFileLines("keys-unused/$implFqName")

    val metadata
      get() = readFile("graph-metadata/$implFqName.kt")

    val parentUsedKeysAll
      get() = readFile("parent-keys-used-all/$implFqName")

    fun parentKeysUsedBy(extension: String) =
      readFileLines("parent-keys-used/$implFqName-by-$extension.txt")

    fun graphMetadata() {
      // /graph-metadata/graph-test-AppGraph.json"
      // /graph-metadata/graph-test-AppGraph2.json"
      TODO()
    }

    fun unmatchedExclusionsFir(graphFqName: String): String {
      return reportsDir.resolveSafe("merging-unmatched-exclusions-fir/$graphFqName.txt").readText()
    }

    fun unmatchedReplacementsFir(graphFqName: String): String {
      return reportsDir
        .resolveSafe("merging-unmatched-replacements-fir/$graphFqName.txt")
        .readText()
    }

    fun unmatchedRankReplacementsFir(graphFqName: String): String {
      return reportsDir
        .resolveSafe("merging-unmatched-rank-replacements-fir/$graphFqName.txt")
        .readText()
    }
  }
}
