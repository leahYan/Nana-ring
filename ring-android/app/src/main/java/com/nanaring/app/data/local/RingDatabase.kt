package com.nanaring.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nanaring.app.data.local.dao.ActivityDao
import com.nanaring.app.data.local.dao.HeartRateDao
import com.nanaring.app.data.local.dao.SleepDao
import com.nanaring.app.data.local.entity.ActivityEntity
import com.nanaring.app.data.local.entity.HeartRateEntity
import com.nanaring.app.data.local.entity.SleepEntity

@Database(
    entities = [HeartRateEntity::class, ActivityEntity::class, SleepEntity::class],
    version  = 3,
    exportSchema = false,
)
abstract class RingDatabase : RoomDatabase() {

    abstract fun heartRateDao(): HeartRateDao
    abstract fun activityDao(): ActivityDao
    abstract fun sleepDao(): SleepDao

    companion object {
        @Volatile
        private var INSTANCE: RingDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE heart_rate ADD COLUMN spO2 INTEGER")
                db.execSQL("ALTER TABLE heart_rate ADD COLUMN systolic INTEGER")
                db.execSQL("ALTER TABLE heart_rate ADD COLUMN diastolic INTEGER")
                db.execSQL("ALTER TABLE heart_rate ADD COLUMN hrv INTEGER")
                db.execSQL("ALTER TABLE heart_rate ADD COLUMN stress INTEGER")
                db.execSQL("ALTER TABLE heart_rate ADD COLUMN temperature REAL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `activity` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `deviceTimestamp` INTEGER NOT NULL,
                        `daysAgo` INTEGER NOT NULL,
                        `steps` INTEGER NOT NULL,
                        `runningSteps` INTEGER NOT NULL,
                        `walkDistanceMeters` INTEGER NOT NULL,
                        `caloriesKcal` INTEGER NOT NULL,
                        `sportDurationSeconds` INTEGER NOT NULL,
                        `sleepDurationSeconds` INTEGER NOT NULL,
                        `syncId` TEXT NOT NULL,
                        `isSynced` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_activity_syncId` ON `activity` (`syncId`)"
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sleep` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sleepId` TEXT NOT NULL,
                        `deviceTimestamp` INTEGER NOT NULL,
                        `daysAgo` INTEGER NOT NULL,
                        `startTimestamp` INTEGER NOT NULL,
                        `endTimestamp` INTEGER NOT NULL,
                        `deepMinutes` INTEGER NOT NULL,
                        `lightMinutes` INTEGER NOT NULL,
                        `remMinutes` INTEGER NOT NULL,
                        `awakeMinutes` INTEGER NOT NULL,
                        `notWornMinutes` INTEGER NOT NULL,
                        `totalMinutes` INTEGER NOT NULL,
                        `wakingCount` INTEGER NOT NULL,
                        `stagesJson` TEXT NOT NULL,
                        `syncId` TEXT NOT NULL,
                        `isSynced` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_syncId` ON `sleep` (`syncId`)"
                )
            }
        }

        fun getInstance(context: Context): RingDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    RingDatabase::class.java,
                    "nana_ring.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
