package dev.co508.emotiontracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A daily reminder to record an emotion. Only a time of day is stored for now —
 * day-of-week filtering is deliberately out of scope until asked for.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
)
