package com.nanaring.app.data.repository

import com.nanaring.app.data.local.entity.ActivityEntity
import com.nanaring.app.data.local.entity.HeartRateEntity
import com.nanaring.app.data.local.entity.SleepEntity
import com.nanaring.app.ui.screens.ConnectionState
import kotlinx.coroutines.flow.StateFlow

interface RingRepository {
    val connectionState: StateFlow<ConnectionState>
    val recentReadings: StateFlow<List<HeartRateEntity>>

    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connectToDevice(mac: String)

    suspend fun getUnsynced(): List<HeartRateEntity>
    suspend fun markSynced(ids: List<Long>)

    suspend fun getUnsyncedActivity(): List<ActivityEntity>
    suspend fun markActivitySynced(ids: List<Long>)

    suspend fun getUnsyncedSleep(): List<SleepEntity>
    suspend fun markSleepSynced(ids: List<Long>)
}
