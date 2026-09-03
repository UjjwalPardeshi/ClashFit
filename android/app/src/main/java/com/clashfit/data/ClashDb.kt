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
    ],
    version = 3,
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
