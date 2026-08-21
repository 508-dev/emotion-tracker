package dev.co508.emotiontracker.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.co508.emotiontracker.MainActivity
import dev.co508.emotiontracker.R
import dev.co508.emotiontracker.data.Reminder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Owns the two system services reminders touch: AlarmManager (fire at the
 * reminder's time — exactly where permitted) and NotificationManager (post the
 * "how are you feeling?" nudge). Exact alarms need the `SCHEDULE_EXACT_ALARM`
 * special permission on API 31+; where that isn't granted we fall back to
 * [AlarmManager.setAndAllowWhileIdle] and the Reminders screen offers to open
 * the exact-alarm settings page. See DECISIONS.md → "Reminders: Exact Alarms
 * With A Graceful Fallback".
 */
class ReminderScheduler(
    private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun ensureNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminders_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        notificationManager.createNotificationChannel(channel)
    }

    /** Reconciles alarms with the current list: schedule enabled reminders, cancel the rest. */
    fun sync(reminders: List<Reminder>) {
        reminders.forEach { reminder ->
            if (reminder.enabled) {
                schedule(reminder)
            } else {
                cancel(reminder)
            }
        }
    }

    /**
     * Arms a one-shot alarm for the next occurrence of the reminder's time.
     * The receiver re-arms it for the following day when it fires, so exact
     * delivery is preserved day after day.
     */
    fun schedule(reminder: Reminder) {
        val triggerAtMillis = ReminderSchedule.nextTriggerMillis(reminder.hour, reminder.minute)
        val pendingIntent = alarmPendingIntent(reminder)
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(reminder: Reminder) {
        alarmManager.cancel(alarmPendingIntent(reminder))
    }

    /** Posts the daily nudge. No-ops if the user hasn't granted notification permission. */
    fun showReminderNotification(reminder: Reminder) {
        if (!canPostNotifications()) {
            return
        }
        val time =
            LocalTime
                .of(reminder.hour, reminder.minute)
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.reminders_notification_body, time))
                .setContentIntent(openAppPendingIntent(reminder.id))
                .setAutoCancel(true)
                .build()
        notificationManager.notify(reminder.id.toInt(), notification)
    }

    /** True when exact alarms are usable: below API 31, or with SCHEDULE_EXACT_ALARM granted. */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun alarmPendingIntent(reminder: Reminder): PendingIntent {
        val intent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_ALARM
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(reminderId: Long): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_WHEEL
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        return PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "reminders"
    }
}
