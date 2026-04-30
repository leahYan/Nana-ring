package com.nanaring.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nanaring.app.data.local.entity.ActivityEntity

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ActivityEntity): Long

    @Query("SELECT * FROM activity WHERE isSynced = 0")
    suspend fun getUnsynced(): List<ActivityEntity>

    @Query("UPDATE activity SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}
