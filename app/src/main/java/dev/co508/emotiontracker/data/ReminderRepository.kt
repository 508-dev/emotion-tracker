package dev.co508.emotiontracker.data

import dev.co508.emotiontracker.data.db.ReminderDao
import dev.co508.emotiontracker.data.db.ReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A daily reminder to record an emotion: a time of day plus an on/off switch. */
data class Reminder(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
)

class ReminderRepository(
    private val dao: ReminderDao,
) {
    fun observeReminders(): Flow<List<Reminder>> =
        dao.observeAll().map { entities ->
            entities.map { it.toReminder() }
        }

    /** One-shot read for the boot/timezone rescheduling receiver. */
    suspend fun getAll(): List<Reminder> = dao.getAll().map { it.toReminder() }

    suspend fun addReminder(
        hour: Int,
        minute: Int,
        enabled: Boolean = true,
    ): Long = dao.insert(ReminderEntity(hour = hour, minute = minute, enabled = enabled))

    suspend fun updateReminderTime(
        id: Long,
        hour: Int,
        minute: Int,
    ) {
        dao.updateTime(id, hour, minute)
    }

    suspend fun setEnabled(
        id: Long,
        enabled: Boolean,
    ) {
        dao.updateEnabled(id, enabled)
    }

    suspend fun deleteReminder(id: Long) {
        dao.deleteById(id)
    }

    private fun ReminderEntity.toReminder() =
        Reminder(
            id = id,
            hour = hour,
            minute = minute,
            enabled = enabled,
        )
}
