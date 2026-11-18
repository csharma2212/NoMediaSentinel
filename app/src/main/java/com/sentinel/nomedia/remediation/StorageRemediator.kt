package com.sentinel.nomedia.remediation

import android.content.Context
import android.os.Environment
import java.io.File

data class SweepResult(
    val purgedFiles: List<File>,
    val scannedDirectories: List<File>,
    val elapsedTimeMs: Long
)

object StorageRemediator {

    val TARGET_ROOT_DIRS = listOf(
        File(Environment.getExternalStorageDirectory(), "DCIM"),
        File(Environment.getExternalStorageDirectory(), "Pictures"),
        File(Environment.getExternalStorageDirectory(), "Download"),
        File(Environment.getExternalStorageDirectory(), "Movies")
    )

    /**
     * Executes a full recursive sweep across public media folders,
     * permanently purges rogue .nomedia files, and dispatches zero-reboot re-indexing.
     */
    fun executeFullRemediation(
        context: Context,
        onProgress: ((message: String) -> Unit)? = null
    ): SweepResult {
        val startTime = System.currentTimeMillis()
        val purgedList = mutableListOf<File>()
        val affectedDirs = mutableSetOf<File>()

        for (rootDir in TARGET_ROOT_DIRS) {
            if (rootDir.exists()) {
                onProgress?.invoke("Scanning: ${rootDir.name}...")
                scanAndPurgeRecursive(rootDir, purgedList, affectedDirs, onProgress)
            }
        }

        // Trigger zero-reboot re-indexing for every directory freed from .nomedia
        for (dir in affectedDirs) {
            onProgress?.invoke("Re-indexing MediaStore for: ${dir.name} (Zero-Reboot)")
            MediaScanHelper.scanDirectoryRecursively(context, dir)
        }

        val elapsed = System.currentTimeMillis() - startTime
        return SweepResult(
            purgedFiles = purgedList,
            scannedDirectories = affectedDirs.toList(),
            elapsedTimeMs = elapsed
        )
    }

    private fun scanAndPurgeRecursive(
        dir: File,
        purgedList: MutableList<File>,
        affectedDirs: MutableSet<File>,
        onProgress: ((String) -> Unit)?
    ) {
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.name.equals(".nomedia", ignoreCase = true)) {
                val deleted = file.deleteRecursively()
                if (deleted) {
                    purgedList.add(file)
                    affectedDirs.add(dir)
                    onProgress?.invoke("PURGED rogue .nomedia at: ${file.absolutePath}")
                }
            } else if (file.isDirectory && !file.name.startsWith(".")) {
                scanAndPurgeRecursive(file, purgedList, affectedDirs, onProgress)
            }
        }
    }
}
