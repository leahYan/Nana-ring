package com.nanaring.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heart_rate")
data class HeartRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceTimestamp: Long,
    val bpm: Int,
    val rri: Int?,
    val source: String,
    val syncId: String,
    val firmwareVersion: String?,
    val hardwareVersion: String?,
    val isSynced: Boolean = false,
)
