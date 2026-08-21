package dev.co508.emotiontracker.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.co508.emotiontracker.data.Reminder
import dev.co508.emotiontracker.data.ReminderRepository
import dev.co508.emotiontracker.reminders.ReminderScheduler
import dev.co508.emotiontracker.ui.reminderRepository
import dev.co508.emotiontracker.ui.reminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemindersViewModel(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val reminders: StateFlow<List<Reminder>> =
        repository
            .observeReminders()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Keep AlarmManager in lockstep with the table: every change to the
        // list re-arms enabled reminders and cancels disabled/deleted ones.
        // This view model lives for the screen's back stack entry, which is
        // fine — every mutation happens from this screen.
        viewModelScope.launch {
            reminders.collect { scheduler.sync(it) }
        }
    }

    fun addReminder(
        hour: Int,
        minute: Int,
    ) {
        viewModelScope.launch { repository.addReminder(hour, minute) }
    }

    fun updateReminderTime(
        id: Long,
        hour: Int,
        minute: Int,
    ) {
        viewModelScope.launch { repository.updateReminderTime(id, hour, minute) }
    }

    fun setEnabled(
        id: Long,
        enabled: Boolean,
    ) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { repository.deleteReminder(id) }
    }

    /** Re-arms every enabled reminder — used after the user grants exact-alarm access in settings. */
    fun resync() {
        viewModelScope.launch { scheduler.sync(reminders.value) }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer { RemindersViewModel(reminderRepository(), reminderScheduler()) }
            }
    }
}
