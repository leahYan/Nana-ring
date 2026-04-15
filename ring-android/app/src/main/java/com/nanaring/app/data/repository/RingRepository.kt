package com.nanaring.app.data.repository

import com.nanaring.app.data.local.entity.HeartRateEntity

/**
 * Abstraction over the ring data source.
 *
 * MockRingDataSource — generates synthetic readings for emulator testing.
 * PhysicalRingDataSource — wraps qring_sdk_1.0.0.1.aar for real hardware (stub until ring ships).
 */
interface RingRepository {
    /** Start BLE scanning / data capture. */
    suspend fun startScan()

    /** Stop BLE scanning / data capture. */
    suspend fun stopScan()

    /** Return all heart rate readings not yet synced to the server. */
    suspend fun getUnsynced(): List<HeartRateEntity>

    /** Mark the given records as synced. */
    suspend fun markSynced(ids: List<Long>)
}
