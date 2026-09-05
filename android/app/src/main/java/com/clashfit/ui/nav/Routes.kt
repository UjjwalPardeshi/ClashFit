package com.clashfit.ui.nav

import kotlinx.serialization.Serializable

/** Type-safe destinations. If a route is not here, the screen does not exist. */
sealed interface Route

@Serializable data object Splash : Route

// ── first run: welcome → account → profile → camera ─────────────────────────
@Serializable data object Onboarding : Route
@Serializable data object SignUp : Route
@Serializable data object SignIn : Route
@Serializable data object ResetPassword : Route
@Serializable data object ProfileSetup : Route
@Serializable data object CameraPrimer : Route

// ── the four tab roots ───────────────────────────────────────────────────────
@Serializable data object Home : Route       // Train
@Serializable data object Library : Route
@Serializable data object Progress : Route
@Serializable data object You : Route
@Serializable data object Compete : Route

// ── social and progression ───────────────────────────────────────────────────
@Serializable data object Leaderboard : Route
@Serializable data object Friends : Route
@Serializable data object Achievements : Route
@Serializable data object Account : Route
@Serializable data object Weekly : Route

@Serializable data object Modes : Route
@Serializable data class ExercisePicker(val mode: String) : Route
/** Calibration, fight, rest and victory are phases of one session, not separate screens. */
@Serializable data class Session(
    val mode: String,
    val exerciseId: String,
    val casual: Boolean = false,
    val ghostId: String? = null,
    val durationSec: Int? = null,
    /**
     * Drive this session from a recorded trace instead of the camera.
     *
     * The demo script names trace replay as the escape when the room's lighting beats the
     * pose model, and it was written but wired to nothing. A replayed session is labelled
     * on screen throughout: a judge who is told is fine, a judge who finds out is not.
     */
    val replay: Boolean = false,
) : Route
@Serializable data class Summary(val sessionId: Long) : Route

@Serializable data class DuelLobby(val mode: String, val exerciseId: String) : Route
@Serializable data class RaidRoom(val exerciseId: String) : Route
@Serializable data class Roster(val mode: String, val exerciseId: String) : Route

/** The outdoor chase. Its own route: it takes no exercise, so the picker makes no sense for it. */
@Serializable data object ZombieRun : Route

@Serializable data object RunHome : Route
@Serializable data object RunActive : Route
@Serializable data class RunSummary(val runId: Long) : Route

@Serializable data object Alarms : Route
@Serializable data class AlarmEdit(val alarmId: Long = 0L) : Route

@Serializable data class ExerciseDetail(val exerciseId: String) : Route
@Serializable data object Character : Route
@Serializable data object Streaks : Route
@Serializable data object History : Route
@Serializable data object Clinic : Route
@Serializable data object Ghosts : Route
@Serializable data object Challenge : Route
@Serializable data object Breathing : Route
@Serializable data object Posture : Route
@Serializable data object Desk : Route
@Serializable data object Settings : Route
@Serializable data object Privacy : Route
@Serializable data object BossPreview : Route
@Serializable data object Preflight : Route

// ── help ────────────────────────────────────────────────────────────────────
@Serializable data object About : Route
@Serializable data object Help : Route

/**
 * Talk to the coach. With a session id it opens on that fight's numbers; without one, on the
 * player's recent history.
 */
@Serializable data class CoachChat(val sessionId: Long = -1L) : Route
