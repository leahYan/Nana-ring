package com.nanaring.app.data.repository

import com.nanaring.app.data.local.dao.HeartRateDao
import com.nanaring.app.data.local.entity.HeartRateEntity
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.random.Random

/**
 * Synthetic data source for emulator testing.
 *
 * Generates realistic heart rate readings matching the ring_data_dictionary.csv schema so
 * the full pipeline (local Room → WorkManager → POST /api/v1/data/batch) can be validated
 * without physical hardware.
 */
class MockRingDataSource(private val heartRateDao: HeartRateDao) : RingRepository {

    private var scanning = false

    override suspend fun startScan() {
        scanning = true
        generateMockReadings()
    }

    override suspend fun stopScan() {
        scanning = false
    }

    override suspend fun getUnsynced(): List<HeartRateEntity> =
        heartRateDao.getUnsynced()

    override suspend fun markSynced(ids: List<Long>) =
        heartRateDao.markSynced(ids)

    private suspend fun generateMockReadings() {
        // Emit 3 synthetic readings at 1-second intervals when scan starts
        repeat(3) {
            if (!scanning) return
            val entity = HeartRateEntity(
                deviceTimestamp = System.currentTimeMillis(),
                bpm             = Random.nextInt(60, 100),
                rri             = Random.nextInt(600, 1000),
                source          = "continuous",
                syncId          = UUID.randomUUID().toString(),
                firmwareVersion = "1.0.0.1",
                hardwareVersion = "HW_v2",
            )
            heartRateDao.insert(entity)
            delay(1_000)
        }
    }
}
