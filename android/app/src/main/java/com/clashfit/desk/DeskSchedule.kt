package com.clashfit.desk

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure computation of the next desk timer fire time.
 * Accounts for quiet hours (which wrap past midnight), snooze windows, and interval.
 * Testable without Android.
 */
object DeskSchedule {

    /**
     * Compute the next fire time for the desk timer.
     *
     * @param now current time in millis
     * @param intervalMin interval in minutes
     * @param quietFromHour hour when quiet begins (0-23)
     * @param quietToHour hour when quiet ends (0-23)
     * @param snoozedUntilMs snooze until time in millis (0 if not snoozed)
     * @return next fire time in millis
     */
    fun nextFireMs(
        now: Long,
        intervalMin: Int,
        quietFromHour: Int,
        quietToHour: Int,
        snoozedUntilMs: Long,
    ): Long {
        val zoneId = ZoneId.systemDefault()
        var candidate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneId)
            .withSecond(0)
            .withNano(0)
            .plusMinutes(intervalMin.toLong())

        // Skip snooze window
        if (snoozedUntilMs > 0 && candidate.toInstant().toEpochMilli() < snoozedUntilMs) {
            candidate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(snoozedUntilMs), zoneId)
                .withSecond(0)
                .withNano(0)
        }

        // Skip quiet hours.
        //
        // Bounded at a day. With quietFrom equal to quietTo every hour matches, and this loop used
        // to step forward a minute at a time and never come back — a hang on whatever thread asked
        // when the next nudge was due. A whole quiet day means there is no answer, so say so by
        // returning the candidate rather than searching for one that cannot exist.
        var guard = 0
        while (isInQuietHours(candidate, quietFromHour, quietToHour)) {
            candidate = candidate.plusMinutes(1)
            if (++guard > MINUTES_IN_A_DAY) return candidate.toInstant().toEpochMilli()
        }

        return candidate.toInstant().toEpochMilli()
    }

    /** One day's worth of minutes: the most the quiet-hours search may ever step forward. */
    private val MINUTES_IN_A_DAY = 24 * 60

    private fun isInQuietHours(zdt: ZonedDateTime, fromHour: Int, toHour: Int): Boolean {
        // Equal bounds mean an empty window, not a full one. Reading it the other way is what let
        // "quiet from 9 to 9" swallow the entire day.
        if (fromHour == toHour) return false
        val hour = zdt.hour
        return if (fromHour < toHour) {
            // Normal case: quiet hours do not wrap (e.g., 14-18)
            hour >= fromHour && hour < toHour
        } else {
            // Wraparound case: quiet hours wrap past midnight (e.g., 18-9)
            hour >= fromHour || hour < toHour
        }
    }
}
