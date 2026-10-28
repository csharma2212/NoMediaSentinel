package com.sentinel.nomedia.detector

import android.content.Context
import android.media.MediaScannerConnection
import android.os.FileObserver
import java.io.File

class SentinelObserver(
    private val context: Context,
    private val targetDirectory: File,
    private val onLog: (String) -> Unit
) : FileObserver(targetDirectory, CREATE or MOVED_TO) {

    override fun onEvent(event: Int, path: String?) {
        if (path == null) return
        if (path.equals(".nomedia", ignoreCase = true)) {
            val eventTimestamp = System.currentTimeMillis()
            val rogueFile = File(targetDirectory, path)
            onLog("BREACH: .nomedia detected at: " + rogueFile.absolutePath)
            val culprit = CulpritTracer.identifyCulprit(context, eventTimestamp)
            onLog("CULPRIT TRACED: " + culprit)
            val deleted = rogueFile.deleteRecursively()
            onLog("REMEDIATION: Deleted rogue target: " + deleted)
            triggerMediaScanner(context, targetDirectory)
        }
    }

    companion object {
        fun triggerMediaScanner(context: Context, directory: File) {
            val filesToScan = directory.listFiles() ?: return
            val paths = filesToScan.map { it.absolutePath }.toTypedArray()
            MediaScannerConnection.scanFile(context, paths, null, null)
        }
    }
}
