// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.index

import com.hyve.knowledge.core.db.KnowledgeDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileHashTrackerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var db: KnowledgeDatabase
    private lateinit var tracker: FileHashTracker

    @BeforeEach
    fun setUp() {
        val dbFile = File(tempDir, "test.db")
        db = KnowledgeDatabase.forFile(dbFile)
        db.getConnection() // trigger schema creation
        tracker = FileHashTracker(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `computes SHA-256 hash`() {
        val file = File(tempDir, "test.java")
        file.writeText("hello world")
        val hash = tracker.computeHash(file)
        assertEquals(64, hash.length) // SHA-256 = 64 hex chars
        // Known SHA-256 of "hello world"
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash)
    }

    @Test
    fun `detects added files on first run`() {
        val sourceDir = File(tempDir, "src")
        sourceDir.mkdirs()
        File(sourceDir, "Foo.java").writeText("class Foo {}")
        File(sourceDir, "Bar.java").writeText("class Bar {}")

        val changes = tracker.detectChanges(sourceDir)
        assertEquals(2, changes.added.size)
        assertEquals(0, changes.changed.size)
        assertEquals(0, changes.deleted.size)
        assertEquals(0, changes.unchanged.size)
        assertTrue(changes.hasChanges)
    }

    @Test
    fun `detects unchanged files after storing hashes`() {
        val sourceDir = File(tempDir, "src")
        sourceDir.mkdirs()
        val fooFile = File(sourceDir, "Foo.java")
        fooFile.writeText("class Foo {}")

        // First run: detect as added, then store hash
        val changes1 = tracker.detectChanges(sourceDir)
        assertEquals(1, changes1.added.size)

        tracker.updateHashes(mapOf("Foo.java" to tracker.computeHash(fooFile)))

        // Second run: should be unchanged
        val changes2 = tracker.detectChanges(sourceDir)
        assertEquals(0, changes2.added.size)
        assertEquals(0, changes2.changed.size)
        assertEquals(1, changes2.unchanged.size)
        assertFalse(changes2.hasChanges)
    }

    @Test
    fun `detects changed files`() {
        val sourceDir = File(tempDir, "src")
        sourceDir.mkdirs()
        val fooFile = File(sourceDir, "Foo.java")
        fooFile.writeText("class Foo {}")

        tracker.updateHashes(mapOf("Foo.java" to tracker.computeHash(fooFile)))

        // Modify file
        fooFile.writeText("class Foo { int x; }")

        val changes = tracker.detectChanges(sourceDir)
        assertEquals(0, changes.added.size)
        assertEquals(1, changes.changed.size)
        assertTrue("Foo.java" in changes.changed)
    }

    @Test
    fun `detects deleted files`() {
        val sourceDir = File(tempDir, "src")
        sourceDir.mkdirs()
        val fooFile = File(sourceDir, "Foo.java")
        fooFile.writeText("class Foo {}")

        tracker.updateHashes(mapOf("Foo.java" to tracker.computeHash(fooFile)))

        // Delete file
        fooFile.delete()

        val changes = tracker.detectChanges(sourceDir)
        assertEquals(0, changes.added.size)
        assertEquals(0, changes.changed.size)
        assertEquals(1, changes.deleted.size)
        assertTrue("Foo.java" in changes.deleted)
    }

    @Test
    fun `removeHashes clears entries`() {
        val sourceDir = File(tempDir, "src")
        sourceDir.mkdirs()
        File(sourceDir, "Foo.java").writeText("class Foo {}")
        File(sourceDir, "Bar.java").writeText("class Bar {}")

        tracker.updateHashes(mapOf(
            "Foo.java" to "hash1",
            "Bar.java" to "hash2",
        ))

        tracker.removeHashes(setOf("Foo.java"))

        // Foo should now appear as added
        val changes = tracker.detectChanges(sourceDir)
        assertTrue("Foo.java" in changes.added)
        assertTrue("Bar.java" in changes.changed || "Bar.java" in changes.unchanged)
    }
}
