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

        val unsynced         = repository.getUnsynced()
        val unsyncedActivity = repository.getUnsyncedActivity()
        val unsyncedSleep    = repository.getUnsyncedSleep()

        if (unsynced.isEmpty() && unsyncedActivity.isEmpty() && unsyncedSleep.isEmpty()) {
            return Result.success()
        }

        // Refresh the access token once before all inserts (TC-BE-06).
        if (!syncAuth.refreshIfNeeded()) return Result.retry()

        return try {
            val hrOk       = if (unsynced.isEmpty()) true else syncAuth.insertHeartRate(unsynced)
            val spo2Ok     = if (unsynced.isEmpty()) true else syncAuth.insertSpO2(unsynced)
            val hrvOk      = if (unsynced.isEmpty()) true else syncAuth.insertHRV(unsynced)
            val stOk       = if (unsynced.isEmpty()) true else syncAuth.insertStress(unsynced)
            val tmpOk      = if (unsynced.isEmpty()) true else syncAuth.insertTemperature(unsynced)
            val bpOk       = if (unsynced.isEmpty()) true else syncAuth.insertBloodPressure(unsynced)
            val activityOk = if (unsyncedActivity.isEmpty()) true else syncAuth.insertActivity(unsyncedActivity)
            val sleepOk    = if (unsyncedSleep.isEmpty()) true else syncAuth.insertSleepSessions(unsyncedSleep)

            if (hrOk && spo2Ok && hrvOk && stOk && tmpOk && bpOk && activityOk && sleepOk) {
                if (unsynced.isNotEmpty()) repository.markSynced(unsynced.map { it.id })
                if (unsyncedActivity.isNotEmpty()) repository.markActivitySynced(unsyncedActivity.map { it.id })
                if (unsyncedSleep.isNotEmpty()) repository.markSleepSynced(unsyncedSleep.map { it.id })
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
