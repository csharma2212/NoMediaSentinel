package com.sentinel.nomedia.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.sentinel.nomedia.R
import com.sentinel.nomedia.intelligence.AppThreatReport
import com.sentinel.nomedia.intelligence.ThreatAuditor
import com.sentinel.nomedia.remediation.StorageRemediator
import com.sentinel.nomedia.service.WatchdogService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvPermissionsDetails: TextView
    private lateinit var btnPermissions: MaterialButton
    private lateinit var btnSweep: MaterialButton
    private lateinit var btnToggleWatchdog: MaterialButton
    private lateinit var btnAuditThreats: MaterialButton
    private lateinit var progressBar: ProgressBar

    private lateinit var tabAuditLogs: TextView
    private lateinit var tabAppThreats: TextView
    private lateinit var containerLogs: ScrollView
    private lateinit var tvAuditLogs: TextView
    private lateinit var rvThreats: RecyclerView

    private lateinit var threatAdapter: ThreatAdapter
    private val threatReports = mutableListOf<AppThreatReport>()

    private val logHistory = StringBuilder()

    companion object {
        private const val PERMISSION_REQ_NOTIFICATIONS = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        setupThreatRecyclerView()
        setupWatchdogLogListener()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateWatchdogButtonState()
    }

    override fun onDestroy() {
        WatchdogService.setLogListener(null)
        super.onDestroy()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvPermissionsDetails = findViewById(R.id.tvPermissionsDetails)
        btnPermissions = findViewById(R.id.btnPermissions)
        btnSweep = findViewById(R.id.btnSweep)
        btnToggleWatchdog = findViewById(R.id.btnToggleWatchdog)
        btnAuditThreats = findViewById(R.id.btnAuditThreats)
        progressBar = findViewById(R.id.progressBar)

        tabAuditLogs = findViewById(R.id.tabAuditLogs)
        tabAppThreats = findViewById(R.id.tabAppThreats)
        containerLogs = findViewById(R.id.containerLogs)
        tvAuditLogs = findViewById(R.id.tvAuditLogs)
        rvThreats = findViewById(R.id.rvThreats)
    }

    private fun setupListeners() {
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

        btnAuditThreats.setOnClickListener {
            runThreatIntelligenceAudit()
        }

        tabAuditLogs.setOnClickListener {
            showTab(isLogs = true)
        }

        tabAppThreats.setOnClickListener {
            showTab(isLogs = false)
        }
    }

    private fun setupThreatRecyclerView() {
        threatAdapter = ThreatAdapter(this, threatReports)
        rvThreats.layoutManager = LinearLayoutManager(this)
        rvThreats.adapter = threatAdapter
    }

    private fun showTab(isLogs: Boolean) {
        if (isLogs) {
            tabAuditLogs.setTextColor(Color.parseColor("#1565C0"))
            tabAppThreats.setTextColor(Color.parseColor("#6C757D"))
            containerLogs.visibility = View.VISIBLE
            rvThreats.visibility = View.GONE
        } else {
            tabAuditLogs.setTextColor(Color.parseColor("#6C757D"))
            tabAppThreats.setTextColor(Color.parseColor("#1565C0"))
            containerLogs.visibility = View.GONE
            rvThreats.visibility = View.VISIBLE
        }
    }

    private fun setupWatchdogLogListener() {
        for (log in WatchdogService.eventLogs) {
            appendLog(log, notifyScroll = false)
        }

        WatchdogService.setLogListener { logMessage ->
            runOnUiThread {
                appendLog(logMessage, notifyScroll = true)
            }
        }
    }

    private fun appendLog(message: String, notifyScroll: Boolean = true) {
        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logHistory.append("[$timeStamp] $message\n")
        tvAuditLogs.text = logHistory.toString()

        if (notifyScroll && containerLogs.visibility == View.VISIBLE) {
            containerLogs.post {
                containerLogs.fullScroll(View.FOCUS_DOWN)
            }
        }
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

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
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
                } catch (_: Exception) {
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

        if (!hasNotificationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQ_NOTIFICATIONS
                )
            }
            return
        }

        Toast.makeText(this, "All required permissions are already granted!", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun updateStatus() {
        val storageOk = hasAllFilesAccess()
        val usageOk = hasUsageAccess()
        val notifOk = hasNotificationPermission()

        val details = buildString {
            append("Storage: ${if (storageOk) "Granted" else "Missing"} | ")
            append("Usage Stats: ${if (usageOk) "Granted" else "Missing"} | ")
            append("Notifications: ${if (notifOk) "Granted" else "Missing"}")
        }
        tvPermissionsDetails.text = details

        when {
            storageOk && usageOk && notifOk -> {
                tvStatus.text = "Status: Fully Authorized & Protected"
                tvStatus.setTextColor(Color.parseColor("#2E7D32"))
                btnPermissions.visibility = View.GONE
            }
            storageOk && !usageOk -> {
                tvStatus.text = "Status: Storage OK | Grant Usage Stats Access"
                tvStatus.setTextColor(Color.parseColor("#E65100"))
                btnPermissions.visibility = View.VISIBLE
                btnPermissions.text = "Grant Usage Stats Access"
            }
            !storageOk -> {
                tvStatus.text = "Status: All Files Access Required"
                tvStatus.setTextColor(Color.parseColor("#C62828"))
                btnPermissions.visibility = View.VISIBLE
                btnPermissions.text = "Grant All Files Access"
            }
            else -> {
                tvStatus.text = "Status: Permissions Needed"
                tvStatus.setTextColor(Color.parseColor("#E65100"))
                btnPermissions.visibility = View.VISIBLE
                btnPermissions.text = "Grant Notifications Permission"
            }
        }
    }

    private fun updateWatchdogButtonState() {
        if (WatchdogService.isRunning) {
            btnToggleWatchdog.text = "Stop Background Guard"
            btnToggleWatchdog.setTextColor(Color.parseColor("#C62828"))
        } else {
            btnToggleWatchdog.text = "Start Background Guard"
            btnToggleWatchdog.setTextColor(Color.parseColor("#1565C0"))
        }
    }

    private fun toggleWatchdogService() {
        if (!hasAllFilesAccess()) {
            Toast.makeText(this, "Grant All Files Access before starting the guard", Toast.LENGTH_SHORT).show()
            handlePermissionsFlow()
            return
        }

        val serviceIntent = Intent(this, WatchdogService::class.java)
        if (!WatchdogService.isRunning) {
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Watchdog guard started", Toast.LENGTH_SHORT).show()
        } else {
            stopService(serviceIntent)
            Toast.makeText(this, "Watchdog guard stopped", Toast.LENGTH_SHORT).show()
        }

        // Small delay to let service state settle
        tvStatus.postDelayed({
            updateWatchdogButtonState()
        }, 300)
    }

    private fun runManualSweep() {
        showTab(isLogs = true)
        progressBar.visibility = View.VISIBLE
        btnSweep.isEnabled = false
        appendLog("Initiating recursive storage sweep across media directories...")

        lifecycleScope.launch(Dispatchers.IO) {
            val result = StorageRemediator.executeFullRemediation(this@MainActivity) { progressMsg ->
                runOnUiThread {
                    appendLog("  $progressMsg")
                }
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                btnSweep.isEnabled = true

                if (result.purgedFiles.isEmpty()) {
                    appendLog("CLEAN: No rogue .nomedia files found across target directories.")
                } else {
                    appendLog("REMEDIATION COMPLETE: Purged ${result.purgedFiles.size} rogue .nomedia file(s).")
                    appendLog("ZERO-REBOOT: Dispatched MediaScanner to ${result.scannedDirectories.size} directory trees.")
                    Toast.makeText(
                        this@MainActivity,
                        "Purged ${result.purgedFiles.size} .nomedia files & re-indexed!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun runThreatIntelligenceAudit() {
        progressBar.visibility = View.VISIBLE
        btnAuditThreats.isEnabled = false
        appendLog("Starting threat intelligence audit across installed storage broadcasters...")

        lifecycleScope.launch(Dispatchers.IO) {
            val reports = ThreatAuditor.auditInstalledApps(this@MainActivity) { current, total, appName ->
                runOnUiThread {
                    appendLog("  Auditing ($current/$total): $appName")
                }
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                btnAuditThreats.isEnabled = true

                threatReports.clear()
                threatReports.addAll(reports)
                threatAdapter.updateData(threatReports)

                tabAppThreats.text = "Threat Intelligence (${threatReports.size})"
                showTab(isLogs = false)

                val highCount = threatReports.count { it.threatLevel == com.sentinel.nomedia.intelligence.ThreatLevel.HIGH }
                appendLog("AUDIT COMPLETE: Analyzed ${threatReports.size} apps. $highCount flagged as High Risk.")
                Toast.makeText(
                    this@MainActivity,
                    "Audit completed: ${threatReports.size} storage apps analyzed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
