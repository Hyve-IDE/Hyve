package com.hyve.mod

import com.hyve.common.settings.HytaleInstallPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class HytaleInstallPathOverrideTest {

    @AfterEach
    fun cleanup() {
        HytaleInstallPath.clearOverride(HytaleInstallPath.KEY_ASSETS_ZIP)
        HytaleInstallPath.clearOverride(HytaleInstallPath.KEY_SERVER_JAR)
        HytaleInstallPath.clearOverride(HytaleInstallPath.KEY_SERVER_MODS)
    }

    // --- Override API ---

    @Test
    fun `getOverride returns null when not set`() {
        assertNull(HytaleInstallPath.getOverride(HytaleInstallPath.KEY_ASSETS_ZIP))
    }

    @Test
    fun `setOverride and getOverride round-trip`() {
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_ASSETS_ZIP, "/custom/Assets.zip")
        assertEquals("/custom/Assets.zip", HytaleInstallPath.getOverride(HytaleInstallPath.KEY_ASSETS_ZIP))
    }

    @Test
    fun `clearOverride removes the override`() {
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_SERVER_JAR, "/custom/Server.jar")
        HytaleInstallPath.clearOverride(HytaleInstallPath.KEY_SERVER_JAR)
        assertNull(HytaleInstallPath.getOverride(HytaleInstallPath.KEY_SERVER_JAR))
    }

    @Test
    fun `hasOverride returns false when not set`() {
        assertFalse(HytaleInstallPath.hasOverride(HytaleInstallPath.KEY_SERVER_MODS))
    }

    @Test
    fun `hasOverride returns true when set`() {
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_SERVER_MODS, "/custom/mods")
        assertTrue(HytaleInstallPath.hasOverride(HytaleInstallPath.KEY_SERVER_MODS))
    }

    @Test
    fun `hasOverride returns false after clear`() {
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_ASSETS_ZIP, "/custom/Assets.zip")
        HytaleInstallPath.clearOverride(HytaleInstallPath.KEY_ASSETS_ZIP)
        assertFalse(HytaleInstallPath.hasOverride(HytaleInstallPath.KEY_ASSETS_ZIP))
    }

    // --- Resolved paths with overrides ---

    @Test
    fun `assetsZipPath returns override when set`() {
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_ASSETS_ZIP, "/custom/path/Assets.zip")
        assertEquals(Paths.get("/custom/path/Assets.zip"), HytaleInstallPath.assetsZipPath())
    }

    @Test
    fun `serverJarPath returns override when set`() {
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_SERVER_JAR, "/custom/path/HytaleServer.jar")
        assertEquals(Paths.get("/custom/path/HytaleServer.jar"), HytaleInstallPath.serverJarPath())
    }

    @Test
    fun `serverModsPath returns override when set`() {
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_SERVER_MODS, "/custom/path/mods")
        assertEquals(Paths.get("/custom/path/mods"), HytaleInstallPath.serverModsPath())
    }

    // --- Override keys are distinct ---

    @Test
    fun `overrides are independent of each other`() {
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_ASSETS_ZIP, "/a")
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_SERVER_JAR, "/b")
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_SERVER_MODS, "/c")

        assertEquals("/a", HytaleInstallPath.getOverride(HytaleInstallPath.KEY_ASSETS_ZIP))
        assertEquals("/b", HytaleInstallPath.getOverride(HytaleInstallPath.KEY_SERVER_JAR))
        assertEquals("/c", HytaleInstallPath.getOverride(HytaleInstallPath.KEY_SERVER_MODS))

        HytaleInstallPath.clearOverride(HytaleInstallPath.KEY_SERVER_JAR)
        assertEquals("/a", HytaleInstallPath.getOverride(HytaleInstallPath.KEY_ASSETS_ZIP))
        assertNull(HytaleInstallPath.getOverride(HytaleInstallPath.KEY_SERVER_JAR))
        assertEquals("/c", HytaleInstallPath.getOverride(HytaleInstallPath.KEY_SERVER_MODS))
    }

    @Test
    fun `setOverride overwrites previous value`() {
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_ASSETS_ZIP, "/first")
        HytaleInstallPath.setOverride(HytaleInstallPath.KEY_ASSETS_ZIP, "/second")
        assertEquals("/second", HytaleInstallPath.getOverride(HytaleInstallPath.KEY_ASSETS_ZIP))
    }
}
