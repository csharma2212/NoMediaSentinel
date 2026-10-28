package com.sentinel.nomedia.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.sentinel.nomedia.detector.SentinelObserver
import com.sentinel.nomedia.ui.MainActivity
import java.io.File

class WatchdogService : LifecycleService() {
    private val observers = mutableListOf<SentinelObserver>()
    private val CHANNEL_ID = "nomedia_watchdog"
    private val NOTIF_ID = 40401

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, createNotification("Sentinel Active: Storage Protected"))
        startWatchdogs()
    }

    private fun startWatchdogs() {
        val targets = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )
        targets.forEach { target ->
            if (!target.exists()) target.mkdirs()
            val observer = SentinelObserver(this, target) { logLine ->
                val intent = Intent("com.sentinel.nomedia.LOG_EVENT")
                intent.putExtra("LOG_LINE", logLine)
                sendBroadcast(intent)
            }
            observer.startWatching()
            observers.add(observer)
        }
    }

    private fun createNotification(content: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NoMedia Sentinel Guard")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_shield)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Storage Watchdog", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        observers.forEach { it.stopWatching() }
        observers.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
