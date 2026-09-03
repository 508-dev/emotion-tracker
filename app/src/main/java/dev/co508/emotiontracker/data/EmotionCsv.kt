package dev.co508.emotiontracker.data

import dev.co508.emotiontracker.data.db.EmotionEntryEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV import/export for the journal — the "Export to CSV", "Download
 * template CSV", and "Restore from CSV" actions on the Settings screen.
 *
 * The format is deliberately hand-editable: [export] writes both the
 * machine-authoritative `emotionId` (for an exact round trip) and a
 * human-readable `emotion` label path (e.g. "Positive > Calm > Content >
 * Satisfied", the same text the wheel's breadcrumbs show), so a template
 * filled in by hand doesn't require knowing raw tree ids. [parse] prefers
 * `emotionId` when it resolves and falls back to the label path otherwise.
 */
object EmotionCsv {
    private const val HEADER = "recordedAt,emotion,emotionId,note"
    private val LOCAL_DATE_TIME_FORMATS =
        listOf(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]"),
        )

    /** One CSV data row that failed to resolve, for surfacing a "N rows skipped" summary. */
    data class RowError(
        val lineNumber: Int,
        val reason: String,
    )

    data class ParseResult(
        val entries: List<EmotionEntryEntity>,
        val errors: List<RowError>,
    )

    fun export(
        entries: List<JournalEntry>,
        tree: EmotionTree,
    ): String =
        buildString {
            append(HEADER).append("\r\n")
            entries.forEach { entry ->
                val recordedAt = Instant.ofEpochMilli(entry.recordedAtEpochMillis).toString()
                val emotionPath = tree.labelPath(entry.emotionId) ?: entry.emotionId
                append(csvRow(recordedAt, emotionPath, entry.emotionId, entry.note.orEmpty()))
            }
        }

    /** Header only — the blank starting point for a hand-authored restore file. */
    fun template(): String = "$HEADER\r\n"

    fun parse(
        text: String,
        tree: EmotionTree,
    ): ParseResult {
        val byId = tree.flattenById()
        val rows = parseCsvRows(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return ParseResult(emptyList(), emptyList())

        val entries = mutableListOf<EmotionEntryEntity>()
        val errors = mutableListOf<RowError>()

        rows.drop(1).forEachIndexed { index, row ->
            val lineNumber = index + 2 // 1-indexed, plus the header row
            if (row.all { it.isBlank() }) return@forEachIndexed

            val recordedAtRaw = row.getOrElse(0) { "" }.trim()
            val emotionPathRaw = row.getOrElse(1) { "" }.trim()
            val emotionIdRaw = row.getOrElse(2) { "" }.trim()
            val note = row.getOrElse(3) { "" }.trim().ifBlank { null }

            val recordedAt = parseTimestamp(recordedAtRaw)
            if (recordedAt == null) {
                errors += RowError(lineNumber, "Unreadable date/time \"$recordedAtRaw\"")
                return@forEachIndexed
            }

            val emotionId =
                emotionIdRaw.takeIf { byId.containsKey(it) }
                    ?: emotionPathRaw.takeIf { it.isNotBlank() }?.let(tree::resolveLabelPath)
            if (emotionId == null) {
                val shown = emotionIdRaw.ifBlank { emotionPathRaw }
                errors += RowError(lineNumber, "Unrecognized emotion \"$shown\"")
                return@forEachIndexed
            }

            entries += EmotionEntryEntity(emotionId = emotionId, recordedAtEpochMillis = recordedAt, note = note)
        }

        return ParseResult(entries, errors)
    }

    /** Accepts a full instant ("...Z" / with offset) or a bare local date-time, assumed to be this device's zone. */
    private fun parseTimestamp(raw: String): Long? {
        if (raw.isBlank()) return null
        runCatching { return Instant.parse(raw).toEpochMilli() }
        for (format in LOCAL_DATE_TIME_FORMATS) {
            runCatching {
                return LocalDateTime
                    .parse(raw, format)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        }
        return null
    }

    private fun csvRow(vararg fields: String): String = fields.joinToString(",", postfix = "\r\n") { csvField(it) }

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /**
     * A minimal RFC-4180-ish parser: handles quoted fields, escaped `""`
     * quotes, commas/newlines inside quotes, and either CRLF or LF line
     * endings. Good enough for round-tripping [export]'s own output or a
     * template filled in by a normal spreadsheet app — not a general-purpose
     * CSV library.
     */
    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(field.toString())
                        field = StringBuilder()
                    }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(field.toString())
                        rows.add(row)
                        row = mutableListOf()
                        field = StringBuilder()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
