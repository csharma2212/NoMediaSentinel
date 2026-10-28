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
}
