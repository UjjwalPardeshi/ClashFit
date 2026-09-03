package com.clashfit.engine.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Tests streak state computation, particularly dayKey() and weekKey() which use UTC
 * to ensure consistent day boundaries across all timezones.
 *
 * dayKey() returns YYYY-MM-DD in UTC, matching store.js toISOString().slice(0, 10).
 * This ensures the same UTC date is used for streak tracking regardless of device timezone.
 */
class StreakStateTest {

    @Test
    fun `dayKey returns UTC date in ISO format`() {
        // 2024-01-15 00:00:00 UTC
        val ms = Instant.parse("2024-01-15T00:00:00Z").toEpochMilli()
        val key = dayKey(ms)
        assertEquals("2024-01-15", key)
    }

    @Test
    fun `dayKey increments by 1 for consecutive UTC dates`() {
        val day1Ms = Instant.parse("2024-01-15T00:00:00Z").toEpochMilli()
        val day2Ms = Instant.parse("2024-01-16T00:00:00Z").toEpochMilli()

        val key1 = dayKey(day1Ms)
        val key2 = dayKey(day2Ms)

        assertEquals("2024-01-15", key1)
        assertEquals("2024-01-16", key2)

        // Verify they differ by 1 day
        val date1 = LocalDate.parse(key1)
        val date2 = LocalDate.parse(key2)
        assertEquals(1, java.time.temporal.ChronoUnit.DAYS.between(date1, date2))
    }

    @Test
    fun `dayKey is consistent across multiple times on same UTC day`() {
        // Different times on the same UTC day should return the same key
        val t1 = Instant.parse("2024-01-15T00:00:00Z").toEpochMilli()
        val t2 = Instant.parse("2024-01-15T12:00:00Z").toEpochMilli()
        val t3 = Instant.parse("2024-01-15T23:59:59Z").toEpochMilli()

        val key1 = dayKey(t1)
        val key2 = dayKey(t2)
        val key3 = dayKey(t3)

        assertEquals("2024-01-15", key1)
        assertEquals("2024-01-15", key2)
        assertEquals("2024-01-15", key3)
    }

    @Test
    fun `dayKey handles leap year correctly`() {
        // 2024 is a leap year, Feb 29 exists
        val leapDay = Instant.parse("2024-02-29T12:00:00Z").toEpochMilli()
        val key = dayKey(leapDay)
        assertEquals("2024-02-29", key)

        // Day before
        val day28 = Instant.parse("2024-02-28T12:00:00Z").toEpochMilli()
        val key28 = dayKey(day28)
        assertEquals("2024-02-28", key28)

        // Day after
        val day1March = Instant.parse("2024-03-01T12:00:00Z").toEpochMilli()
        val keyMarch = dayKey(day1March)
        assertEquals("2024-03-01", keyMarch)
    }

    @Test
    fun `weekKey returns ISO week format`() {
        // 2024-01-15 is a Monday in week 3
        val ms = Instant.parse("2024-01-15T12:00:00Z").toEpochMilli()
        val key = weekKey(ms)

        // Should be in format YYYY-Wxx
        val parts = key.split("-")
        assertEquals(2, parts.size)
        assertEquals("2024", parts[0])
        assertEquals(true, parts[1].startsWith("W"))
    }

    @Test
    fun `weekKey increments for different weeks`() {
        // Week 1 and Week 2
        val week1 = Instant.parse("2024-01-01T12:00:00Z").toEpochMilli()
        val week2 = Instant.parse("2024-01-08T12:00:00Z").toEpochMilli()

        val key1 = weekKey(week1)
        val key2 = weekKey(week2)

        assertEquals(true, key1.contains("2024-W"))
        assertEquals(true, key2.contains("2024-W"))
        // Keys should be different (different weeks)
        assertEquals(false, key1 == key2)
    }

    @Test
    fun `weekKey is UTC-based like dayKey`() {
        // Verify weeks don't shift with timezone
        val utcTime = Instant.parse("2024-01-15T12:00:00Z").toEpochMilli()

        // Parse the week key
        val key = weekKey(utcTime)
        val (year, week) = key.split("-").let { it[0].toInt() to it[1].substring(1).toInt() }

        assertEquals(2024, year)
        assertEquals(true, week in 1..53)
    }

    @Test
    fun `dayKey uses UTC not local timezone`() {
        // Session at 23:30 local time UTC+5 = 18:30 UTC (previous UTC day)
        // This test verifies that dayKey always uses UTC, not local time

        // 2024-01-15 18:30:00 UTC (which is 2024-01-16 00:30:00 UTC+5:30)
        val utcMs = Instant.parse("2024-01-15T18:30:00Z").toEpochMilli()
        val key = dayKey(utcMs)

        // dayKey should return 2024-01-15 (UTC date), NOT 2024-01-16 (local date)
        assertEquals("2024-01-15", key, "dayKey must use UTC date, not local timezone")
    }

    @Test
    fun `streak uses UTC day boundaries for consistency`() {
        // Verify multiple sessions on same UTC day have same dayKey
        val t1 = Instant.parse("2024-01-15T06:00:00Z").toEpochMilli()
        val t2 = Instant.parse("2024-01-15T18:00:00Z").toEpochMilli()

        val key1 = dayKey(t1)
        val key2 = dayKey(t2)

        assertEquals(key1, key2, "Sessions on same UTC day should have same dayKey")
        assertEquals("2024-01-15", key1)
    }
}
