package dev.508.emotiontracker.data

import dev.508.emotiontracker.data.db.EmotionEntryDao
import dev.508.emotiontracker.data.db.EmotionEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A recorded emotion, resolved against the current tree for display. */
data class JournalEntry(
    val id: Long,
    val emotionId: String,
    /** Null when [emotionId] no longer exists in the tree (see EmotionEntryEntity doc). */
    val emotion: EmotionNode?,
    val recordedAtEpochMillis: Long,
    val note: String?,
)

class EmotionRepository(
    val tree: EmotionTree,
    private val dao: EmotionEntryDao,
) {
    private val byId: Map<String, EmotionNode> = tree.flattenById()

    fun resolve(emotionId: String): EmotionNode? = byId[emotionId]

    fun observeJournal(): Flow<List<JournalEntry>> =
        dao.observeAll().map { entries ->
            entries.map { entity ->
                JournalEntry(
                    id = entity.id,
                    emotionId = entity.emotionId,
                    emotion = byId[entity.emotionId],
                    recordedAtEpochMillis = entity.recordedAtEpochMillis,
                    note = entity.note,
                )
            }
        }

    suspend fun recordEmotion(emotionId: String, atEpochMillis: Long = System.currentTimeMillis()) {
        dao.insert(EmotionEntryEntity(emotionId = emotionId, recordedAtEpochMillis = atEpochMillis))
    }

    suspend fun updateNote(entryId: Long, note: String?) {
        dao.updateNote(entryId, note?.ifBlank { null })
    }

    suspend fun deleteAllEntries() {
        dao.deleteAll()
    }
}
