package org.jetbrains.kotlin.compiler.plugin.devkit.ide

import com.intellij.ide.starter.report.ErrorReporterToCI
import java.nio.file.Path
import kotlin.io.path.readLines

/** Context lines kept before and after a log line that matched, so a stack trace comes with it. */
private const val LOG_CONTEXT_BEFORE = 5
private const val LOG_CONTEXT_AFTER = 2

/**
 * The IDE's own error reports, kept only where the plugin under test is named.
 *
 * These are exceptions the IDE caught and the performance testing plugin wrote out. Filtering *in*
 * the plugin's own patterns is the only workable direction: an IDE run reports plenty of errors
 * that have nothing to do with the plugin.
 */
internal fun collectPluginErrors(logsDir: Path, errorPatterns: List<String>): List<String> =
    ErrorReporterToCI.collectErrors(logsDir)
        .filter { error ->
            errorPatterns.any {
                error.messageText.contains(it, ignoreCase = true) ||
                    error.stackTraceContent.contains(it, ignoreCase = true)
            }
        }
        .map { "${it.messageText}\n${it.stackTraceContent}" }

/**
 * Context around every `idea.log` line matching one of [patterns], overlapping windows merged.
 *
 * The fallback for when analysis died without the IDE turning it into a structured error report.
 */
internal fun findLogMentions(logsDir: Path, patterns: List<String>): String? {
    if (patterns.isEmpty()) return null
    val lines = runCatching { logsDir.resolve("idea.log").readLines() }.getOrNull() ?: return null
    val matches = lines.indices.filter { i -> patterns.any { lines[i].contains(it) } }
    if (matches.isEmpty()) return null
    val context = buildSet {
        for (index in matches) {
            addAll(
                (index - LOG_CONTEXT_BEFORE).coerceAtLeast(0)..(index + LOG_CONTEXT_AFTER)
                        .coerceAtMost(lines.lastIndex)
            )
        }
    }
    return context.sorted().joinToString("\n") { lines[it] }
}
