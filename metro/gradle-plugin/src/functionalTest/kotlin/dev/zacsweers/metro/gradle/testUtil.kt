// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.gradle

import com.autonomousapps.kit.Source
import com.autonomousapps.kit.Source.Companion.DEFAULT_SOURCE_SET
import com.autonomousapps.kit.SourceType
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.source as devkitSource

private val DEFAULT_IMPORTS = listOf("dev.zacsweers.metro.*")

/** Returns a [Source] representation of this [source]. This includes common imports from Metro. */
fun source(
  @Language("kotlin") source: String,
  fileNameWithoutExtension: String? = null,
  packageName: String = "test",
  sourceSet: String = DEFAULT_SOURCE_SET,
  includeDefaultImports: Boolean = true,
  vararg extraImports: String,
): Source =
  devkitSource(
    source,
    fileNameWithoutExtension,
    packageName,
    sourceSet,
    buildList {
      if (includeDefaultImports) addAll(DEFAULT_IMPORTS)
      addAll(extraImports)
    },
  )

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