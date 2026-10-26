package io.github.nottarun7.fitt.reminders

import java.time.Instant
import java.time.ZoneId

/** Resolve the next local daily time anew, rather than adding 24 hours across DST. */
fun nextReminderInstant(now: Instant, minute: Int, zone: ZoneId): Instant {
    require(minute in 0..1439)
    val date = now.atZone(zone).toLocalDate()
    fun onDay(days: Long) = date.plusDays(days).atTime(minute / 60, minute % 60).atZone(zone).toInstant()
    val today = onDay(0)
    return if (today > now) today else onDay(1)
}
