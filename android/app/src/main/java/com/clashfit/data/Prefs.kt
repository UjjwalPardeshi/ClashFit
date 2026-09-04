package com.clashfit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clashfit_prefs")

/** Player preferences. Small, typed, observable. */
class Prefs(private val context: Context) {

    data class Settings(
        val reduceMotion: Boolean = false,
        val haptics: Boolean = true,
        val voiceCommands: Boolean = false,
        val speech: Boolean = true,
        val arenaMode: Boolean = false,
        val casual: Boolean = false,
        val debugOverlay: Boolean = false,
        val preferredExercise: String = "squat",
        val deskEnabled: Boolean = false,
        val deskIntervalMin: Int = 50,
        val deskExerciseId: String = "squat",
        val deskReps: Int = 10,
        val deskQuietFromHour: Int = 18,
        val deskQuietToHour: Int = 9,
        val deskSnoozedUntilMs: Long = 0L,
        /** True once the first-run flow has been completed on this device. */
        val onboarded: Boolean = false,
        /** What the player said they are here for. One of [Goal]. */
        val goal: String = Goal.GET_STRONGER.name,
        /** Index into the avatar palette on the profile. */
        val avatarColor: Int = 0,
        /**
         * Render the boss as the 3D model. Off falls back to the Compose-drawn boss, which is a
         * complete boss with the same six states rather than a placeholder.
         *
         * This exists as a switch a human can reach in five seconds. The 3D renderer is native
         * code running on a device we may be meeting for the first time, and the automatic
         * fallback only catches failures loud enough to throw. If it starts but looks wrong on a
         * venue's projector, nobody wants to be recompiling.
         */
        val boss3d: Boolean = true,
    )

    enum class Goal(val title: String, val blurb: String) {
        GET_STRONGER("Get stronger", "Boss fights, survival, the clean-rep grind."),
        BUILD_HABIT("Build the habit", "Short daily sets and a streak that protects rest days."),
        PLAY_WITH_FRIENDS("Play with friends", "Duels, raids and the leaderboard."),
        MOVE_BETTER("Move better", "Holds, yoga and the clinic tests."),
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            reduceMotion = p[REDUCE_MOTION] ?: false,
            haptics = p[HAPTICS] ?: true,
            voiceCommands = p[VOICE] ?: false,
            speech = p[SPEECH] ?: true,
            arenaMode = p[ARENA] ?: false,
            casual = p[CASUAL] ?: false,
            debugOverlay = p[DEBUG] ?: false,
            preferredExercise = p[PREFERRED_EXERCISE] ?: "squat",
            deskEnabled = p[DESK_ENABLED] ?: false,
            deskIntervalMin = p[DESK_INTERVAL_MIN] ?: 50,
            deskExerciseId = p[DESK_EXERCISE_ID] ?: "squat",
            deskReps = p[DESK_REPS] ?: 10,
            deskQuietFromHour = p[DESK_QUIET_FROM_HOUR] ?: 18,
            deskQuietToHour = p[DESK_QUIET_TO_HOUR] ?: 9,
            deskSnoozedUntilMs = p[DESK_SNOOZED_UNTIL_MS] ?: 0L,
            onboarded = p[ONBOARDED] ?: false,
            goal = p[GOAL] ?: Goal.GET_STRONGER.name,
            avatarColor = p[AVATAR_COLOR] ?: 0,
            boss3d = p[BOSS_3D] ?: true,
        )
    }

    suspend fun setReduceMotion(v: Boolean) = context.dataStore.edit { it[REDUCE_MOTION] = v }
    suspend fun setBoss3d(v: Boolean) = context.dataStore.edit { it[BOSS_3D] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[HAPTICS] = v }
    suspend fun setVoiceCommands(v: Boolean) = context.dataStore.edit { it[VOICE] = v }
    suspend fun setSpeech(v: Boolean) = context.dataStore.edit { it[SPEECH] = v }
    suspend fun setArenaMode(v: Boolean) = context.dataStore.edit { it[ARENA] = v }
    suspend fun setCasual(v: Boolean) = context.dataStore.edit { it[CASUAL] = v }
    suspend fun setDebugOverlay(v: Boolean) = context.dataStore.edit { it[DEBUG] = v }
    suspend fun setPreferredExercise(v: String) = context.dataStore.edit { it[PREFERRED_EXERCISE] = v }
    suspend fun setDeskEnabled(v: Boolean) = context.dataStore.edit { it[DESK_ENABLED] = v }
    suspend fun setDeskIntervalMin(v: Int) = context.dataStore.edit { it[DESK_INTERVAL_MIN] = v }
    suspend fun setDeskExerciseId(v: String) = context.dataStore.edit { it[DESK_EXERCISE_ID] = v }
    suspend fun setDeskReps(v: Int) = context.dataStore.edit { it[DESK_REPS] = v }
    suspend fun setDeskQuietFromHour(v: Int) = context.dataStore.edit { it[DESK_QUIET_FROM_HOUR] = v }
    suspend fun setDeskQuietToHour(v: Int) = context.dataStore.edit { it[DESK_QUIET_TO_HOUR] = v }
    suspend fun setDeskSnoozedUntilMs(v: Long) = context.dataStore.edit { it[DESK_SNOOZED_UNTIL_MS] = v }
    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[ONBOARDED] = v }
    suspend fun setGoal(v: Goal) = context.dataStore.edit { it[GOAL] = v.name }
    suspend fun setAvatarColor(v: Int) = context.dataStore.edit { it[AVATAR_COLOR] = v }

    /** Everything the first-run flow decides, in one write. */
    suspend fun completeOnboarding(goal: Goal, preferredExercise: String, avatarColor: Int) = context.dataStore.edit {
        it[GOAL] = goal.name
        it[PREFERRED_EXERCISE] = preferredExercise
        it[AVATAR_COLOR] = avatarColor
        it[ONBOARDED] = true
    }

    private companion object {
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val BOSS_3D = booleanPreferencesKey("boss_3d")
        val HAPTICS = booleanPreferencesKey("haptics")
        val VOICE = booleanPreferencesKey("voice_commands")
        val SPEECH = booleanPreferencesKey("speech")
        val ARENA = booleanPreferencesKey("arena_mode")
        val CASUAL = booleanPreferencesKey("casual")
        val DEBUG = booleanPreferencesKey("debug_overlay")
        val PREFERRED_EXERCISE = stringPreferencesKey("preferred_exercise")
        val DESK_ENABLED = booleanPreferencesKey("desk_enabled")
        val DESK_INTERVAL_MIN = intPreferencesKey("desk_interval_min")
        val DESK_EXERCISE_ID = stringPreferencesKey("desk_exercise_id")
        val DESK_REPS = intPreferencesKey("desk_reps")
        val DESK_QUIET_FROM_HOUR = intPreferencesKey("desk_quiet_from_hour")
        val DESK_QUIET_TO_HOUR = intPreferencesKey("desk_quiet_to_hour")
        val DESK_SNOOZED_UNTIL_MS = longPreferencesKey("desk_snoozed_until_ms")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val GOAL = stringPreferencesKey("goal")
        val AVATAR_COLOR = intPreferencesKey("avatar_color")
    }
}
