package com.nanaring.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nanaring.app.NanaRingApplication

private const val TAG = "SyncWorker"

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as NanaRingApplication).container
        val repository = container.ringRepository
        val syncAuth   = container.supabaseSyncAuth

        Log.d(TAG, "doWork started (attempt ${runAttemptCount + 1})")

        if (!syncAuth.restoreSession()) {
            Log.w(TAG, "No session — skipping sync")
            return Result.failure()
        }

        val unsynced         = repository.getUnsynced()
        val unsyncedActivity = repository.getUnsyncedActivity()
        val unsyncedSleep    = repository.getUnsyncedSleep()

        Log.d(TAG, "Unsynced — heartRate=${unsynced.size} activity=${unsyncedActivity.size} sleep=${unsyncedSleep.size}")

        if (unsynced.isEmpty() && unsyncedActivity.isEmpty() && unsyncedSleep.isEmpty()) {
            Log.d(TAG, "Nothing to sync")
            return Result.success()
        }

        if (!syncAuth.refreshIfNeeded()) {
            Log.w(TAG, "Token refresh failed — will retry")
            return Result.retry()
        }

        return try {
            val hrOk       = if (unsynced.isEmpty()) true else syncAuth.insertHeartRate(unsynced)
            val spo2Ok     = if (unsynced.isEmpty()) true else syncAuth.insertSpO2(unsynced)
            val hrvOk      = if (unsynced.isEmpty()) true else syncAuth.insertHRV(unsynced)
            val stOk       = if (unsynced.isEmpty()) true else syncAuth.insertStress(unsynced)
            val tmpOk      = if (unsynced.isEmpty()) true else syncAuth.insertTemperature(unsynced)
            val bpOk       = if (unsynced.isEmpty()) true else syncAuth.insertBloodPressure(unsynced)
            val activityOk = if (unsyncedActivity.isEmpty()) true else syncAuth.insertActivity(unsyncedActivity)
            val sleepOk    = if (unsyncedSleep.isEmpty()) true else syncAuth.insertSleepSessions(unsyncedSleep)

            Log.d(TAG, "Insert results — hr=$hrOk spo2=$spo2Ok hrv=$hrvOk stress=$stOk temp=$tmpOk bp=$bpOk activity=$activityOk sleep=$sleepOk")

            if (hrOk && spo2Ok && hrvOk && stOk && tmpOk && bpOk && activityOk && sleepOk) {
                if (unsynced.isNotEmpty()) repository.markSynced(unsynced.map { it.id })
                if (unsyncedActivity.isNotEmpty()) repository.markActivitySynced(unsyncedActivity.map { it.id })
                if (unsyncedSleep.isNotEmpty()) repository.markSleepSynced(unsyncedSleep.map { it.id })
                Log.d(TAG, "Sync complete")
                Result.success()
            } else {
                Log.w(TAG, "Partial failure — retrying")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync exception: ${e.message}", e)
            Result.retry()
        }
    }
}
