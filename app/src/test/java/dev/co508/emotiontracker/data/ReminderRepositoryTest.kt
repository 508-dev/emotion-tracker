package dev.co508.emotiontracker.data

import dev.co508.emotiontracker.data.db.ReminderDao
import dev.co508.emotiontracker.data.db.ReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory stand-in for [ReminderDao] — no Room/Android context required. */
private class FakeReminderDao : ReminderDao {
    private val state = MutableStateFlow<List<ReminderEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ReminderEntity>> = state.asStateFlow()

    override suspend fun getAll(): List<ReminderEntity> = state.value

    override suspend fun insert(reminder: ReminderEntity): Long {
        val withId = reminder.copy(id = nextId++)
        state.value = state.value + withId
        return withId.id
    }

    override suspend fun updateTime(
        id: Long,
        hour: Int,
        minute: Int,
    ) {
        state.value = state.value.map { if (it.id == id) it.copy(hour = hour, minute = minute) else it }
    }

    override suspend fun updateEnabled(
        id: Long,
        enabled: Boolean,
    ) {
        state.value = state.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    override suspend fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class ReminderRepositoryTest {
    @Test
    fun `addReminder surfaces as an observed reminder, enabled by default`() =
        runTest {
            val repository = ReminderRepository(FakeReminderDao())

            repository.addReminder(hour = 9, minute = 30)

            val reminders = repository.observeReminders().first()
            assertEquals(1, reminders.size)
            assertEquals(9, reminders[0].hour)
            assertEquals(30, reminders[0].minute)
            assertTrue(reminders[0].enabled)
        }

    @Test
    fun `updateReminderTime changes the stored time`() =
        runTest {
            val dao = FakeReminderDao()
            val repository = ReminderRepository(dao)
            val id = dao.insert(ReminderEntity(hour = 9, minute = 30))

            repository.updateReminderTime(id, 18, 45)

            val reminder = repository.observeReminders().first().first { it.id == id }
            assertEquals(18, reminder.hour)
            assertEquals(45, reminder.minute)
        }

    @Test
    fun `setEnabled toggles the reminder off`() =
        runTest {
            val dao = FakeReminderDao()
            val repository = ReminderRepository(dao)
            val id = dao.insert(ReminderEntity(hour = 9, minute = 30))

            repository.setEnabled(id, false)

            val reminder = repository.observeReminders().first().first { it.id == id }
            assertFalse(reminder.enabled)
        }

    @Test
    fun `deleteReminder removes it from the list`() =
        runTest {
            val dao = FakeReminderDao()
            val repository = ReminderRepository(dao)
            val id = dao.insert(ReminderEntity(hour = 9, minute = 30))

            repository.deleteReminder(id)

            assertTrue(repository.observeReminders().first().isEmpty())
        }

    @Test
    fun `getAll returns everything for boot rescheduling`() =
        runTest {
            val dao = FakeReminderDao()
            val repository = ReminderRepository(dao)
            dao.insert(ReminderEntity(hour = 9, minute = 30))
            dao.insert(ReminderEntity(hour = 21, minute = 0, enabled = false))

            assertEquals(2, repository.getAll().size)
        }
}
