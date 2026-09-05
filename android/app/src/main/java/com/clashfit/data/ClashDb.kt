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
    version = 6,
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

/**
 * Walking, and the two numbers a personal best needs.
 *
 * Three columns added to `runs`. The rule from `MIGRATION_3_4` applies in reverse here: a NOT NULL
 * column added to an existing table *must* carry a SQL default, so the entity declares the same
 * default through `@ColumnInfo(defaultValue = …)` and Room's exported schema expects it. The two
 * halves are pinned together by `MigrationSqlTest`; change one without the other and every phone
 * that already has the app crashes on launch while a fresh install looks perfect.
 *
 * `fastestKmSec` is nullable and therefore needs no default: existing rows get NULL, which is the
 * truth — nobody computed a fastest kilometre for them.
 */
internal val MIGRATION_4_5_SQL: List<String> = listOf(
    "ALTER TABLE `runs` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'RUN'",
    "ALTER TABLE `runs` ADD COLUMN `steps` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `runs` ADD COLUMN `fastestKmSec` REAL",
)

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_4_5_SQL.forEach(db::execSQL)
    }
}

/**
 * The Zombie Run score, so a chase can be put on a leaderboard.
 *
 * Same rule as the migration above, for the same reason: a NOT NULL column added to an existing
 * table must carry a SQL default, the entity declares the identical default through
 * `@ColumnInfo`, and `MigrationSqlTest` pins the two together.
 */
internal val MIGRATION_5_6_SQL: List<String> = listOf(
    "ALTER TABLE `runs` ADD COLUMN `score` INTEGER NOT NULL DEFAULT 0",
)

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_5_6_SQL.forEach(db::execSQL)
    }
}
