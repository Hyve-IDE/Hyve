package com.hyve.mod

import com.hyve.common.settings.HytalePathDetector
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class HytalePathDetectorTest {

    @Test
    fun `isValidInstallPath returns false for nonexistent path`() {
        assertFalse(HytalePathDetector.isValidInstallPath(Paths.get("/nonexistent/path")))
    }

    @Test
    fun `isValidInstallPath returns false for empty directory`() {
        val tempDir = kotlin.io.path.createTempDirectory("hytale-test")
        try {
            assertFalse(HytalePathDetector.isValidInstallPath(tempDir))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `isValidInstallPath returns true when Server jar exists`() {
        val tempDir = kotlin.io.path.createTempDirectory("hytale-test")
        try {
            val serverDir = tempDir.resolve("Server").toFile()
            serverDir.mkdirs()
            java.io.File(serverDir, "HytaleServer.jar").createNewFile()

            assert(HytalePathDetector.isValidInstallPath(tempDir))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
