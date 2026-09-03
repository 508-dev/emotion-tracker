package dev.co508.emotiontracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val tree =
    EmotionTree(
        version = 1,
        root =
            EmotionNode(
                id = "root",
                label = "How are you feeling?",
                color = "#000000",
                children =
                    listOf(
                        EmotionNode(
                            id = "positive",
                            label = "Positive",
                            color = "#000000",
                            children =
                                listOf(
                                    EmotionNode(
                                        id = "positive.calm",
                                        label = "Calm",
                                        color = "#000000",
                                        children =
                                            listOf(
                                                EmotionNode(
                                                    id = "positive.calm.content",
                                                    label = "Content",
                                                    color = "#000000",
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            ),
    )

class EmotionCsvTest {
    @Test
    fun `template is just the header`() {
        assertEquals("recordedAt,emotion,emotionId,note\r\n", EmotionCsv.template())
    }

    @Test
    fun `export writes the label path alongside the id`() {
        val entries =
            listOf(
                JournalEntry(
                    id = 1,
                    emotionId = "positive.calm.content",
                    emotion = tree.flattenById()["positive.calm.content"],
                    recordedAtEpochMillis = 0L,
                    note = null,
                ),
            )

        val csv = EmotionCsv.export(entries, tree)

        assertTrue(csv.contains("Positive > Calm > Content"))
        assertTrue(csv.contains("positive.calm.content"))
    }

    @Test
    fun `a note with a comma and a quote round-trips`() {
        val entries =
            listOf(
                JournalEntry(
                    id = 1,
                    emotionId = "positive.calm.content",
                    emotion = tree.flattenById()["positive.calm.content"],
                    recordedAtEpochMillis = 1_700_000_000_000L,
                    note = "commas, and \"quotes\" too",
                ),
            )

        val csv = EmotionCsv.export(entries, tree)
        val result = EmotionCsv.parse(csv, tree)

        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.entries.size)
        assertEquals("commas, and \"quotes\" too", result.entries[0].note)
        assertEquals(1_700_000_000_000L, result.entries[0].recordedAtEpochMillis)
        assertEquals("positive.calm.content", result.entries[0].emotionId)
    }

    @Test
    fun `restoring by label path alone (no emotionId) resolves against the tree`() {
        val csv = "recordedAt,emotion,emotionId,note\n2026-01-01T00:00:00Z,Positive > Calm > Content,,\n"

        val result = EmotionCsv.parse(csv, tree)

        assertEquals(1, result.entries.size)
        assertEquals("positive.calm.content", result.entries[0].emotionId)
    }

    @Test
    fun `a bare local date-time is interpreted in the system zone`() {
        val csv = "recordedAt,emotion,emotionId,note\n2026-01-01T09:30,Positive > Calm > Content,,\n"

        val result = EmotionCsv.parse(csv, tree)

        assertEquals(1, result.entries.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `an unrecognized emotion is skipped with a reason, not a crash`() {
        val csv = "recordedAt,emotion,emotionId,note\n2026-01-01T00:00:00Z,Nonexistent > Path,,\n"

        val result = EmotionCsv.parse(csv, tree)

        assertTrue(result.entries.isEmpty())
        assertEquals(1, result.errors.size)
        assertEquals(2, result.errors[0].lineNumber)
    }

    @Test
    fun `an unreadable date is skipped with a reason, not a crash`() {
        val csv = "recordedAt,emotion,emotionId,note\nnot a date,Positive > Calm > Content,,\n"

        val result = EmotionCsv.parse(csv, tree)

        assertTrue(result.entries.isEmpty())
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `blank rows are ignored rather than reported as errors`() {
        val csv = "recordedAt,emotion,emotionId,note\n,,,\n2026-01-01T00:00:00Z,Positive > Calm > Content,,\n"

        val result = EmotionCsv.parse(csv, tree)

        assertEquals(1, result.entries.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `a leading byte-order mark on the header does not break parsing`() {
        val csv = "\uFEFFrecordedAt,emotion,emotionId,note\n2026-01-01T00:00:00Z,Positive > Calm > Content,,\n"

        val result = EmotionCsv.parse(csv, tree)

        assertEquals(1, result.entries.size)
    }

    @Test
    fun `an id that no longer exists in the tree falls back to the raw id in export`() {
        val entries =
            listOf(
                JournalEntry(
                    id = 1,
                    emotionId = "retired-id",
                    emotion = null,
                    recordedAtEpochMillis = 0L,
                    note = null,
                ),
            )

        val csv = EmotionCsv.export(entries, tree)

        assertTrue(csv.contains("retired-id"))
    }

    @Test
    fun `label path resolution is case-insensitive`() {
        assertEquals("positive.calm.content", tree.resolveLabelPath("positive > calm > content"))
    }

    @Test
    fun `label path resolution fails on a typo`() {
        assertNull(tree.resolveLabelPath("Positive > Calm > Contnet"))
    }
}
