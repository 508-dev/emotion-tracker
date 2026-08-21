package dev.co508.emotiontracker.reminders

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure time-of-day scheduling math, kept free of Android types so it's JVM-testable. */
object ReminderSchedule {
    /**
     * The next occurrence of [hour]:[minute] in [now]'s zone that is strictly
     * after [now] — today if it hasn't passed yet, otherwise tomorrow.
     */
    fun nextTrigger(
        now: ZonedDateTime,
        hour: Int,
        minute: Int,
    ): ZonedDateTime {
        var next = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return next
    }

    fun nextTriggerMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long =
        nextTrigger(Instant.ofEpochMilli(nowMillis).atZone(zone), hour, minute)
            .toInstant()
            .toEpochMilli()
}
