package com.nanaring.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activity",
    indices = [Index(value = ["syncId"], unique = true)],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceTimestamp: Long,       // ms when read from ring
    val daysAgo: Int,                // 0 = today, 1 = yesterday, up to 6
    val steps: Int,
    val runningSteps: Int,
    val walkDistanceMeters: Int,
    val caloriesKcal: Int,           // SDK calorie / 1000
    val sportDurationSeconds: Int,
    val sleepDurationSeconds: Int,
    val syncId: String,              // "activity-{daysAgo}-{day}-{month}-{year}"
    val isSynced: Boolean = false,
)
