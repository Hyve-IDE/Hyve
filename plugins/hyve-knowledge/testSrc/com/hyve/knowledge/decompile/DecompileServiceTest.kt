// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.decompile

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DecompileServiceTest {

    private lateinit var tempDir: File
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("decompile_service_test").toFile()
        outputDir = File(tempDir, "decompiled")
        outputDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ── Staleness detection ──────────────────────────────────────

    @Test
    fun `isStale returns true when output dir is empty`() {
        val fakeJar = createFakeJar("content-v1")
        assertTrue(DecompileService.isStale(fakeJar, outputDir))
    }

    @Test
    fun `isStale returns true when no meta file exists`() {
        val fakeJar = createFakeJar("content-v1")
        File(outputDir, "Foo.java").writeText("class Foo {}")
        assertTrue(DecompileService.isStale(fakeJar, outputDir))
    }

    @Test
    fun `isStale returns false when meta hash matches`() {
        val fakeJar = createFakeJar("content-v1")
        File(outputDir, "Foo.java").writeText("class Foo {}")
        DecompileService.writeDecompileMeta(fakeJar, outputDir)
        assertFalse(DecompileService.isStale(fakeJar, outputDir))
    }

    @Test
    fun `isStale returns true when jar hash changed`() {
        val fakeJar = createFakeJar("content-v1")
        File(outputDir, "Foo.java").writeText("class Foo {}")
        DecompileService.writeDecompileMeta(fakeJar, outputDir)

        fakeJar.writeText("content-v2")
        assertTrue(DecompileService.isStale(fakeJar, outputDir))
    }

    @Test
    fun `isStale returns true when output dir does not exist`() {
        val fakeJar = createFakeJar("content-v1")
        val nonExistent = File(tempDir, "nope")
        assertTrue(DecompileService.isStale(fakeJar, nonExistent))
    }

    // ── Meta file ────────────────────────────────────────────────

    @Test
    fun `writeDecompileMeta creates meta file with jarHash`() {
        val fakeJar = createFakeJar("test-content")
        DecompileService.writeDecompileMeta(fakeJar, outputDir)

        val metaFile = File(outputDir, "decompile_meta.json")
        assertTrue(metaFile.exists())
        val text = metaFile.readText()
        assertTrue(text.contains("\"jarHash\""))
        assertTrue(text.contains("\"decompiledAt\""))
    }

    @Test
    fun `writeDecompileMeta hash is deterministic`() {
        val fakeJar = createFakeJar("deterministic")
        DecompileService.writeDecompileMeta(fakeJar, outputDir)
        val hash1 = File(outputDir, "decompile_meta.json").readText()

        DecompileService.writeDecompileMeta(fakeJar, outputDir)
        val hash2 = File(outputDir, "decompile_meta.json").readText()

        val extractHash = { s: String -> Regex("\"jarHash\"\\s*:\\s*\"([^\"]+)\"").find(s)?.groupValues?.get(1) }
        assertEquals(extractHash(hash1), extractHash(hash2))
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun createFakeJar(content: String): File {
        val jar = File(tempDir, "fake.jar")
        jar.writeText(content)
        return jar
    }
}
