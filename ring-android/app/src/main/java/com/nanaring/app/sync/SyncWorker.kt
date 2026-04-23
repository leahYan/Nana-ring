package com.nanaring.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nanaring.app.NanaRingApplication
import com.nanaring.app.data.remote.BatchUploadRequest
import com.nanaring.app.data.remote.HeartRateDto
import com.nanaring.app.data.remote.HRVDto
import com.nanaring.app.data.remote.SpO2Dto
import com.nanaring.app.data.remote.StressDto
import com.nanaring.app.data.remote.TemperatureDto
import com.nanaring.app.data.local.entity.HeartRateEntity

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as NanaRingApplication).container
        val repository  = container.ringRepository
        val apiService  = container.apiService()
        val authManager = container.authManager

        if (!authManager.isConfigured()) return Result.failure()

        val unsynced = repository.getUnsynced()
        if (unsynced.isEmpty()) return Result.success()

        // Each HeartRateEntity row is one measurement session. Split its fields
        // into the separate typed lists the backend expects — one table per metric.
        val heartRates   = mutableListOf<HeartRateDto>()
        val spO2Records  = mutableListOf<SpO2Dto>()
        val hrvRecords   = mutableListOf<HRVDto>()
        val stressRecords = mutableListOf<StressDto>()
        val tempRecords  = mutableListOf<TemperatureDto>()

        for (e in unsynced) {
            heartRates.add(
                HeartRateDto(
                    device_timestamp = e.deviceTimestamp,
                    bpm              = e.bpm,
                    rri              = e.rri,
                    source           = e.source,
                    sync_id          = e.syncId,
                    firmware_version = e.firmwareVersion,
                    hardware_version = e.hardwareVersion,
                )
            )
            e.spO2?.let { pct ->
                spO2Records.add(
                    SpO2Dto(
                        device_timestamp = e.deviceTimestamp,
                        percent          = pct,
                        reading_type     = "manual",
                        sync_id          = e.syncId?.let { "$it-spo2" },
                        firmware_version = e.firmwareVersion,
                        hardware_version = e.hardwareVersion,
                    )
                )
            }
            e.hrv?.let { ms ->
                hrvRecords.add(
                    HRVDto(
                        device_timestamp = e.deviceTimestamp,
                        ms               = ms,
                        source           = e.source,
                        sync_id          = e.syncId?.let { "$it-hrv" },
                        firmware_version = e.firmwareVersion,
                        hardware_version = e.hardwareVersion,
                    )
                )
            }
            e.stress?.let { lvl ->
                stressRecords.add(
                    StressDto(
                        device_timestamp = e.deviceTimestamp,
                        level            = lvl,
                        source           = e.source,
                        sync_id          = e.syncId?.let { "$it-stress" },
                        firmware_version = e.firmwareVersion,
                        hardware_version = e.hardwareVersion,
                    )
                )
            }
            e.temperature?.let { c ->
                tempRecords.add(
                    TemperatureDto(
                        device_timestamp = e.deviceTimestamp,
                        celsius_primary  = c,
                        measurement_mode = "manual_once",
                        sync_id          = e.syncId?.let { "$it-temp" },
                        firmware_version = e.firmwareVersion,
                        hardware_version = e.hardwareVersion,
                    )
                )
            }
        }

        return try {
            val response = apiService.batchUpload(
                bearerToken = "Bearer ${authManager.jwt}",
                request     = BatchUploadRequest(
                    heart_rate  = heartRates,
                    spo2        = spO2Records,
                    hrv         = hrvRecords,
                    stress      = stressRecords,
                    temperature = tempRecords,
                ),
            )
            if (response.errors.isEmpty()) {
                repository.markSynced(unsynced.map { it.id })
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
