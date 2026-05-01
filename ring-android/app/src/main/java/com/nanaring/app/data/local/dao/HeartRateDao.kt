package com.nanaring.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nanaring.app.data.local.entity.HeartRateEntity

@Dao
interface HeartRateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: HeartRateEntity): Long

    @Query("SELECT * FROM heart_rate WHERE isSynced = 0")
    suspend fun getUnsynced(): List<HeartRateEntity>

    @Query("UPDATE heart_rate SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT * FROM heart_rate ORDER BY deviceTimestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<HeartRateEntity>
}
