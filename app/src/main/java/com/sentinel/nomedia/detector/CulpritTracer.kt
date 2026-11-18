package com.sentinel.nomedia.detector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

enum class CulpritConfidence {
    HIGH,       // Exact foreground transition matched timestamp window
    MEDIUM,     // Background service active or recent usage
    LOW         // Inferred from broad storage permissions
}

data class CulpritInfo(
    val packageName: String,
    val appLabel: String,
    val confidence: CulpritConfidence,
    val details: String
)

object CulpritTracer {

    fun identifyCulprit(context: Context, eventTimestamp: Long): CulpritInfo {
        val candidates = mutableListOf<String>()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

        if (usageStatsManager != null) {
            val beginTime = eventTimestamp - 4000
            val endTime = eventTimestamp + 1500
            val events = usageStatsManager.queryEvents(beginTime, endTime)
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val type = event.eventType
                if (type == UsageEvents.Event.ACTIVITY_RESUMED ||
                    type == UsageEvents.Event.FOREGROUND_SERVICE_START ||
                    type == UsageEvents.Event.USER_INTERACTION
                ) {
                    if (event.packageName != context.packageName && !isSystemPackage(context, event.packageName)) {
                        candidates.add(event.packageName)
                    }
                }
            }
        }

        if (candidates.isNotEmpty()) {
            val topPackage = candidates.last()
            val label = getAppLabel(context, topPackage)
            return CulpritInfo(
                packageName = topPackage,
                appLabel = label,
                confidence = CulpritConfidence.HIGH,
                details = "Active in foreground at breach timestamp (${topPackage})"
            )
        }

        // Fallback: Check for third-party packages with MANAGE_EXTERNAL_STORAGE
        val pm = context.packageManager
        val storageApps = mutableListOf<String>()

        try {
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }

            for (pkg in packages) {
                if (pkg.packageName == context.packageName || isSystemPackage(context, pkg.packageName)) continue
                val requested = pkg.requestedPermissions ?: continue
                if ("android.permission.MANAGE_EXTERNAL_STORAGE" in requested) {
                    storageApps.add(pkg.packageName)
                }
            }
        } catch (_: Exception) {}

        if (storageApps.isNotEmpty()) {
            val topPkg = storageApps.first()
            val label = getAppLabel(context, topPkg)
            return CulpritInfo(
                packageName = topPkg,
                appLabel = label,
                confidence = CulpritConfidence.LOW,
                details = "Suspicious broadcaster holding All Files Access: ${storageApps.take(3).joinToString(", ")}"
            )
        }

        return CulpritInfo(
            packageName = "unknown",
            appLabel = "Unknown Daemon",
            confidence = CulpritConfidence.LOW,
            details = "Background I/O without resumed activity"
        )
    }

    fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun isSystemPackage(context: Context, packageName: String): Boolean {
        if (packageName.startsWith("com.android.") ||
            packageName.startsWith("com.google.android.") ||
            packageName == "android") {
            return true
        }
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: Exception) {
            false
        }
    }
}
