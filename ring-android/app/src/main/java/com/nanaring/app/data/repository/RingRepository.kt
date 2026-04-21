package com.nanaring.app.data.repository

import com.nanaring.app.data.local.entity.HeartRateEntity
import com.nanaring.app.ui.screens.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the ring data source.
 *
 * Only [PhysicalRingDataSource] is used in production.
 * No mock or fake implementation may be wired into [AppContainer].
 */
interface RingRepository {
    /** Live connection + BPM state for the UI. */
    val connectionState: StateFlow<ConnectionState>

    /** Start BLE scan. Transitions to [ConnectionState.ScanResults] when complete. */
    suspend fun startScan()

    /** Stop BLE scan / disconnect. */
    suspend fun stopScan()

    /** Connect to a specific ring by MAC address (chosen from ScanResults). */
    suspend fun connectToDevice(mac: String)

    /** Return all heart rate readings not yet synced to the server. */
    suspend fun getUnsynced(): List<HeartRateEntity>

    /** Mark the given records as synced. */
    suspend fun markSynced(ids: List<Long>)
}
