package com.clashfit.run

import com.clashfit.data.RunEntity
import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit tests for RunSummary. */
class RunSummaryTest {
    @Test
    fun formatPace_zero() {
        assertEquals("0:00", RunSummary.formatPace(0f))
    }

    @Test
    fun formatPace_seconds() {
        assertEquals("0:30", RunSummary.formatPace(30f))
    }

    @Test
    fun formatPace_minutes() {
        assertEquals("4:00", RunSummary.formatPace(240f))
    }

    @Test
    fun formatPace_minutesSeconds() {
        assertEquals("5:30", RunSummary.formatPace(330f))
    }

    @Test
    fun fromEntity() {
        val entity = RunEntity(
            id = 1L,
            startedAtMs = 1000L,
            endedAtMs = 61_000L,
            distanceM = 1000f,
            movingMs = 60_000L,
            avgPaceSecPerKm = 60f,
            cadenceSpm = 170,
            elevationGainM = 10f,
            splitsJson = "60,60,60",
        )

        val summary = RunSummary(entity)
        assertEquals(1L, summary.id)
        assertEquals(1000f, summary.distanceM)
        assertEquals("1:00", summary.paceStr)
    }

    @Test
    fun durationMs_fromStartToEnd() {
        val start = 1000L
        val end = 61_000L
        val summary = RunSummary(
            id = 1L,
            startedAtMs = start,
            endedAtMs = end,
            distanceM = 0f,
            movingMs = 0L,
            avgPaceSecPerKm = 0f,
            cadenceSpm = 0,
            elevationGainM = 0f,
        )

        assertEquals(60_000L, summary.durationMs)
    }
}
