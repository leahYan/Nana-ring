package com.nanaring.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nanaring.app.data.local.dao.HeartRateDao
import com.nanaring.app.data.local.entity.HeartRateEntity

@Database(
    entities = [HeartRateEntity::class],
    version  = 2,
    exportSchema = false,
)
abstract class RingDatabase : RoomDatabase() {

    abstract fun heartRateDao(): HeartRateDao

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

        fun getInstance(context: Context): RingDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    RingDatabase::class.java,
                    "nana_ring.db",
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
