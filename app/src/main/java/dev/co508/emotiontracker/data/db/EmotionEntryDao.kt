package dev.co508.emotiontracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmotionEntryDao {
    @Insert
    suspend fun insert(entry: EmotionEntryEntity): Long

    /** Bulk insert for CSV restore — one statement rather than one round trip per row. */
    @Insert
    suspend fun insertAll(entries: List<EmotionEntryEntity>)

    @Query("SELECT * FROM emotion_entries ORDER BY recordedAtEpochMillis DESC")
    fun observeAll(): Flow<List<EmotionEntryEntity>>

    @Query("UPDATE emotion_entries SET note = :note WHERE id = :entryId")
    suspend fun updateNote(
        entryId: Long,
        note: String?,
    )

    @Query("DELETE FROM emotion_entries")
    suspend fun deleteAll()
}
