package com.sentinel.nomedia.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sentinel.nomedia.R
import com.sentinel.nomedia.service.WatchdogService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnPermissions: Button
    private lateinit var btnSweep: Button
    private lateinit var btnToggleWatchdog: Button
    private lateinit var tvAuditLogs: TextView

    private var isWatchdogRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnPermissions = findViewById(R.id.btnPermissions)
        btnSweep = findViewById(R.id.btnSweep)
        btnToggleWatchdog = findViewById(R.id.btnToggleWatchdog)
        tvAuditLogs = findViewById(R.id.tvAuditLogs)

        btnPermissions.setOnClickListener {
            handlePermissionsFlow()
        }

        btnSweep.setOnClickListener {
            if (!hasAllFilesAccess()) {
                Toast.makeText(this, "Grant All Files Access first", Toast.LENGTH_SHORT).show()
                handlePermissionsFlow()
            } else {
                runManualSweep()
            }
        }

        btnToggleWatchdog.setOnClickListener {
            toggleWatchdogService()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun handlePermissionsFlow() {
        if (!hasAllFilesAccess()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    Toast.makeText(this, "Enable 'Allow access to manage all files'", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(fallbackIntent)
                }
            }
            return
        }

        if (!hasUsageAccess()) {
            val usageIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(usageIntent)
            Toast.makeText(this, "Find 'NoMedia Sentinel' and toggle 'Permit usage access'", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "All required permissions are already granted!", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun updateStatus() {
        val storageOk = hasAllFilesAccess()
        val usageOk = hasUsageAccess()

        when {
            storageOk && usageOk -> {
                tvStatus.text = "Status: Fully Authorized & Protected"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                btnPermissions.text = "Permissions Granted (Active)"
                btnPermissions.isEnabled = false
            }
            storageOk && !usageOk -> {
                tvStatus.text = "Status: Storage OK | Usage Access Needed"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                btnPermissions.text = "Grant Usage Stats Access"
                btnPermissions.isEnabled = true
            }
            else -> {
                tvStatus.text = "Status: Storage Permission Required"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                btnPermissions.text = "Grant Storage Permissions"
                btnPermissions.isEnabled = true
            }
        }
    }

    private fun runManualSweep() {
        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logBuilder = StringBuilder()
        logBuilder.append("[$timeStamp] Starting media directories sweep...\n")

        val targetDirs = listOf(
            File(Environment.getExternalStorageDirectory(), "DCIM"),
            File(Environment.getExternalStorageDirectory(), "Pictures"),
            File(Environment.getExternalStorageDirectory(), "Download")
        )

        var purgedCount = 0

        for (dir in targetDirs) {
            if (dir.exists()) {
                val found = scanAndPurge(dir, logBuilder)
                purgedCount += found
            }
        }

        if (purgedCount == 0) {
            logBuilder.append("[$timeStamp] Clean! No rogue .nomedia files found.\n")
        } else {
            logBuilder.append("[$timeStamp] Completed. Purged $purgedCount rogue file(s).\n")
        }

        tvAuditLogs.text = logBuilder.toString()
    }

    private fun scanAndPurge(dir: File, log: StringBuilder): Int {
        var count = 0
        val files = dir.listFiles() ?: return 0

        for (file in files) {
            if (file.name.equals(".nomedia", ignoreCase = true)) {
                val deleted = file.delete()
                val status = if (deleted) "DELETED & PURGED" else "DELETE FAILED"
                log.append("  [!] Found: ${file.absolutePath} -> $status\n")
                if (deleted) count++
            } else if (file.isDirectory && !file.name.startsWith(".")) {
                count += scanAndPurge(file, log)
            }
        }
        return count
    }

    private fun toggleWatchdogService() {
        val serviceIntent = Intent(this, WatchdogService::class.java)
        if (!isWatchdogRunning) {
            ContextCompat.startForegroundService(this, serviceIntent)
            isWatchdogRunning = true
            btnToggleWatchdog.text = "Stop Background Guard"
            Toast.makeText(this, "Watchdog guard started", Toast.LENGTH_SHORT).show()
        } else {
            stopService(serviceIntent)
            isWatchdogRunning = false
            btnToggleWatchdog.text = "Start Background Guard"
            Toast.makeText(this, "Watchdog guard stopped", Toast.LENGTH_SHORT).show()
        }
    }
}