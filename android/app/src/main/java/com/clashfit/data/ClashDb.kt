package com.clashfit.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class, CalibrationEntity::class,
        SessionEntity::class, SetEntity::class, RepEntity::class,
        StreakEntity::class, PersonalBestEntity::class, LadderEntity::class,
        RunEntity::class, RunPointEntity::class,
        AlarmEntity::class, GhostEntity::class, PostureSampleEntity::class,
        MetaEntity::class, AchievementEntity::class, WeeklyEntity::class, XpLedgerEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class ClashDb : RoomDatabase() {
    abstract fun profile(): ProfileDao
    abstract fun calibration(): CalibrationDao
    abstract fun sessions(): SessionDao
    abstract fun streak(): StreakDao
    abstract fun bests(): BestDao
    abstract fun ladders(): LadderDao
    abstract fun runs(): RunDao
    abstract fun alarms(): AlarmDao
    abstract fun ghosts(): GhostDao
    abstract fun posture(): PostureDao
    abstract fun meta(): MetaDao
    abstract fun achievements(): AchievementDao
    abstract fun weekly(): WeeklyDao
    abstract fun xpLedger(): XpLedgerDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS posture (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                tMs INTEGER NOT NULL,
                score INTEGER NOT NULL,
                neckDeg REAL NOT NULL,
                elevation REAL NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_posture_tMs ON posture(tMs)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sets ADD COLUMN asymmetryPct INTEGER")
        db.execSQL("ALTER TABLE sets ADD COLUMN weakerSide TEXT")
    }
}

/**
 * The four progression tables. Written exactly as Room's own exported schema writes them
 * (`app/schemas/com.clashfit.data.ClashDb/4.json`), because Room validates a migrated database
 * column by column INCLUDING default values. An extra `DEFAULT 0` here, however harmless it looks,
 * makes Room reject the database on the next launch and crash every phone that already had the
 * app installed. `MigrationSqlTest` pins these against the exported schema so they cannot drift.
 */
internal val MIGRATION_3_4_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `meta` (`id` INTEGER NOT NULL, `xp` INTEGER NOT NULL, " +
        "`level` INTEGER NOT NULL, `sessions` INTEGER NOT NULL, `wins` INTEGER NOT NULL, " +
        "`totalReps` INTEGER NOT NULL, `cleanReps` INTEGER NOT NULL, `totalDamage` INTEGER NOT NULL, " +
        "`familiesPlayedMask` INTEGER NOT NULL, `pbCount` INTEGER NOT NULL, " +
        "`weeklyChallengesDone` INTEGER NOT NULL, `bestStreak` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `achievements` (`id` TEXT NOT NULL, `unlockedAtMs` INTEGER NOT NULL, " +
        "PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `weekly` (`weekKey` TEXT NOT NULL, `metric` TEXT NOT NULL, " +
        "`value` INTEGER NOT NULL, `completedAtMs` INTEGER, PRIMARY KEY(`weekKey`))",
    "CREATE TABLE IF NOT EXISTS `xp_ledger` (`sessionId` INTEGER NOT NULL, `xp` INTEGER NOT NULL, " +
        "`atMs` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))",
)

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_3_4_SQL.forEach(db::execSQL)
    }
}
