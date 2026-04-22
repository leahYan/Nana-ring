package com.nanaring.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heart_rate")
data class HeartRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceTimestamp: Long,
    val bpm: Int,
    val rri: Int?,
    val spO2: Int?,           // blood oxygen %
    val systolic: Int?,       // blood pressure systolic mmHg
    val diastolic: Int?,      // blood pressure diastolic mmHg
    val hrv: Int?,            // heart rate variability ms
    val stress: Int?,         // stress level 0–100
    val temperature: Float?,  // body temperature °C
    val source: String,
    val syncId: String,
    val firmwareVersion: String?,
    val hardwareVersion: String?,
    val isSynced: Boolean = false,
)
