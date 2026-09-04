package org.jetbrains.kotlin.compiler.plugin.devkit.ide

/**
 * A highlight the IDE reported, flattened out of the Driver SDK model so it survives the IDE being
 * closed before the assertions run.
 */
public data class HighlightData(
    public val severity: String,
    public val description: String?,
    public val highlightedText: String?,
    /** 0-indexed source lines that contain [highlightedText]. */
    public val highlightedTextLines: List<Int>,
)

/** An inlay hint the IDE rendered, flattened for the same reason as [HighlightData]. */
public data class InlayData(
    public val offset: Int,
    public val text: String?,
    public val isBlock: Boolean,
)

/** Everything one source file's analysis produced, plus what it declared it should produce. */
public data class AnalyzedFile(
    /** Path relative to the project root, `/`-separated. */
    public val path: String,
    public val sourceText: String,
    public val expectations: IdeExpectations,
    public val highlights: List<HighlightData>,
    public val inlays: List<InlayData>,
)

/**
 * How far below its marker comment a diagnostic may be highlighted. Diagnostics can point at a
 * nested declaration, so the window has to allow some slack, while staying small enough that an
 * unrelated highlight elsewhere in the file can't satisfy the expectation.
 */
private const val DIAGNOSTIC_LINE_WINDOW = 8

/** Lookup from character offset to 0-indexed line number. */
internal fun lineStartOffsets(sourceText: String): List<Int> = buildList {
    add(0)
    sourceText.forEachIndexed { i, c -> if (c == '\n') add(i + 1) }
}

internal fun List<Int>.lineAtOffset(offset: Int): Int {
    val index = binarySearch(offset)
    return if (index >= 0) index else -(index + 1) - 1
}

private fun String.normalizedForMatching(): String =
    lineSequence().joinToString(" ") { it.trim() }.replace(Regex("\\s+"), " ")

/**
 * Source lines containing [highlightedText].
 *
 * The Driver SDK highlight exposes its text and message but not a source offset, so this is the
 * lightweight locality check that stands in for one: a diagnostic matches only if its highlighted
 * token appears near the marker, unless the IDE reported no highlighted text at all.
 */
internal fun sourceLinesContaining(sourceLines: List<String>, highlightedText: String?): List<Int> {
    if (highlightedText.isNullOrBlank()) return emptyList()
    return sourceLines.mapIndexedNotNull { index, line ->
        index.takeIf { line.contains(highlightedText) }
    }
}

/**
 * Whether [this] is the highlight [expected] describes.
 *
 * The diagnostic ID alone is not enough: some IDE versions put it in the description, which proves
 * the diagnostic kind but neither the message nor the source range. Requiring the message snippet
 * and a nearby highlighted token as well is what makes fixture drift fail loudly when a checker
 * moves or rewrites a diagnostic.
 */
internal fun HighlightData.matches(expected: ExpectedDiagnostic): Boolean {
    if (severity != expected.severity) return false
    val description = description ?: return false
    val lineIsClose =
        highlightedTextLines.isEmpty() ||
            highlightedTextLines.any {
                it in expected.line..(expected.line + DIAGNOSTIC_LINE_WINDOW)
            }
    if (!lineIsClose) return false

    val expectedDescription = expected.description.normalizedForMatching()
    if (!description.normalizedForMatching().contains(expectedDescription)) return false

    val hasDiagnosticId = description.contains(expected.diagnosticId)
    val highlightedText = highlightedText ?: return hasDiagnosticId
    return hasDiagnosticId || expectedDescription.contains(highlightedText)
}

/** Everything [AnalyzedFile] got wrong, one line each, empty when the file analysed as declared. */
internal fun AnalyzedFile.failures(): List<String> = buildList {
    for (expected in expectations.diagnostics) {
        if (highlights.none { it.matches(expected) }) add("Missing expected $expected")
    }
    for (highlight in highlights) {
        if (highlight.severity != "ERROR") continue
        if (expectations.diagnostics.none { highlight.matches(it) }) {
            add("Unexpected ERROR: ${highlight.description}")
        }
    }
    for (expected in expectations.inlays) {
        if (inlays.none { it.text?.contains(expected.text) == true })
            add("Missing expected $expected")
    }
}

/** Everything the IDE reported about [AnalyzedFile], for the failure message. */
internal fun AnalyzedFile.report(): String {
    val lineStarts = lineStartOffsets(sourceText)
    val highlightReport =
        highlights
            .filter { it.description != null }
            .joinToString("\n") {
                val lines =
                    it.highlightedTextLines.joinToString { line -> "${line + 1}" }.ifEmpty { "?" }
                "    [${it.severity} lines $lines] ${it.description} (text='${it.highlightedText}')"
            }
            .ifEmpty { "    (none)" }
    val inlayReport =
        inlays
            .joinToString("\n") {
                val line = lineStarts.lineAtOffset(it.offset) + 1
                "    [${if (it.isBlock) "block" else "inline"} line $line] ${it.text ?: "(no text)"}"
            }
            .ifEmpty { "    (none)" }
    return "  Highlights:\n$highlightReport\n  Inlays:\n$inlayReport"
}
