package com.clashfit

import android.content.Context
import androidx.room.Room
import com.clashfit.audio.Haptics
import com.clashfit.audio.Sfx
import com.clashfit.auth.AuthService
import com.clashfit.auth.FirebaseAuthService
import com.clashfit.auth.LocalAuthService
import com.clashfit.coach.CloudCoach
import com.clashfit.coach.CoachConfig
import com.clashfit.coach.CoachEngine
import kotlinx.coroutines.flow.first
import com.clashfit.coach.LlmEngine
import com.clashfit.coach.RefereeEyes
import com.clashfit.coach.SpeechOut
import com.clashfit.cloud.CloudConfig
import com.clashfit.cloud.ScoreSync
import com.clashfit.cloud.WeeklyTotals
import com.clashfit.cloud.FirestoreLeaderboard
import com.clashfit.cloud.FirestoreFriends
import com.clashfit.cloud.LeaderboardRepository
import com.clashfit.cloud.FriendsRepository
import com.clashfit.cloud.NoCloudLeaderboard
import com.clashfit.cloud.NoCloudFriends
import com.clashfit.core.config.ConfigStore
import com.clashfit.core.util.Clock
import com.clashfit.data.ClashDb
import com.clashfit.data.Prefs
import com.clashfit.play.PlayHub
import com.clashfit.voice.VoiceCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
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
            .addMigrations(
                com.clashfit.data.MIGRATION_1_2,
                com.clashfit.data.MIGRATION_2_3,
                com.clashfit.data.MIGRATION_3_4,
                com.clashfit.data.MIGRATION_4_5,
                com.clashfit.data.MIGRATION_5_6,
                com.clashfit.data.MIGRATION_6_7,
            )
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
    }

    val prefs: Prefs by lazy { Prefs(app) }

    val meta: com.clashfit.meta.MetaRepository by lazy { com.clashfit.meta.RoomMetaRepository(db, clock) }
    val rewards: com.clashfit.meta.RewardStore = com.clashfit.meta.RewardStore()

    // The coach and the villain are the same model; the template bank always ships beneath it.
    val llmEngine: LlmEngine by lazy { LlmEngine(app, CoachConfig(), clock, scope, json) }
    // The cloud rung sits between them, and only speaks when the player has switched it on.
    val cloudCoach: CloudCoach by lazy { CloudCoach() }
    val coachEngine: CoachEngine by lazy {
        CoachEngine(llmEngine, cloudCoach, cloudAllowed = { prefs.settings.first().cloudCoach }, json = json)
    }
    /** The referee's eyes: the on-device model looking at the worst rep. On-device only, always. */
    val refereeEyes: RefereeEyes by lazy { RefereeEyes(llmEngine) }
    val speechOut: SpeechOut by lazy { SpeechOut(app, mainScope) }

    // Audio fires first. Everything is synthesised; there are no sound assets.
    val sfx: Sfx by lazy { Sfx() }
    val haptics: Haptics by lazy { Haptics(app, prefs, scope) }
    val voiceCommands: VoiceCommands by lazy { VoiceCommands(app, mainScope) }

    // Multiplayer links and pass-the-phone rosters outlive any one screen.
    val playHub: PlayHub by lazy { PlayHub(app, json, clock, scope) }

    val auth: AuthService by lazy { if (CloudConfig.isConfigured) FirebaseAuthService(scope) else LocalAuthService(app, scope) }
    val leaderboard: LeaderboardRepository by lazy { if (CloudConfig.isConfigured) FirestoreLeaderboard(auth, scope) else NoCloudLeaderboard("This build has no cloud keys. Add FIREBASE_API_KEY, FIREBASE_APP_ID and FIREBASE_PROJECT_ID to android/local.properties.") }
    val friends: FriendsRepository by lazy { if (CloudConfig.isConfigured) FirestoreFriends(auth, scope) else NoCloudFriends("This build has no cloud keys. Add FIREBASE_API_KEY, FIREBASE_APP_ID and FIREBASE_PROJECT_ID to android/local.properties.") }

    /**
     * Publishes the player's standing to the leaderboard. Started once by [ClashFitApp]; it does
     * nothing at all until somebody is signed in, and nothing ever leaves here but scores.
     */
    val scoreSync: ScoreSync by lazy {
        ScoreSync(
            auth = auth,
            leaderboard = leaderboard,
            meta = meta,
            streakFlow = db.streak().observe().map { it?.best },
            scope = scope,
            clock = clock,
            weeklyTotals = { since ->
                WeeklyTotals(
                    damage = db.sessions().damageSince(since),
                    cleanReps = db.sessions().cleanRepsSince(since),
                    chaseScore = db.runs().chaseScoreSince(since),
                )
            },
        )
    }

    companion object {
        fun of(context: Context): AppGraph = (context.applicationContext as ClashFitApp).graph
    }
}
