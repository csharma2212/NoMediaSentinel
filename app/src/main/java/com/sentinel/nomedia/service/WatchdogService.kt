package com.sentinel.nomedia.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sentinel.nomedia.R
import com.sentinel.nomedia.detector.CulpritInfo
import com.sentinel.nomedia.detector.SentinelObserver
import com.sentinel.nomedia.ui.MainActivity
import java.io.File

class WatchdogService : Service() {

    private val CHANNEL_ID = "nomedia_watchdog_channel"
    private val BREACH_CHANNEL_ID = "nomedia_breach_channel"
    private val NOTIFICATION_ID = 1001
    private val BREACH_NOTIFICATION_BASE_ID = 2000

    private val observers = mutableListOf<SentinelObserver>()

    companion object {
        var isRunning: Boolean = false
            private set

        // Shared in-memory event log for UI display
        private val _eventLogs = mutableListOf<String>()
        val eventLogs: List<String> get() = _eventLogs.toList()

        private var logListener: ((String) -> Unit)? = null

        fun setLogListener(listener: ((String) -> Unit)?) {
            logListener = listener
        }

        fun postLog(message: String) {
            synchronized(_eventLogs) {
                if (_eventLogs.size > 200) {
                    _eventLogs.removeAt(0)
                }
                _eventLogs.add(message)
            }
            logListener?.invoke(message)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannels()
        startServiceForeground()
        registerWatchdogs()
        postLog("[DAEMON STARTED] Sentinel Watchdog is actively guarding DCIM, Pictures, Downloads, and Movies.")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val guardChannel = NotificationChannel(
                CHANNEL_ID,
                "NoMedia Sentinel Guard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors media directories against rogue .nomedia files"
            }
            manager.createNotificationChannel(guardChannel)

            val breachChannel = NotificationChannel(
                BREACH_CHANNEL_ID,
                "NoMedia Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a rogue .nomedia breach is detected and purged"
            }
            manager.createNotificationChannel(breachChannel)
        }
    }

    private fun startServiceForeground() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NoMedia Sentinel Guard: Active")
            .setContentText("Watching media folders. Zero-reboot re-index enabled.")
            .setSmallIcon(R.drawable.ic_policeman_badge)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun registerWatchdogs() {
        val targetPaths = listOf(
            File(Environment.getExternalStorageDirectory(), "DCIM"),
            File(Environment.getExternalStorageDirectory(), "Pictures"),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "Movies")
        )

        for (targetDir in targetPaths) {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val observer = SentinelObserver(
                context = this,
                rootDirectory = targetDir,
                onBreach = { culprit, rogueFile ->
                    notifyBreach(culprit, rogueFile)
                },
                onLog = { msg ->
                    postLog(msg)
                }
            )
            observer.startWatching()
            observers.add(observer)
        }
    }

    private fun notifyBreach(culprit: CulpritInfo, rogueFile: File) {
        val message = "Blocked .nomedia in ${rogueFile.parentFile?.name ?: "media"}. Culprit: ${culprit.appLabel}"
        postLog("[ALERT] $message (${culprit.packageName})")

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val breachNotification = NotificationCompat.Builder(this, BREACH_CHANNEL_ID)
            .setContentTitle("Breach Thwarted: .nomedia Purged")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Rogue file purged instantly at ${rogueFile.absolutePath}.\n" +
                "Source app: ${culprit.appLabel} (${culprit.packageName})\n" +
                "Confidence: ${culprit.confidence}\n" +
                "Media re-indexed without phone reboot."
            ))
            .setSmallIcon(R.drawable.ic_policeman_badge)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((BREACH_NOTIFICATION_BASE_ID + System.currentTimeMillis() % 1000).toInt(), breachNotification)
    }

    override fun onDestroy() {
        isRunning = false
        for (observer in observers) {
            observer.stopWatching()
        }
        observers.clear()
        postLog("[DAEMON STOPPED] Sentinel Watchdog has been deactivated.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
