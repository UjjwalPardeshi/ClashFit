package com.clashfit.desk

import org.junit.Test
import kotlin.test.assertEquals
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DeskScheduleTest {

    @Test
    fun `next fire time is interval minutes from now`() {
        // Use fixed time outside quiet hours to avoid flakiness
        val zdt = ZonedDateTime.of(2026, 9, 3, 10, 0, 0, 0, ZoneId.systemDefault())
        val now = zdt.toInstant().toEpochMilli()
        val intervalMin = 50
        val quiet = 18
        val quietTo = 9

        val nextFire = DeskSchedule.nextFireMs(now, intervalMin, quiet, quietTo, 0L)
        val expectedMinutes = intervalMin

        val actualMinutes = (nextFire - now) / 1000 / 60
        assertEquals(expectedMinutes.toLong(), actualMinutes, "Next fire should be $intervalMin minutes from now")
    }

    @Test
    fun `skips quiet hours that do not wrap`() {
        // 14:00, quiet 14-18, next should be 18:00 (4 hours later)
        val zdt = ZonedDateTime.of(2026, 9, 3, 14, 0, 0, 0, ZoneId.systemDefault())
        val now = zdt.toInstant().toEpochMilli()
        val intervalMin = 50
        val quiet = 14
        val quietTo = 18

        val nextFire = DeskSchedule.nextFireMs(now, intervalMin, quiet, quietTo, 0L)

        val next = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextFire), ZoneId.systemDefault())
        val expectedHour = 18
        assertEquals(expectedHour, next.hour, "Next fire should be at 18:00")
    }

    @Test
    fun `skips quiet hours that wrap past midnight`() {
        // 20:00, quiet 18-9 (wraps), next should be 09:00 (13 hours later)
        val zdt = ZonedDateTime.of(2026, 9, 3, 20, 0, 0, 0, ZoneId.systemDefault())
        val now = zdt.toInstant().toEpochMilli()
        val intervalMin = 50
        val quiet = 18
        val quietTo = 9

        val nextFire = DeskSchedule.nextFireMs(now, intervalMin, quiet, quietTo, 0L)

        val next = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextFire), ZoneId.systemDefault())
        val expectedHour = 9
        assertEquals(expectedHour, next.hour, "Next fire should be at 09:00")
    }

    @Test
    fun `respects snooze window`() {
        val zdt = ZonedDateTime.of(2026, 9, 3, 10, 0, 0, 0, ZoneId.systemDefault())
        val now = zdt.toInstant().toEpochMilli()
        val snoozedUntilMs = now + 2 * 60 * 60 * 1000 // 2 hours from now

        val nextFire = DeskSchedule.nextFireMs(now, 50, 18, 9, snoozedUntilMs)

        val expectedMinutes = 2 * 60
        val actualMinutes = (nextFire - now) / 1000 / 60
        assertEquals(expectedMinutes.toLong(), actualMinutes, "Next fire should respect 2-hour snooze")
    }

    @Test
    fun `snooze window within quiet hours skips to next available`() {
        // 20:00, quiet 18-9, snoozed until 07:00 (before quiet ends at 09:00)
        val zdt = ZonedDateTime.of(2026, 9, 3, 20, 0, 0, 0, ZoneId.systemDefault())
        val now = zdt.toInstant().toEpochMilli()

        val snoozeZdt = ZonedDateTime.of(2026, 9, 4, 7, 0, 0, 0, ZoneId.systemDefault())
        val snoozedUntilMs = snoozeZdt.toInstant().toEpochMilli()

        val nextFire = DeskSchedule.nextFireMs(now, 50, 18, 9, snoozedUntilMs)

        val next = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextFire), ZoneId.systemDefault())
        assertEquals(9, next.hour, "Next fire should be at 09:00 (end of quiet hours)")
    }

    @Test
    fun `different interval times`() {
        val zdt = ZonedDateTime.of(2026, 9, 3, 10, 0, 0, 0, ZoneId.systemDefault())
        val now = zdt.toInstant().toEpochMilli()
        val quiet = 18
        val quietTo = 9

        val nextFire30 = DeskSchedule.nextFireMs(now, 30, quiet, quietTo, 0L)
        val nextFire60 = DeskSchedule.nextFireMs(now, 60, quiet, quietTo, 0L)

        val minutes30 = (nextFire30 - now) / 1000 / 60
        val minutes60 = (nextFire60 - now) / 1000 / 60

        assertEquals(30L, minutes30)
        assertEquals(60L, minutes60)
    }
}
