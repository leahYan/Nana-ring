package com.nanaring.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nanaring.app.NanaRingApplication
import com.nanaring.app.data.remote.BatchUploadRequest
import com.nanaring.app.data.remote.HeartRateDto

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

        val dtos = unsynced.map { e ->
            HeartRateDto(
                device_timestamp = e.deviceTimestamp,
                bpm              = e.bpm,
                rri              = e.rri,
                source           = e.source,
                sync_id          = e.syncId,
                firmware_version = e.firmwareVersion,
                hardware_version = e.hardwareVersion,
            )
        }

        return try {
            val response = apiService.batchUpload(
                bearerToken = "Bearer ${authManager.jwt}",
                request     = BatchUploadRequest(heart_rate = dtos),
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
