package com.sentinel.nomedia.detector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager

object CulpritTracer {
    fun identifyCulprit(context: Context, eventTimestamp: Long): String {
        val candidates = mutableListOf<String>()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager != null) {
            val beginTime = eventTimestamp - 4000
            val endTime = eventTimestamp + 1000
            val events = usageStatsManager.queryEvents(beginTime, endTime)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.FOREGROUND_SERVICE_START) {
                    if (event.packageName != context.packageName) {
                        candidates.add(event.packageName)
                    }
                }
            }
        }
        if (candidates.isNotEmpty()) {
            return candidates.last()
        }
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        for (pkg in packages) {
            if (pkg.packageName == context.packageName) continue
            val requested = pkg.requestedPermissions ?: continue
            if ("android.permission.MANAGE_EXTERNAL_STORAGE" in requested) {
                candidates.add(pkg.packageName)
            }
        }
        if (candidates.isNotEmpty()) {
            return "Suspicious Storage-Broadcaster: " + candidates.joinToString(", ")
        }
        return "Unknown (System Daemon or Background I/O without Resumed Activity)"
    }
}
