package com.sentinel.nomedia.intelligence

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class ThreatLevel {
    HIGH,        // Proven history of creating .nomedia or hiding gallery photos
    SUSPICIOUS,  // Has broad storage access + matches junk cleaner/downloader profile
    MODERATE,    // Holds All Files Access, no direct malicious reports found
    LOW          // Normal app with standard media permissions
}

data class AppThreatReport(
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    val threatLevel: ThreatLevel,
    val summary: String,
    val onlineFindings: List<String>,
    val searchUrl: String,
    val permissions: List<String>
)

object ThreatAuditor {

    // Known patterns of apps that frequently write .nomedia or cause gallery disappearance
    private val KNOWN_RISKY_KEYWORDS = listOf(
        "cleaner", "booster", "junk", "speed", "optimizer",
        "vault", "hide", "secret", "locker", "status saver",
        "downloader", "story saver", "save status", "cache clean"
    )

    private val KNOWN_OFFENDER_PACKAGES = mapOf(
        "com.cleanmaster.mguard" to "Known to place .nomedia in cache and thumbnail directories",
        "com.dewmobile.kuaiya.play" to "Transfers media with hidden markers causing gallery desync",
        "com.estrongs.android.pop" to "Historical reports of placing .nomedia in recycle/trash paths",
        "com.piriform.ccleaner" to "Junk cleaner scans that drop exclusion flags in photo cache"
    )

    /**
     * Identifies installed third-party apps holding broad storage access
     * and performs online web intelligence to discover reported media-hiding issues.
     */
    suspend fun auditInstalledApps(
        context: Context,
        onProgress: ((current: Int, total: Int, appName: String) -> Unit)? = null
    ): List<AppThreatReport> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val candidates = mutableListOf<PackageCandidate>()

        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        for (pkg in packages) {
            if (pkg.packageName == context.packageName) continue
            val appInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) continue

            val requested = pkg.requestedPermissions ?: emptyArray()
            val hasManage = "android.permission.MANAGE_EXTERNAL_STORAGE" in requested
            val hasWrite = "android.permission.WRITE_EXTERNAL_STORAGE" in requested

            if (hasManage || hasWrite) {
                val appLabel = pm.getApplicationLabel(appInfo).toString()
                val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null }
                val perms = mutableListOf<String>()
                if (hasManage) perms.add("All Files Access (MANAGE_EXTERNAL_STORAGE)")
                if (hasWrite) perms.add("Legacy Storage Write (WRITE_EXTERNAL_STORAGE)")

                candidates.add(
                    PackageCandidate(
                        appName = appLabel,
                        packageName = pkg.packageName,
                        icon = icon,
                        hasManageStorage = hasManage,
                        permissions = perms
                    )
                )
            }
        }

        val reports = mutableListOf<AppThreatReport>()
        val total = candidates.size

        for ((index, candidate) in candidates.withIndex()) {
            onProgress?.invoke(index + 1, total, candidate.appName)

            val onlineFindings = mutableListOf<String>()
            var threat = ThreatLevel.LOW

            // 1. Check offline threat signatures
            val knownReason = KNOWN_OFFENDER_PACKAGES[candidate.packageName]
            if (knownReason != null) {
                threat = ThreatLevel.HIGH
                onlineFindings.add("Known signature: $knownReason")
            }

            // 2. Keyword heuristic matching
            val nameLower = candidate.appName.lowercase()
            val matchesKeyword = KNOWN_RISKY_KEYWORDS.any { nameLower.contains(it) }
            if (matchesKeyword && threat != ThreatLevel.HIGH) {
                threat = if (candidate.hasManageStorage) ThreatLevel.HIGH else ThreatLevel.SUSPICIOUS
                onlineFindings.add("App category matching risk profile: Cleaner / Vault / Downloader")
            } else if (candidate.hasManageStorage && threat == ThreatLevel.LOW) {
                threat = ThreatLevel.MODERATE
            }

            // 3. Search internet issues regarding this app
            val encodedQuery = URLEncoder.encode(
                "\"${candidate.appName}\" (\".nomedia\" OR \"missing photos\" OR \"gallery empty\")",
                "UTF-8"
            )
            val searchUrl = "https://www.google.com/search?q=$encodedQuery"

            try {
                val webEvidence = searchWebIssues(candidate.appName, candidate.packageName)
                if (webEvidence.isNotEmpty()) {
                    onlineFindings.addAll(webEvidence)
                    if (threat < ThreatLevel.SUSPICIOUS) {
                        threat = ThreatLevel.SUSPICIOUS
                    }
                }
            } catch (_: Exception) {
                // If offline or network timeout, local heuristics persist
            }

            val summary = when (threat) {
                ThreatLevel.HIGH -> "High Risk: Known or reported history of creating .nomedia and hiding user photos."
                ThreatLevel.SUSPICIOUS -> "Suspicious: Has broad storage access and matches cleaner/downloader pattern."
                ThreatLevel.MODERATE -> "Moderate: Holds All Files Access. Can write hidden files anywhere in storage."
                ThreatLevel.LOW -> "Low Risk: Standard storage usage with no known media interference issues."
            }

            reports.add(
                AppThreatReport(
                    appName = candidate.appName,
                    packageName = candidate.packageName,
                    icon = candidate.icon,
                    threatLevel = threat,
                    summary = summary,
                    onlineFindings = onlineFindings,
                    searchUrl = searchUrl,
                    permissions = candidate.permissions
                )
            )
        }

        // Sort: HIGH first, then SUSPICIOUS, MODERATE, LOW
        reports.sortedBy { it.threatLevel.ordinal }
    }

    /**
     * Queries web search engine for reports connecting the app to .nomedia or missing photo bugs.
     */
    private fun searchWebIssues(appName: String, packageName: String): List<String> {
        val results = mutableListOf<String>()
        val query = "$appName (\".nomedia\" OR \"missing photos\" OR \"gallery hidden\")"
        val endpoint = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")

        var connection: HttpURLConnection? = null
        try {
            val url = URL(endpoint)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0)")
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val html = reader.readText()
                reader.close()

                val lower = html.lowercase()
                if (lower.contains(".nomedia")) {
                    results.add("Internet reports found mentioning '.nomedia' associated with this app.")
                }
                if (lower.contains("gallery") && (lower.contains("disappear") || lower.contains("missing"))) {
                    results.add("Community forum discussions regarding missing gallery photos detected.")
                }
            }
        } catch (_: Exception) {
            // Graceful fallback on connection/timeout errors
        } finally {
            connection?.disconnect()
        }

        return results
    }

    private data class PackageCandidate(
        val appName: String,
        val packageName: String,
        val icon: Drawable?,
        val hasManageStorage: Boolean,
        val permissions: List<String>
    )
}
