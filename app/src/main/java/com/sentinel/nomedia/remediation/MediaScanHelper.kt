package com.sentinel.nomedia.remediation

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import java.io.File

object MediaScanHelper {
    private const val TAG = "MediaScanHelper"

    /**
     * Dispatches MediaScannerConnection.scanFile() for all files inside the specified directory
     * (and its subdirectories) to restore immediate visibility in Gallery apps without rebooting.
     */
    fun scanDirectoryRecursively(context: Context, directory: File, onComplete: ((scannedCount: Int) -> Unit)? = null) {
        if (!directory.exists()) {
            onComplete?.invoke(0)
            return
        }

        val allFiles = mutableListOf<String>()
        collectMediaFiles(directory, allFiles)

        if (allFiles.isEmpty()) {
            // Even if empty of recognizable media files, scan the directory itself
            MediaScannerConnection.scanFile(context, arrayOf(directory.absolutePath), null) { path, uri ->
                Log.d(TAG, "Scanned directory: $path -> $uri")
                onComplete?.invoke(1)
            }
            return
        }

        var completed = 0
        val total = allFiles.size
        MediaScannerConnection.scanFile(
            context,
            allFiles.toTypedArray(),
            null
        ) { path, uri ->
            Log.d(TAG, "Indexed: $path -> $uri")
            completed++
            if (completed == total) {
                onComplete?.invoke(total)
            }
        }
    }

    private fun collectMediaFiles(dir: File, accumulator: MutableList<String>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    collectMediaFiles(file, accumulator)
                }
            } else {
                val name = file.name.lowercase()
                if (isMediaExtension(name)) {
                    accumulator.add(file.absolutePath)
                }
            }
        }
    }

    private fun isMediaExtension(filename: String): Boolean {
        return filename.endsWith(".jpg") || filename.endsWith(".jpeg") ||
                filename.endsWith(".png") || filename.endsWith(".webp") ||
                filename.endsWith(".heic") || filename.endsWith(".gif") ||
                filename.endsWith(".mp4") || filename.endsWith(".mkv") ||
                filename.endsWith(".mov") || filename.endsWith(".3gp")
    }
}
