package io.github.nottarun7.fitt

import io.github.nottarun7.fitt.reminders.nextReminderInstant
import io.github.nottarun7.fitt.ui.parseMinute
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderTimeTest {
    @Test fun timeInputIsStrictAndCoversTheWholeDay() {
        assertEquals(0, parseMinute("00:00")); assertEquals(1439, parseMinute("23:59"))
        listOf("24:00", "12:60", "8:30", "", "ab:cd", "12:30:00").forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { parseMinute(text) }
        }
    }
    @Test fun sameDayAndNextDayRespectTheLocalTimeZone() {
        val zone = ZoneId.of("Asia/Kolkata")
        val before = Instant.parse("2026-10-25T13:00:00Z")
        assertEquals(Instant.parse("2026-10-25T14:30:00Z"), nextReminderInstant(before, 1200, zone))
        assertEquals(Instant.parse("2026-10-26T14:30:00Z"), nextReminderInstant(Instant.parse("2026-10-25T14:30:00Z"), 1200, zone))
    }
    @Test fun dailyTimeSurvivesShortAndLongDaylightSavingDays() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-03-08T12:00:00Z"), nextReminderInstant(Instant.parse("2026-03-07T13:00:00Z"), 480, zone))
        assertEquals(Instant.parse("2026-11-01T13:00:00Z"), nextReminderInstant(Instant.parse("2026-10-31T12:00:00Z"), 480, zone))
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), nextReminderInstant(Instant.parse("2026-03-08T00:00:00Z"), 150, zone))
    }
}
