package dev.co508.emotiontracker.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {
    private val zone = ZoneId.of("America/Chicago")

    private fun at(dateTime: String): ZonedDateTime = LocalDateTime.parse(dateTime).atZone(zone)

    @Test
    fun `returns today when the time is still ahead`() {
        val now = at("2026-08-21T08:00:00")
        assertEquals(at("2026-08-21T09:30:00"), ReminderSchedule.nextTrigger(now, 9, 30))
    }

    @Test
    fun `rolls to tomorrow when the time already passed`() {
        val now = at("2026-08-21T10:00:00")
        assertEquals(at("2026-08-22T09:30:00"), ReminderSchedule.nextTrigger(now, 9, 30))
    }

    @Test
    fun `rolls to tomorrow when the time is exactly now`() {
        val now = at("2026-08-21T09:30:00")
        assertEquals(at("2026-08-22T09:30:00"), ReminderSchedule.nextTrigger(now, 9, 30))
    }

    @Test
    fun `handles a time just after midnight`() {
        val now = at("2026-08-21T23:59:00")
        assertEquals(at("2026-08-22T00:05:00"), ReminderSchedule.nextTrigger(now, 0, 5))
    }

    @Test
    fun `nextTriggerMillis converts through epoch millis`() {
        val now = at("2026-08-21T08:00:00")
        assertEquals(
            at("2026-08-21T09:30:00").toInstant().toEpochMilli(),
            ReminderSchedule.nextTriggerMillis(9, 30, now.toInstant().toEpochMilli(), zone),
        )
    }
}
