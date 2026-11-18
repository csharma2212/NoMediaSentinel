package com.sentinel.nomedia.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.sentinel.nomedia.service.WatchdogService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val hasStorageAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }

            if (hasStorageAccess) {
                try {
                    val serviceIntent = Intent(context, WatchdogService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (_: Exception) {
                    // Handled safely if OEM restricts immediate post-boot background starts
                }
            }
        }
    }
}
