package com.sentinel.nomedia.detector

import android.content.Context
import android.os.FileObserver
import android.util.Log
import com.sentinel.nomedia.remediation.MediaScanHelper
import java.io.File

/**
 * SentinelObserver maintains a recursive tree of Linux inotify FileObservers.
 * It monitors media directories in real time, intercepts .nomedia creation events,
 * immediately purges rogue files (<100ms), traces the culprit package, and triggers
 * zero-reboot MediaStore re-indexing.
 */
class SentinelObserver(
    private val context: Context,
    private val rootDirectory: File,
    private val onBreach: (culprit: CulpritInfo, file: File) -> Unit,
    private val onLog: ((String) -> Unit)? = null
) {
    private val activeObservers = mutableMapOf<String, FileObserver>()
    private var isWatching = false

    companion object {
        private const val TAG = "SentinelObserver"
        const val WATCH_EVENTS = FileObserver.CREATE or FileObserver.MOVED_TO

        fun triggerMediaScanner(context: Context, directory: File) {
            MediaScanHelper.scanDirectoryRecursively(context, directory)
        }
    }

    fun startWatching() {
        synchronized(activeObservers) {
            if (isWatching) return
            isWatching = true
            watchDirectoryRecursive(rootDirectory)
            onLog?.invoke("SentinelObserver active on: ${rootDirectory.absolutePath} (recursive)")
        }
    }

    fun stopWatching() {
        synchronized(activeObservers) {
            isWatching = false
            for (observer in activeObservers.values) {
                observer.stopWatching()
            }
            activeObservers.clear()
            onLog?.invoke("SentinelObserver stopped for: ${rootDirectory.name}")
        }
    }

    private fun watchDirectoryRecursive(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        val path = dir.absolutePath
        if (activeObservers.containsKey(path)) return

        val observer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            object : FileObserver(dir, WATCH_EVENTS) {
                override fun onEvent(event: Int, fileName: String?) {
                    if (fileName == null) return
                    val targetFile = File(path, fileName)

                    if (fileName.equals(".nomedia", ignoreCase = true)) {
                        handleBreach(targetFile, dir)
                    } else if (targetFile.isDirectory && !fileName.startsWith(".")) {
                        synchronized(activeObservers) {
                            if (isWatching) {
                                watchDirectoryRecursive(targetFile)
                            }
                        }
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(path, WATCH_EVENTS) {
                override fun onEvent(event: Int, fileName: String?) {
                    if (fileName == null) return
                    val targetFile = File(path, fileName)

                    if (fileName.equals(".nomedia", ignoreCase = true)) {
                        handleBreach(targetFile, dir)
                    } else if (targetFile.isDirectory && !fileName.startsWith(".")) {
                        synchronized(activeObservers) {
                            if (isWatching) {
                                watchDirectoryRecursive(targetFile)
                            }
                        }
                    }
                }
            }
        }

        try {
            observer.startWatching()
            activeObservers[path] = observer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to watch path: $path", e)
        }

        // Recursively watch existing subdirectories
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory && !child.name.startsWith(".")) {
                watchDirectoryRecursive(child)
            }
        }
    }

    private fun handleBreach(rogueFile: File, parentDir: File) {
        val eventTime = System.currentTimeMillis()
        onLog?.invoke("BREACH DETECTED: Rogue .nomedia at ${rogueFile.absolutePath}")

        // 1. Trace the culprit immediately
        val culprit = CulpritTracer.identifyCulprit(context, eventTime)
        onLog?.invoke("CULPRIT IDENTIFIED: ${culprit.appLabel} (${culprit.packageName}) [${culprit.confidence}]")

        // 2. Sub-100ms Autonomous Purge
        val deleted = rogueFile.deleteRecursively()
        onLog?.invoke("AUTONOMOUS REMEDIATION: Purge status = $deleted")

        // 3. Zero-Reboot Re-indexing
        triggerMediaScanner(context, parentDir)
        onLog?.invoke("ZERO-REBOOT SCAN: Dispatched MediaStore re-index for ${parentDir.name}")

        // 4. Fire breach callback
        onBreach(culprit, rogueFile)
    }
}
