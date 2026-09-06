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
        /**
         * True once somebody has chosen to train without an account.
         *
         * The welcome screen offered two doors and both of them needed the network, so a fresh
         * install with no signal could not get past it at all. This is the third door. It is not
         * an account and it grants nothing: the leaderboard still says sign in, because there is
         * nobody to put on it. Signing in later makes it irrelevant rather than replacing it.
         */
        val guest: Boolean = false,
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
        /**
         * Draw OpenStreetMap tiles under a route.
         *
         * On, and said in words the first time rather than buried in a permission dialog. A map
         * centred on you is a request that carries roughly where you are to a tile server, and
         * this app's whole argument is that it says so when something leaves the phone. The notice
         * is shown once, before the first activity, and turning it off is one tap from there or
         * from Settings at any time. `docs/33-FEATURE-ZOMBIE-RUN.md` § Rules this mode must
         * follow, rule 4.
         *
         * With this off the route still draws — on the app's own grid, with no network at all.
         */
        val mapTiles: Boolean = true,
        /** True once the tile notice has been shown, so it is asked once and not every run. */
        val mapTilesAsked: Boolean = false,
        /** True once the share sheet's "this picture contains your route" notice was accepted. */
        val shareNoticeSeen: Boolean = false,
        /** Weekly distance goal for runs and walks, in metres. */
        val weeklyDistanceGoalM: Int = 15_000,
        /**
         * Show gym weights in pounds rather than kilograms.
         *
         * A display choice only. Every weight is stored in kilograms, so switching this cannot
         * reinterpret a history entered under the other unit — which is the failure a per-row
         * unit would eventually produce.
         */
        val weightUnitLbs: Boolean = false,

        // ── the chase, as the player set it up last time ──────────────────────────────────
        /** True to win Zombie Run by covering ground, false to win it by outlasting a clock. */
        val chaseWinByDistance: Boolean = false,
        /** The distance goal, in kilometres. 1..50. */
        val chaseDistanceKm: Int = 3,
        /** The time goal, in minutes. 1..300, which is five hours. */
        val chaseMinutes: Int = 10,
        /** How fast the pack moves at a steady jog, in km/h. 1..10. */
        val chaseZombieKph: Int = 8,
        /**
         * False until these values have actually come back from disk.
         *
         * Every screen collects this flow with a default instance as its initial value, so for the
         * first frame of every screen the defaults are indistinguishable from the player's real
         * choices. That is harmless for a colour and not harmless for a one-time notice, which
         * flashed on screen every single visit because `mapTilesAsked` reads false in the default
         * before the real `true` arrives a frame later.
         */
        val loaded: Boolean = false,
        /**
         * Read hand gestures from the camera during a fight: open palm pauses, thumb up ends the
         * set, fist skips the rest. On by default because it is the one mid-set input that works
         * in a loud room and needs no permission the fight does not already have.
         */
        val gestures: Boolean = true,
        /**
         * Let the coach use a cloud model when the on-device one is not installed.
         *
         * Off by default, and deliberately not "smart": when it is on, the 23-number summary of a
         * set — and a typed question in the chat — may go to the model provider. Never a camera
         * frame, never a landmark, never a rep timeline; those paths do not exist in the code.
         * The privacy screen states what this switch changes in one sentence.
         */
        val cloudCoach: Boolean = false,
        /**
         * Draw the exo-suit instead of the measured points.
         *
         * The dots are the honest overlay and the default: they are the joints the referee is
         * actually reading, with the angle it scored beside them, so what the phone sees is on
         * screen. The suit is the same landmarks dressed as armour — better on a stage, and it is
         * what the deck and the landing page describe. One switch, because which of those matters
         * depends on who is holding the phone.
         */
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
            guest = p[GUEST] ?: false,
            goal = p[GOAL] ?: Goal.GET_STRONGER.name,
            avatarColor = p[AVATAR_COLOR] ?: 0,
            boss3d = p[BOSS_3D] ?: true,
            mapTiles = p[MAP_TILES] ?: true,
            mapTilesAsked = p[MAP_TILES_ASKED] ?: false,
            loaded = true,
            shareNoticeSeen = p[SHARE_NOTICE_SEEN] ?: false,
            weeklyDistanceGoalM = p[WEEKLY_DISTANCE_GOAL_M] ?: 15_000,
            weightUnitLbs = p[WEIGHT_UNIT_LBS] ?: false,
            chaseWinByDistance = p[CHASE_BY_DISTANCE] ?: false,
            chaseDistanceKm = (p[CHASE_DISTANCE_KM] ?: 3).coerceIn(1, 50),
            chaseMinutes = (p[CHASE_MINUTES] ?: 10).coerceIn(1, 300),
            chaseZombieKph = (p[CHASE_ZOMBIE_KPH] ?: 8).coerceIn(1, 10),
            gestures = p[GESTURES] ?: true,
            cloudCoach = p[CLOUD_COACH] ?: false,
        )
    }

    suspend fun setGuest(v: Boolean) = context.dataStore.edit { it[GUEST] = v }
    suspend fun setGestures(v: Boolean) = context.dataStore.edit { it[GESTURES] = v }
    suspend fun setCloudCoach(v: Boolean) = context.dataStore.edit { it[CLOUD_COACH] = v }
    suspend fun setReduceMotion(v: Boolean) = context.dataStore.edit { it[REDUCE_MOTION] = v }
    suspend fun setBoss3d(v: Boolean) = context.dataStore.edit { it[BOSS_3D] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[HAPTICS] = v }
    suspend fun setVoiceCommands(v: Boolean) = context.dataStore.edit { it[VOICE] = v }
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
    suspend fun setShareNoticeSeen(v: Boolean) = context.dataStore.edit { it[SHARE_NOTICE_SEEN] = v }
    suspend fun setWeeklyDistanceGoalM(v: Int) = context.dataStore.edit { it[WEEKLY_DISTANCE_GOAL_M] = v }

    suspend fun setWeightUnitLbs(v: Boolean) = context.dataStore.edit { it[WEIGHT_UNIT_LBS] = v }

    /**
     * Answers the tile question, and records that it was asked.
     *
     * Both keys move together on purpose: a "no" that did not also record having asked would put
     * the notice back in front of the player on the next run, which is nagging rather than
     * consent. Settings still holds the switch, so "no" is never final.
     */
    suspend fun setMapTiles(v: Boolean) = context.dataStore.edit {
        it[MAP_TILES] = v
        it[MAP_TILES_ASKED] = true
    }

    /**
     * The chase the player just dialled in, kept so the next one opens on it.
     *
     * Clamped on the way in as well as on the way out. A slider cannot produce a value outside its
     * range, but a preferences file edited by hand or carried forward from an older build can, and
     * a zombie at zero km/h is a mode that cannot be lost.
     */
    suspend fun setChase(byDistance: Boolean, distanceKm: Int, minutes: Int, zombieKph: Int) =
        context.dataStore.edit {
            it[CHASE_BY_DISTANCE] = byDistance
            it[CHASE_DISTANCE_KM] = distanceKm.coerceIn(1, 50)
            it[CHASE_MINUTES] = minutes.coerceIn(1, 300)
            it[CHASE_ZOMBIE_KPH] = zombieKph.coerceIn(1, 10)
        }

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
        val ARENA = booleanPreferencesKey("arena_mode")
        val CASUAL = booleanPreferencesKey("casual")
        val DEBUG = booleanPreferencesKey("debug_overlay")
        val PREFERRED_EXERCISE = stringPreferencesKey("preferred_exercise")
        val CHASE_BY_DISTANCE = booleanPreferencesKey("chase_by_distance")
        val CHASE_DISTANCE_KM = intPreferencesKey("chase_distance_km")
        val CHASE_MINUTES = intPreferencesKey("chase_minutes")
        val CHASE_ZOMBIE_KPH = intPreferencesKey("chase_zombie_kph")
        val DESK_ENABLED = booleanPreferencesKey("desk_enabled")
        val DESK_INTERVAL_MIN = intPreferencesKey("desk_interval_min")
        val DESK_EXERCISE_ID = stringPreferencesKey("desk_exercise_id")
        val DESK_REPS = intPreferencesKey("desk_reps")
        val DESK_QUIET_FROM_HOUR = intPreferencesKey("desk_quiet_from_hour")
        val DESK_QUIET_TO_HOUR = intPreferencesKey("desk_quiet_to_hour")
        val DESK_SNOOZED_UNTIL_MS = longPreferencesKey("desk_snoozed_until_ms")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val GUEST = booleanPreferencesKey("guest")
        val GESTURES = booleanPreferencesKey("gestures")
        val CLOUD_COACH = booleanPreferencesKey("cloud_coach")
        val GOAL = stringPreferencesKey("goal")
        val AVATAR_COLOR = intPreferencesKey("avatar_color")
        val MAP_TILES = booleanPreferencesKey("map_tiles")
        val MAP_TILES_ASKED = booleanPreferencesKey("map_tiles_asked")
        val SHARE_NOTICE_SEEN = booleanPreferencesKey("share_notice_seen")
        val WEEKLY_DISTANCE_GOAL_M = intPreferencesKey("weekly_distance_goal_m")
        val WEIGHT_UNIT_LBS = booleanPreferencesKey("weight_unit_lbs")
    }
}
