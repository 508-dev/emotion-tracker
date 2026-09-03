package dev.co508.emotiontracker.data

import dev.co508.emotiontracker.data.db.EmotionEntryDao
import dev.co508.emotiontracker.data.db.EmotionEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val testTree =
    EmotionTree(
        version = 1,
        root =
            EmotionNode(
                id = "root",
                label = "Root",
                color = "#000000",
                children =
                    listOf(
                        EmotionNode(id = "positive", label = "Positive", color = "#2D6CDF", children = emptyList()),
                    ),
            ),
    )

/** In-memory stand-in for [EmotionEntryDao] — no Room/Android context required. */
private class FakeEmotionEntryDao : EmotionEntryDao {
    private val state = MutableStateFlow<List<EmotionEntryEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(entry: EmotionEntryEntity): Long {
        val withId = entry.copy(id = nextId++)
        state.value = listOf(withId) + state.value
        return withId.id
    }

    override suspend fun insertAll(entries: List<EmotionEntryEntity>) {
        val withIds = entries.map { it.copy(id = nextId++) }
        state.value = withIds.reversed() + state.value
    }

    override fun observeAll(): Flow<List<EmotionEntryEntity>> = state.asStateFlow()

    override suspend fun updateNote(
        entryId: Long,
        note: String?,
    ) {
        state.value = state.value.map { if (it.id == entryId) it.copy(note = note) else it }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}

class EmotionRepositoryTest {
    @Test
    fun `resolve returns the matching node`() {
        val repository = EmotionRepository(testTree, FakeEmotionEntryDao())
        assertEquals("Positive", repository.resolve("positive")?.label)
    }

    @Test
    fun `resolve returns null for an id no longer in the tree`() {
        val repository = EmotionRepository(testTree, FakeEmotionEntryDao())
        assertNull(repository.resolve("retired-emotion-id"))
    }

    @Test
    fun `recordEmotion surfaces as a journal entry resolved against the tree`() =
        runTest {
            val repository = EmotionRepository(testTree, FakeEmotionEntryDao())
            repository.recordEmotion("positive", atEpochMillis = 1_000L)

            val entries = repository.observeJournal().first()

            assertEquals(1, entries.size)
            assertEquals("positive", entries[0].emotionId)
            assertEquals("Positive", entries[0].emotion?.label)
            assertEquals(1_000L, entries[0].recordedAtEpochMillis)
        }

    @Test
    fun `an entry for a retired emotion id resolves to a null emotion, not a crash`() =
        runTest {
            val dao = FakeEmotionEntryDao()
            val repository = EmotionRepository(testTree, dao)
            dao.insert(EmotionEntryEntity(emotionId = "retired-emotion-id", recordedAtEpochMillis = 0L))

            val entries = repository.observeJournal().first()

            assertEquals("retired-emotion-id", entries[0].emotionId)
            assertNull(entries[0].emotion)
        }

    @Test
    fun `deleteAllEntries clears the journal`() =
        runTest {
            val repository = EmotionRepository(testTree, FakeEmotionEntryDao())
            repository.recordEmotion("positive")

            repository.deleteAllEntries()

            assertTrue(repository.observeJournal().first().isEmpty())
        }

    @Test
    fun `restoreEntries adds entries without touching existing ones`() =
        runTest {
            val dao = FakeEmotionEntryDao()
            val repository = EmotionRepository(testTree, dao)
            repository.recordEmotion("positive", atEpochMillis = 1_000L)

            repository.restoreEntries(
                listOf(
                    EmotionEntryEntity(emotionId = "positive", recordedAtEpochMillis = 2_000L, note = "restored"),
                ),
            )

            val entries = repository.observeJournal().first()
            assertEquals(2, entries.size)
            assertTrue(entries.any { it.recordedAtEpochMillis == 1_000L })
            assertTrue(entries.any { it.recordedAtEpochMillis == 2_000L && it.note == "restored" })
        }

    @Test
    fun `updateNote blanks out to null`() =
        runTest {
            val dao = FakeEmotionEntryDao()
            val repository = EmotionRepository(testTree, dao)
            val id = dao.insert(EmotionEntryEntity(emotionId = "positive", recordedAtEpochMillis = 0L))

            repository.updateNote(id, "   ")

            assertNull(
                repository
                    .observeJournal()
                    .first()
                    .first { it.id == id }
                    .note,
            )
        }
}
