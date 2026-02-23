package com.hyve.mod

import com.hyve.mod.run.HytaleServerRunState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for the parseMajorVersion helper (no IntelliJ platform needed)
 * and verifies run configuration field defaults.
 */
class HytaleServerRunConfigurationFieldsTest {

    @Test
    fun `parseMajorVersion extracts major from full version`() {
        assertEquals(21, HytaleServerRunState.parseMajorVersion("21.0.2"))
    }

    @Test
    fun `parseMajorVersion extracts from version prefix string`() {
        assertEquals(21, HytaleServerRunState.parseMajorVersion("version 21.0.2"))
    }

    @Test
    fun `parseMajorVersion returns null for empty string`() {
        assertNull(HytaleServerRunState.parseMajorVersion(""))
    }

    @Test
    fun `parseMajorVersion extracts from quoted version string`() {
        assertEquals(17, HytaleServerRunState.parseMajorVersion("version \"17.0.1\""))
    }
}
