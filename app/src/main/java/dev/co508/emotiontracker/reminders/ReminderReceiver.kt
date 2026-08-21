package dev.co508.emotiontracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.co508.emotiontracker.EmotionTrackerApp
import kotlinx.coroutines.launch

/**
 * Two jobs:
 *
 * - Our own [ACTION_ALARM] broadcasts: post the reminder notification and
 *   re-arm the alarm for the next day. The reminder is re-read from the
 *   database, so a reminder disabled or deleted after scheduling silently
 *   stops instead of being re-armed forever.
 * - System broadcasts that invalidate previously-scheduled alarms
 *   (BOOT_COMPLETED, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED): re-read the
 *   reminders table and re-arm every enabled alarm.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_ALARM -> {
                val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
                if (reminderId >= 0L) {
                    handleAlarm(context, reminderId)
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> rescheduleAll(context)
        }
    }

    private fun handleAlarm(
        context: Context,
        reminderId: Long,
    ) {
        val app = context.applicationContext as EmotionTrackerApp
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                val reminder = app.reminderRepository.getAll().firstOrNull { it.id == reminderId }
                if (reminder != null && reminder.enabled) {
                    app.reminderScheduler.showReminderNotification(reminder)
                    app.reminderScheduler.schedule(reminder)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Re-arm every enabled reminder; broadcasts carry no reminder payload. */
    private fun rescheduleAll(context: Context) {
        val app = context.applicationContext as EmotionTrackerApp
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                app.reminderScheduler.sync(app.reminderRepository.getAll())
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "dev.co508.emotiontracker.action.REMINDER_ALARM"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
