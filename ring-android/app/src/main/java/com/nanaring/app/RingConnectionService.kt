package com.nanaring.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service that keeps the process alive while the ring is connected
 * and measuring. Without this, Samsung's Adaptive Battery kills the process
 * mid-measurement, preventing Room inserts and Supabase sync from completing.
 *
 * The BLE logic stays in PhysicalRingDataSource — this service only provides
 * the foreground service context required by Android to protect the process.
 */
class RingConnectionService : Service() {

    companion object {
        private const val CHANNEL_ID   = "ring_connection"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RingConnectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RingConnectionService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Ring connection",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Shown while the ring is connected and measuring" }
            )
        }
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Nana Ring connected")
            .setContentText("Measuring and syncing health data")
            .setOngoing(true)
            .build()
}
