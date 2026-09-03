package com.clashfit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    )

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
        )
    }

    suspend fun setReduceMotion(v: Boolean) = context.dataStore.edit { it[REDUCE_MOTION] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[HAPTICS] = v }
    suspend fun setVoiceCommands(v: Boolean) = context.dataStore.edit { it[VOICE] = v }
    suspend fun setSpeech(v: Boolean) = context.dataStore.edit { it[SPEECH] = v }
    suspend fun setArenaMode(v: Boolean) = context.dataStore.edit { it[ARENA] = v }
    suspend fun setCasual(v: Boolean) = context.dataStore.edit { it[CASUAL] = v }
    suspend fun setDebugOverlay(v: Boolean) = context.dataStore.edit { it[DEBUG] = v }
    suspend fun setPreferredExercise(v: String) = context.dataStore.edit { it[PREFERRED_EXERCISE] = v }

    private companion object {
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val HAPTICS = booleanPreferencesKey("haptics")
        val VOICE = booleanPreferencesKey("voice_commands")
        val SPEECH = booleanPreferencesKey("speech")
        val ARENA = booleanPreferencesKey("arena_mode")
        val CASUAL = booleanPreferencesKey("casual")
        val DEBUG = booleanPreferencesKey("debug_overlay")
        val PREFERRED_EXERCISE = stringPreferencesKey("preferred_exercise")
    }
}
