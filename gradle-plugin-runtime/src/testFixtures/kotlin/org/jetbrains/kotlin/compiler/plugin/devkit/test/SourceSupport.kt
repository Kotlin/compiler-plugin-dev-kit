package org.jetbrains.kotlin.compiler.plugin.devkit.test

import com.autonomousapps.kit.Source
import com.autonomousapps.kit.Source.Companion.DEFAULT_SOURCE_SET
import com.autonomousapps.kit.Source.Companion.kotlin
import com.autonomousapps.kit.SourceType
import java.util.Locale
import org.gradle.testkit.runner.BuildResult
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

private val CLASS_NAME_REGEX = Regex("(class|object|interface) (?<name>[a-zA-Z0-9_]+)")
private val FUNCTION_NAME_REGEX = Regex("fun( <[a-zA-Z0-9_]+>)? (?<name>[a-zA-Z0-9_]+)")

/**
 * Builds a testkit [Source], prepending a `package` statement and [imports]. The file name is
 * [fileNameWithoutExtension], else inferred from the first declared type/function, else `"source"`.
 */
public fun source(
    @Language("kotlin") source: String,
    fileNameWithoutExtension: String? = null,
    packageName: String = "test",
    sourceSet: String = DEFAULT_SOURCE_SET,
    imports: List<String> = emptyList(),
): Source {
    @Suppress("DEPRECATION")
    val fileName =
        fileNameWithoutExtension
            ?: CLASS_NAME_REGEX.find(source)?.groups?.get("name")?.value
            ?: FUNCTION_NAME_REGEX.find(source)?.groups?.get("name")?.value?.capitalize(Locale.US)
            ?: "source"
    return kotlin(
            buildString {
                appendLine("package $packageName")
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

/** Returns a copy of this [Source] with new [newContent], keeping its file name and [imports]. */
public fun Source.copy(
    @Language("Kotlin") newContent: String,
    imports: List<String> = emptyList(),
): Source {
    return when (sourceType) {
        SourceType.KOTLIN -> source(newContent, fileNameWithoutExtension = name, imports = imports)
        else -> error("Unsupported source: $sourceType")
    }
}

public fun String.toKotlinVersion(): KotlinVersion = KotlinToolingVersion(this).toKotlinVersion()

/**
 * Asserts that the build output contains the expected text for the Kotlin version currently under
 * test ([TEST_COMPILER_VERSION]). [map] is keyed by Kotlin version string; the entry for the exact
 * version is used, otherwise the closest lower version's entry.
 */
public fun BuildResult.assertOutputContainsOnDifferentKotlinVersions(map: Map<String, String>) {
    val mapped = map.mapKeys { it.key.toKotlinVersion() }
    val testCompilerVersion = "$TEST_COMPILER_VERSION".toKotlinVersion()
    val outputForVersion =
        mapped[testCompilerVersion]
            ?: mapped.entries.filter { it.key <= testCompilerVersion }.maxByOrNull { it.key }?.value
            ?: error("No output found for version $testCompilerVersion or any lower version")
    assertOutputContains(outputForVersion)
}
