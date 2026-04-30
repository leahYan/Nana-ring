package com.nanaring.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep",
    indices = [Index(value = ["syncId"], unique = true)],
)
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sleepId: String,             // pre-generated UUID — used as PK in ring_sleep
    val deviceTimestamp: Long,
    val daysAgo: Int,
    val startTimestamp: Long,        // ms (first non-zero sleep slot)
    val endTimestamp: Long,          // ms (last non-zero sleep slot end)
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val notWornMinutes: Int,
    val totalMinutes: Int,
    val wakingCount: Int,
    val stagesJson: String,          // JSON: [{"type":3,"durationMinutes":60},…]
    val syncId: String,              // "sleep-{daysAgo}-{day}-{month}-{year}"
    val isSynced: Boolean = false,
)
