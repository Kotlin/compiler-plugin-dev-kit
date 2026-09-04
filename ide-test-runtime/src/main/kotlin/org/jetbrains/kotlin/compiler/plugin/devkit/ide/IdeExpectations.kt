package org.jetbrains.kotlin.compiler.plugin.devkit.ide

/** Marker comment introducing an [ExpectedDiagnostic]. */
public const val EXPECT_DIAGNOSTIC_MARKER: String = "EXPECT_DIAGNOSTIC:"

/** Marker comment introducing an [ExpectedInlay]. */
public const val EXPECT_INLAY_MARKER: String = "EXPECT_INLAY:"

/** A highlight the IDE is expected to report, declared by a marker comment above the code. */
public data class ExpectedDiagnostic(
    public val diagnosticId: String,
    public val severity: String,
    public val description: String,
    /** 0-indexed line of the code the marker comment sits above. */
    public val line: Int,
) {
    override fun toString(): String = "$severity [$diagnosticId] at line ${line + 1}: $description"
}

/** An inlay hint the IDE is expected to render, declared by a marker comment above the code. */
public data class ExpectedInlay(
    public val text: String,
    /** 0-indexed line of the code the marker comment sits above. */
    public val line: Int,
) {
    override fun toString(): String = "inlay containing '$text' at line ${line + 1}"
}

/**
 * What one source file declares the IDE should report about it, through `// EXPECT_DIAGNOSTIC:` and
 * `// EXPECT_INLAY:` comments placed directly above the code they cover.
 */
public data class IdeExpectations(
    public val diagnostics: List<ExpectedDiagnostic>,
    public val inlays: List<ExpectedInlay>,
) {
    public val isEmpty: Boolean
        get() = diagnostics.isEmpty() && inlays.isEmpty()

    public companion object {
        public fun parse(sourceText: String): IdeExpectations {
            val diagnostics = mutableListOf<ExpectedDiagnostic>()
            val inlays = mutableListOf<ExpectedInlay>()
            sourceText.lines().forEachIndexed { index, rawLine ->
                val line = rawLine.trim()
                val comment =
                    line.removePrefix("//").takeIf { it != line }?.trim() ?: return@forEachIndexed
                comment
                    .removePrefix(EXPECT_DIAGNOSTIC_MARKER)
                    .takeIf { it != comment }
                    ?.let {
                        val parts = it.split(",", limit = 3)
                        require(parts.size == 3) {
                            "$EXPECT_DIAGNOSTIC_MARKER needs 3 comma-separated fields " +
                                "(id, severity, description): $rawLine"
                        }
                        diagnostics +=
                            ExpectedDiagnostic(
                                diagnosticId = parts[0].trim(),
                                severity = parts[1].trim().uppercase(),
                                description = parts[2].trim(),
                                line = index + 1,
                            )
                    }
                comment
                    .removePrefix(EXPECT_INLAY_MARKER)
                    .takeIf { it != comment }
                    ?.let {
                        inlays += ExpectedInlay(text = it.trim(), line = index + 1)
                    }
            }
            return IdeExpectations(diagnostics, inlays)
        }
    }
}
