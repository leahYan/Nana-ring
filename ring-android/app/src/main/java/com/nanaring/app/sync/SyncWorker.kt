package com.nanaring.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nanaring.app.NanaRingApplication

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as NanaRingApplication).container
        val repository = container.ringRepository
        val syncAuth   = container.supabaseSyncAuth

        // No stored session → user must re-authenticate; don't retry automatically.
        if (!syncAuth.restoreSession()) return Result.failure()

        val unsynced = repository.getUnsynced()
        if (unsynced.isEmpty()) return Result.success()

        // Refresh the access token once before all inserts (TC-BE-06).
        if (!syncAuth.refreshIfNeeded()) return Result.retry()

        return try {
            val hrOk   = syncAuth.insertHeartRate(unsynced)
            val spo2Ok = syncAuth.insertSpO2(unsynced)
            val hrvOk  = syncAuth.insertHRV(unsynced)
            val stOk   = syncAuth.insertStress(unsynced)
            val tmpOk  = syncAuth.insertTemperature(unsynced)

            if (hrOk && spo2Ok && hrvOk && stOk && tmpOk) {
                repository.markSynced(unsynced.map { it.id })
                Result.success()
            } else {
                // Partial failure — retry with exponential backoff.
                // The Prefer: resolution=ignore-duplicates header prevents duplicates
                // for rows that were already successfully inserted (TC-BE-02).
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
