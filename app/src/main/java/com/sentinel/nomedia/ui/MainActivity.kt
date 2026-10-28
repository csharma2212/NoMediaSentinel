package com.sentinel.nomedia.ui

import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sentinel.nomedia.databinding.ActivityMainBinding
import com.sentinel.nomedia.detector.SentinelObserver
import com.sentinel.nomedia.service.WatchdogService
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isServiceRunning = false

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val log = intent.getStringExtra("LOG_LINE") ?: return
            binding.tvAuditLogs.append("\n" + log)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        registerReceiver(logReceiver, IntentFilter("com.sentinel.nomedia.LOG_EVENT"), Context.RECEIVER_NOT_EXPORTED)
        binding.btnPermissions.setOnClickListener { checkAndPromptPermissions() }
        binding.btnToggleWatchdog.setOnClickListener { toggleService() }
        binding.btnSweep.setOnClickListener { executeManualSweep() }
    }

    private fun checkAndPromptPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:" + packageName)
                }
                startActivity(intent)
            }
        }
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        if (mode != AppOpsManager.MODE_ALLOWED) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            Toast.makeText(this, "Grant Usage Access to trace culprits", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleService() {
        val intent = Intent(this, WatchdogService::class.java)
        if (!isServiceRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            binding.tvStatus.text = "Service Status: Active & Monitoring"
            binding.tvStatus.setTextColor(0xFF2E7D32.toInt())
            binding.btnToggleWatchdog.text = "Stop Watchdog Daemon"
            isServiceRunning = true
        } else {
            stopService(intent)
            binding.tvStatus.text = "Service Status: Inactive"
            binding.tvStatus.setTextColor(0xFFD32F2F.toInt())
            binding.btnToggleWatchdog.text = "Start Watchdog Daemon"
            isServiceRunning = false
        }
    }

    private fun executeManualSweep() {
        val targets = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )
        var removedCount = 0
        targets.forEach { target ->
            if (target.exists()) {
                target.walkTopDown().forEach { file ->
                    if (file.name.equals(".nomedia", ignoreCase = true)) {
                        file.deleteRecursively()
                        removedCount++
                        binding.tvAuditLogs.append("\nManual Purge: " + file.absolutePath)
                    }
                }
                SentinelObserver.triggerMediaScanner(this, target)
            }
        }
        Toast.makeText(this, "Sweep complete: $removedCount items purged", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        unregisterReceiver(logReceiver)
        super.onDestroy()
    }
}
