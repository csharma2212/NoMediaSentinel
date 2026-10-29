package com.sentinel.nomedia.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sentinel.nomedia.R
import com.sentinel.nomedia.ui.MainActivity
import java.io.File

class WatchdogService : Service() {

    private val CHANNEL_ID = "nomedia_watchdog_channel"
    private val NOTIFICATION_ID = 1001
    private val observers = mutableListOf<FileObserver>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startServiceForeground()
        registerWatchdogs()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NoMedia Sentinel Guard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors media directories against rogue .nomedia files"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startServiceForeground() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NoMedia Sentinel Active")
            .setContentText("Actively protecting DCIM, Pictures, and Downloads")
            .setSmallIcon(R.drawable.ic_policeman_badge)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @Suppress("DEPRECATION")
    private fun registerWatchdogs() {
        val targetPaths = listOf(
            File(Environment.getExternalStorageDirectory(), "DCIM/Camera").absolutePath,
            File(Environment.getExternalStorageDirectory(), "Pictures").absolutePath,
            File(Environment.getExternalStorageDirectory(), "Download").absolutePath
        )

        for (path in targetPaths) {
            val targetDir = File(path)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val observer = object : FileObserver(path, CREATE or MOVED_TO) {
                override fun onEvent(event: Int, fileName: String?) {
                    if (fileName != null && fileName.equals(".nomedia", ignoreCase = true)) {
                        val fileToDelete = File(path, fileName)
                        if (fileToDelete.exists()) {
                            fileToDelete.delete()
                        }
                    }
                }
            }
            observer.startWatching()
            observers.add(observer)
        }
    }

    override fun onDestroy() {
        for (observer in observers) {
            observer.stopWatching()
        }
        observers.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}