package dev.508.emotiontracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmotionEntryDao {
    @Insert
    suspend fun insert(entry: EmotionEntryEntity): Long

    @Query("SELECT * FROM emotion_entries ORDER BY recordedAtEpochMillis DESC")
    fun observeAll(): Flow<List<EmotionEntryEntity>>

    @Query("UPDATE emotion_entries SET note = :note WHERE id = :entryId")
    suspend fun updateNote(entryId: Long, note: String?)

    @Query("DELETE FROM emotion_entries")
    suspend fun deleteAll()
}
