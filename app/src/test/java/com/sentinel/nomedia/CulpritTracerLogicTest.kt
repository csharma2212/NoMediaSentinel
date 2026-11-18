package com.sentinel.nomedia

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class CulpritTracerLogicTest {

    @Test
    fun testNoMediaRecognition() {
        val fileName = ".nomedia"
        val isMatch = fileName.equals(".nomedia", ignoreCase = true)
        assertThat(isMatch).isTrue()

        val uppercase = ".NOMEDIA"
        assertThat(uppercase.equals(".nomedia", ignoreCase = true)).isTrue()
    }

    @Test
    fun testFilePurgeSimulation() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "SentinelUnitTest")
        tempDir.mkdirs()
        val dummyNoMedia = File(tempDir, ".nomedia")
        dummyNoMedia.writeText("test_content")
        assertThat(dummyNoMedia.exists()).isTrue()
        dummyNoMedia.deleteRecursively()
        assertThat(dummyNoMedia.exists()).isFalse()
        tempDir.deleteRecursively()
    }

    @Test
    fun testNestedNoMediaPurge() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "NestedSentinelTest")
        val subDirA = File(baseDir, "TripPhotos")
        val subDirB = File(subDirA, "NestedCamera")
        subDirB.mkdirs()

        val nomediaA = File(subDirA, ".nomedia")
        val nomediaB = File(subDirB, ".nomedia")
        nomediaA.writeText("")
        nomediaB.writeText("")

        assertThat(nomediaA.exists()).isTrue()
        assertThat(nomediaB.exists()).isTrue()

        // Recursive purge simulation
        fun purgeDir(dir: File): Int {
            var count = 0
            dir.listFiles()?.forEach { file ->
                if (file.name.equals(".nomedia", ignoreCase = true)) {
                    if (file.deleteRecursively()) count++
                } else if (file.isDirectory) {
                    count += purgeDir(file)
                }
            }
            return count
        }

        val purged = purgeDir(baseDir)
        assertThat(purged).isEqualTo(2)
        assertThat(nomediaA.exists()).isFalse()
        assertThat(nomediaB.exists()).isFalse()

        baseDir.deleteRecursively()
    }

    @Test
    fun testMediaExtensionMatching() {
        val validMedia = listOf("photo.jpg", "IMAGE.PNG", "video.mp4", "clip.mkv", "raw.heic")
        val invalidMedia = listOf("document.pdf", "app.apk", ".nomedia", "archive.zip")

        fun isMedia(filename: String): Boolean {
            val lower = filename.lowercase()
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                    lower.endsWith(".png") || lower.endsWith(".webp") ||
                    lower.endsWith(".heic") || lower.endsWith(".gif") ||
                    lower.endsWith(".mp4") || lower.endsWith(".mkv")
        }

        for (v in validMedia) {
            assertThat(isMedia(v)).isTrue()
        }
        for (inv in invalidMedia) {
            assertThat(isMedia(inv)).isFalse()
        }
    }
}
