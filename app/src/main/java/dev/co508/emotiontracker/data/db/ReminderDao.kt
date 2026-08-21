package dev.co508.emotiontracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    fun observeAll(): Flow<List<ReminderEntity>>

    /** One-shot read for rescheduling after boot/timezone changes. */
    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    suspend fun getAll(): List<ReminderEntity>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET hour = :hour, minute = :minute WHERE id = :id")
    suspend fun updateTime(
        id: Long,
        hour: Int,
        minute: Int,
    )

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(
        id: Long,
        enabled: Boolean,
    )

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
