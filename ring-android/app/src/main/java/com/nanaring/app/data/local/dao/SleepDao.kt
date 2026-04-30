package com.nanaring.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nanaring.app.data.local.entity.SleepEntity

@Dao
interface SleepDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SleepEntity): Long

    @Query("SELECT * FROM sleep WHERE isSynced = 0")
    suspend fun getUnsynced(): List<SleepEntity>

    @Query("UPDATE sleep SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}
