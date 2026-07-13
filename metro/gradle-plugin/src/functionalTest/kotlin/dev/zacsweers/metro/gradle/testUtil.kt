// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.gradle

import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.Source.Companion.DEFAULT_SOURCE_SET
import com.autonomousapps.kit.Source.Companion.kotlin
import com.autonomousapps.kit.SourceType
import java.util.Locale
import org.gradle.testkit.runner.BuildResult
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.assertOutputContains
import org.jetbrains.kotlin.compiler.plugin.devkit.test.classLoader
import org.jetbrains.kotlin.compiler.plugin.devkit.test.getTestCompilerVersion

// TODO dedupe with MetroCompilerTest
private val CLASS_NAME_REGEX = Regex("(class|object|interface) (?<name>[a-zA-Z0-9_]+)")
private val FUNCTION_NAME_REGEX = Regex("fun( <[a-zA-Z0-9_]+>)? (?<name>[a-zA-Z0-9_]+)")
private val DEFAULT_IMPORTS = listOf("dev.zacsweers.metro.*")

/** Returns a [Source] representation of this [source]. This includes common imports from Metro. */
fun source(
  @Language("kotlin") source: String,
  fileNameWithoutExtension: String? = null,
  packageName: String = "test",
  sourceSet: String = DEFAULT_SOURCE_SET,
  includeDefaultImports: Boolean = true,
  vararg extraImports: String,
): Source {
  @Suppress("DEPRECATION")
  val fileName =
    fileNameWithoutExtension
      ?: CLASS_NAME_REGEX.find(source)?.groups?.get("name")?.value
      ?: FUNCTION_NAME_REGEX.find(source)?.groups?.get("name")?.value?.capitalize(Locale.US)
      ?: "source"
  return kotlin(
      buildString {
        // Package statement
        appendLine("package $packageName")

        // Imports
        val imports = buildList {
          if (includeDefaultImports) {
            addAll(DEFAULT_IMPORTS)
          }
          addAll(extraImports)
        }
        for (import in imports) {
          appendLine("import $import")
        }

        appendLine()
        appendLine()
        appendLine(source.trimIndent())
      }
    )
    .withSourceSet(sourceSet)
    .withPath(packageName, fileName)
    .build()
}

fun Source.copy(
  @Language("Kotlin") newContent: String,
  includeDefaultImports: Boolean = true,
): Source {
  return when (sourceType) {
    SourceType.KOTLIN -> {
      source(
        newContent,
        fileNameWithoutExtension = name,
        includeDefaultImports = includeDefaultImports,
      )
    }
    else -> error("Unsupported source: $sourceType")
  }
}

fun String.toKotlinVersion(): KotlinVersion =
  substringBefore("-").split(".").let { (major, minor, patch) ->
    KotlinVersion(major.toInt(), minor.toInt(), patch.toInt())
  }

// Overload that accepts a map of exp
fun BuildResult.assertOutputContainsOnDifferentKotlinVersions(map: Map<String, String>) {
  val mapped = map.mapKeys { it.key.toKotlinVersion() }
  val testCompilerVersion = getTestCompilerVersion().toKotlinVersion()
  val outputForVersion =
    mapped[testCompilerVersion]
      ?: mapped.entries.filter { it.key <= testCompilerVersion }.maxByOrNull { it.key }?.value
      ?: error("No output found for version $testCompilerVersion or any lower version")
  assertOutputContains(outputForVersion)
}
