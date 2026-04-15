package com.nanaring.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nanaring.app.data.local.dao.HeartRateDao
import com.nanaring.app.data.local.entity.HeartRateEntity

@Database(
    entities = [HeartRateEntity::class],
    version  = 1,
    exportSchema = false,
)
abstract class RingDatabase : RoomDatabase() {

    abstract fun heartRateDao(): HeartRateDao

    companion object {
        @Volatile
        private var INSTANCE: RingDatabase? = null

        fun getInstance(context: Context): RingDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    RingDatabase::class.java,
                    "nana_ring.db",
                ).build().also { INSTANCE = it }
            }
    }
}
