package com.clashfit.engine.summary

/**
 * Clinic mode scorer for the 30-second sit-to-stand protocol.
 * Shows raw count + personal trend, no norm comparison (norms are not yet shipped/cited).
 * docs/25-CLINIC-MODE.md
 */

data class ClinicResult(
    val protocol: String,
    val rawCount: Int,
    val trend: List<ClinicTrendPoint> = emptyList(),
    val hasNormComparison: Boolean = false,
)

data class ClinicTrendPoint(
    val at: Long, // timestamp ms
    val count: Int,
)

/**
 * Scorer for a clinic protocol. Currently focused on the 30-second sit-to-stand.
 * The raw count is the only defensible metric until norms are properly sourced and cited.
 */
class ClinicScorer(val protocol: String = "sit_to_stand_30s") {
    fun score(count: Int, history: List<ClinicTrendPoint> = emptyList()): ClinicResult {
        return ClinicResult(
            protocol = protocol,
            rawCount = count,
            trend = history,
            hasNormComparison = false, // Never show norms until they are properly sourced
        )
    }

    /** Extract trend from a session history. Oldest first. */
    fun trend(sessions: List<SessionRecord>, limit: Int = 20): List<ClinicTrendPoint> {
        return sessions
            .filter { it.protocol == protocol }
            .takeLast(limit)
            .map { ClinicTrendPoint(it.endedAt, it.repCount) }
    }

    data class SessionRecord(
        val endedAt: Long,
        val protocol: String,
        val repCount: Int,
    )
}
