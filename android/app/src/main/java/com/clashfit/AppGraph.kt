package com.clashfit

import android.content.Context
import androidx.room.Room
import com.clashfit.audio.Haptics
import com.clashfit.audio.Sfx
import com.clashfit.coach.CoachConfig
import com.clashfit.coach.CoachEngine
import com.clashfit.coach.LlmEngine
import com.clashfit.coach.SpeechOut
import com.clashfit.core.config.ConfigStore
import com.clashfit.core.util.Clock
import com.clashfit.data.ClashDb
import com.clashfit.data.Prefs
import com.clashfit.play.PlayHub
import com.clashfit.voice.VoiceCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/**
 * Manual dependency graph: one object, constructed once, every collaborator built by hand.
 * No DI framework by decision — construction is explicit, fast to compile, and testable.
 * docs/01-TRD.md §10
 */
class AppGraph(val app: Context) {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** For speech and other main-thread-only clients. */
    val mainScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val clock: Clock = Clock.SYSTEM

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    val config: ConfigStore by lazy { ConfigStore(app, json, scope) }

    val db: ClashDb by lazy {
        Room.databaseBuilder(app, ClashDb::class.java, "clashfit.db")
            .addMigrations(com.clashfit.data.MIGRATION_1_2, com.clashfit.data.MIGRATION_2_3)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
    }

    val prefs: Prefs by lazy { Prefs(app) }

    // The coach and the villain are the same model; the template bank always ships beneath it.
    val llmEngine: LlmEngine by lazy { LlmEngine(app, CoachConfig(), clock, scope, json) }
    val coachEngine: CoachEngine by lazy { CoachEngine(llmEngine) }
    val speechOut: SpeechOut by lazy { SpeechOut(app, mainScope) }

    // Audio fires first. Everything is synthesised; there are no sound assets.
    val sfx: Sfx by lazy { Sfx() }
    val haptics: Haptics by lazy { Haptics(app, prefs) }
    val voiceCommands: VoiceCommands by lazy { VoiceCommands(app, mainScope) }

    // Multiplayer links and pass-the-phone rosters outlive any one screen.
    val playHub: PlayHub by lazy { PlayHub(app, json, clock, scope) }

    companion object {
        fun of(context: Context): AppGraph = (context.applicationContext as ClashFitApp).graph
    }
}
