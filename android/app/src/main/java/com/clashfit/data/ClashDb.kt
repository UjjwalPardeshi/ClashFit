package com.clashfit.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class, CalibrationEntity::class,
        SessionEntity::class, SetEntity::class, RepEntity::class,
        StreakEntity::class, PersonalBestEntity::class, LadderEntity::class,
        RunEntity::class, RunPointEntity::class,
        AlarmEntity::class, GhostEntity::class,
    ],
    version = 1,
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
}
