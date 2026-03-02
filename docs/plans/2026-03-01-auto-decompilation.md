# Auto-Decompilation During Indexing — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Automatically decompile HytaleServer.jar during BuildAllIndex and SiblingIndexTask when decompiled source is missing or stale, so pre-release and sibling patchlines get code indexed without a manual decompile step.

**Architecture:** Extract core decompilation logic from `DecompileTask` into a stateless `DecompileService` object. Both the manual action and auto-indexing call it. JAR staleness is tracked via a `decompile_meta.json` file containing the server JAR's SHA-256 hash.

**Tech Stack:** Kotlin, FernFlower (IntelliJ bundled `org.jetbrains.java.decompiler`), SHA-256 hashing via `java.security.MessageDigest`.

---

### Task 1: Create DecompileService with staleness detection

**Files:**
- Create: `plugins/hyve-knowledge/src/com/hyve/knowledge/decompile/DecompileService.kt`
- Test: `plugins/hyve-knowledge/testSrc/com/hyve/knowledge/decompile/DecompileServiceTest.kt`

**Step 1: Write the failing tests**

```kotlin
// DecompileServiceTest.kt
package com.hyve.knowledge.decompile

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DecompileServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var outputDir: File

    @BeforeEach
    fun setUp() {
        outputDir = tempDir.resolve("decompiled").toFile()
        outputDir.mkdirs()
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
        // Put a java file so dir is non-empty
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

        // Overwrite JAR with different content
        fakeJar.writeText("content-v2")
        assertTrue(DecompileService.isStale(fakeJar, outputDir))
    }

    @Test
    fun `isStale returns true when output dir does not exist`() {
        val fakeJar = createFakeJar("content-v1")
        val nonExistent = tempDir.resolve("nope").toFile()
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

        // jarHash should be identical; decompiledAt may differ
        val extractHash = { s: String -> Regex("\"jarHash\"\\s*:\\s*\"([^\"]+)\"").find(s)?.groupValues?.get(1) }
        assertEquals(extractHash(hash1), extractHash(hash2))
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun createFakeJar(content: String): File {
        val jar = tempDir.resolve("fake.jar").toFile()
        jar.writeText(content)
        return jar
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :test --tests "com.hyve.knowledge.decompile.DecompileServiceTest"`
Expected: Compilation error — `DecompileService` doesn't exist yet.

**Step 3: Write DecompileService**

```kotlin
// DecompileService.kt
package com.hyve.knowledge.decompile

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * Stateless decompilation service. Owns FernFlower invocation, post-processing
 * fixes, and JAR staleness detection via decompile_meta.json.
 *
 * Called by both DecompileTask (manual action) and BuildAllIndexAction (auto).
 */
object DecompileService {

    private val log = Logger.getInstance(DecompileService::class.java)
    private const val META_FILE = "decompile_meta.json"

    /**
     * Decompiles [serverJar] into [outputDir] using FernFlower, then applies
     * post-processing fixes. Progress updates are sent to [indicator].
     */
    fun decompile(serverJar: File, outputDir: File, indicator: ProgressIndicator) {
        outputDir.mkdirs()
        log.info("Decompiling ${serverJar.name} -> $outputDir")

        // Phase 1: FernFlower decompilation
        indicator.text = "Decompiling ${serverJar.name}..."
        indicator.isIndeterminate = true

        val provider = JarBytecodeProvider(serverJar)
        val saver = DirectoryResultSaver(outputDir)
        val logger = IndicatorLogger(indicator)

        val options = HashMap<String, Any>(IFernflowerPreferences.DEFAULTS).apply {
            put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1")
            put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1")
            put(IFernflowerPreferences.REMOVE_BRIDGE, "1")
            put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1")
            put(IFernflowerPreferences.DECOMPILE_INNER, "1")
            put(IFernflowerPreferences.DECOMPILE_ENUM, "1")
            put(IFernflowerPreferences.USE_DEBUG_VAR_NAMES, "1")
            put(IFernflowerPreferences.LOG_LEVEL, IFernflowerLogger.Severity.INFO.name)
            put(IFernflowerPreferences.INDENT_STRING, "    ")
        }

        val decompiler = BaseDecompiler(provider, saver, options, logger)
        decompiler.addSource(serverJar)

        try {
            decompiler.decompileContext()
        } finally {
            provider.close()
        }

        if (indicator.isCanceled) return

        log.info("FernFlower wrote ${saver.filesWritten} Hytale files, skipped ${saver.filesSkipped} dependency files")

        // Phase 2: Apply decompilation fixes
        indicator.isIndeterminate = false
        indicator.fraction = 0.0
        indicator.text = "Applying decompilation fixes..."

        val javaFiles = outputDir.walkTopDown()
            .filter { it.extension == "java" }
            .toList()

        var fixedCount = 0
        javaFiles.forEachIndexed { idx, file ->
            if (indicator.isCanceled) return
            indicator.text2 = file.name
            indicator.fraction = idx.toDouble() / javaFiles.size.coerceAtLeast(1)
            if (DecompilationFixes.applyToFile(file)) {
                fixedCount++
            }
        }

        log.info("Applied fixes to $fixedCount / ${javaFiles.size} files")
    }

    /**
     * Returns true if [outputDir] needs (re-)decompilation:
     * - outputDir doesn't exist or is empty
     * - decompile_meta.json is missing
     * - stored JAR hash doesn't match current JAR
     */
    fun isStale(serverJar: File, outputDir: File): Boolean {
        if (!outputDir.exists()) return true
        val files = outputDir.listFiles() ?: return true
        // Ignore the meta file itself when checking for emptiness
        if (files.none { it.name != META_FILE }) return true

        val metaFile = File(outputDir, META_FILE)
        if (!metaFile.exists()) return true

        val storedHash = extractHash(metaFile.readText()) ?: return true
        val currentHash = sha256(serverJar)
        return storedHash != currentHash
    }

    /**
     * Writes decompile_meta.json with the SHA-256 hash of [serverJar].
     */
    fun writeDecompileMeta(serverJar: File, outputDir: File) {
        outputDir.mkdirs()
        val hash = sha256(serverJar)
        val meta = """
            |{
            |  "jarHash": "$hash",
            |  "decompiledAt": "${Instant.now()}"
            |}
        """.trimMargin()
        File(outputDir, META_FILE).writeText(meta)
    }

    // ── Internal ─────────────────────────────────────────────────

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractHash(metaJson: String): String? =
        Regex("\"jarHash\"\\s*:\\s*\"([^\"]+)\"").find(metaJson)?.groupValues?.get(1)

    /**
     * FernFlower logger that updates the progress indicator.
     */
    private class IndicatorLogger(
        private val indicator: ProgressIndicator,
    ) : IFernflowerLogger() {

        private var currentClass: String = ""

        private fun isHytaleClass(): Boolean =
            currentClass.startsWith("com/hypixel/hytale/")

        override fun writeMessage(message: String, severity: Severity) {
            when (severity) {
                Severity.TRACE -> log.trace(message)
                Severity.INFO -> log.info(message)
                Severity.WARN -> log.warn(message)
                Severity.ERROR -> {
                    if (isHytaleClass()) log.error(message) else log.warn(message)
                }
            }
        }

        override fun writeMessage(message: String, severity: Severity, t: Throwable) {
            if (isHytaleClass()) log.error(message, t) else log.warn(message, t)
        }

        override fun startClass(className: String) {
            currentClass = className
            indicator.text2 = className
        }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :test --tests "com.hyve.knowledge.decompile.DecompileServiceTest"`
Expected: All 7 tests PASS.

**Step 5: Commit**

```
feat: add DecompileService with JAR staleness detection
```

---

### Task 2: Refactor DecompileTask to delegate to DecompileService

**Files:**
- Modify: `plugins/hyve-knowledge/src/com/hyve/knowledge/decompile/DecompileTask.kt`

**Step 1: Write the failing test**

No new test needed — existing manual action behavior is preserved. The test is: the build still compiles and existing `DecompilationFixesTest` still passes.

**Step 2: Refactor DecompileTask**

Replace the entire `run()` body and remove the inner `IndicatorLogger` class. The notification callbacks (`onSuccess`, `onThrowable`) stay.

Replace `DecompileTask.kt` with:

```kotlin
// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.decompile

import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Background task for the manual "Decompile Hytale Server" action.
 * Delegates to [DecompileService] for the actual work.
 */
class DecompileTask(
    project: Project,
    private val serverJar: File,
) : Task.Backgroundable(project, "Decompiling Hytale Server...", true) {

    private val log = Logger.getInstance(DecompileTask::class.java)

    override fun run(indicator: ProgressIndicator) {
        val settings = KnowledgeSettings.getInstance()
        val outputDir = settings.resolvedDecompilePath()

        DecompileService.decompile(serverJar, outputDir, indicator)
        DecompileService.writeDecompileMeta(serverJar, outputDir)
    }

    override fun onSuccess() {
        val settings = KnowledgeSettings.getInstance()
        val outputDir = settings.resolvedDecompilePath()
        val count = outputDir.walkTopDown().count { it.extension == "java" }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Decompilation complete",
                "$count Java files written to ${outputDir.absolutePath}",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    override fun onThrowable(error: Throwable) {
        log.error("Decompilation failed", error)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Decompilation failed",
                error.message ?: "Unknown error",
                NotificationType.ERROR,
            )
            .notify(project)
    }
}
```

**Step 3: Build to verify compilation**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. All existing tests pass.

**Step 4: Commit**

```
refactor: delegate DecompileTask to DecompileService
```

---

### Task 3: Add auto-decompilation to BuildAllTask

**Files:**
- Modify: `plugins/hyve-knowledge/src/com/hyve/knowledge/actions/BuildAllIndexAction.kt:1-5` (add import)
- Modify: `plugins/hyve-knowledge/src/com/hyve/knowledge/actions/BuildAllIndexAction.kt:95-110` (code corpus section)

**Step 1: Add import**

Add to the imports block (after line 6):

```kotlin
import com.hyve.knowledge.decompile.DecompileService
```

**Step 2: Replace the code corpus section**

Replace lines 95-110 (the `// ── Code corpus (0-25%)` block) with:

```kotlin
            // ── Code corpus (0-25%) ─────────────────────────────
            val serverJar = HytaleInstallPath.serverJarPath()?.toFile()
            val decompileDir = settings.resolvedDecompilePath()

            // Auto-decompile if JAR exists but decompiled source is missing or stale
            if (serverJar != null && serverJar.exists() && DecompileService.isStale(serverJar, decompileDir)) {
                indicator.text = "Decompiling server code..."
                try {
                    DecompileService.decompile(serverJar, decompileDir, indicator)
                    DecompileService.writeDecompileMeta(serverJar, decompileDir)
                } catch (e: Exception) {
                    log.warn("Auto-decompilation failed", e)
                    results.add("Code: decompilation failed (${e.message})")
                }
            }

            if (decompileDir.exists() && decompileDir.listFiles()?.any { it.name != "decompile_meta.json" } == true) {
                indicator.text = "Indexing server code..."
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                try {
                    val task = IndexerTask(project)
                    task.run(indicator)
                    results.add("Code: indexed")
                } catch (e: Exception) {
                    log.warn("Code indexing failed", e)
                    results.add("Code: failed (${e.message})")
                }
            } else if (!results.any { it.startsWith("Code:") }) {
                results.add("Code: skipped (no decompiled source)")
            }
```

**Step 3: Build to verify compilation**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```
feat: auto-decompile during BuildAllIndex when source is missing or stale
```

---

### Task 4: Add auto-decompilation to SiblingIndexTask

**Files:**
- Modify: `plugins/hyve-knowledge/src/com/hyve/knowledge/actions/BuildAllIndexAction.kt:307-321` (SiblingIndexTask code corpus section)

**Step 1: Replace the sibling code corpus section**

Replace lines 307-321 (the `// ── Code corpus (0-25%)` block inside `SiblingIndexTask`) with the same pattern as Task 3:

```kotlin
            // ── Code corpus (0-25%) ─────────────────────────────
            val serverJar = HytaleInstallPath.serverJarPath()?.toFile()
            val decompileDir = settings.resolvedDecompilePath()

            // Auto-decompile if JAR exists but decompiled source is missing or stale
            if (serverJar != null && serverJar.exists() && DecompileService.isStale(serverJar, decompileDir)) {
                indicator.text = "Decompiling ${siblingInfo.patchline} server code..."
                try {
                    DecompileService.decompile(serverJar, decompileDir, indicator)
                    DecompileService.writeDecompileMeta(serverJar, decompileDir)
                } catch (e: Exception) {
                    log.warn("Auto-decompilation failed for ${siblingInfo.displayName}", e)
                    results.add("Code: decompilation failed (${e.message})")
                }
            }

            if (decompileDir.exists() && decompileDir.listFiles()?.any { it.name != "decompile_meta.json" } == true) {
                indicator.text = "Indexing ${siblingInfo.patchline} server code..."
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                try {
                    IndexerTask(project).run(indicator)
                    results.add("Code: indexed")
                } catch (e: Exception) {
                    log.warn("Code indexing failed for ${siblingInfo.displayName}", e)
                    results.add("Code: failed (${e.message})")
                }
            } else if (!results.any { it.startsWith("Code:") }) {
                results.add("Code: skipped (no decompiled source)")
            }
```

**Step 2: Build to verify compilation**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```
feat: auto-decompile during sibling patchline indexing
```

---

### Task 5: Final build verification

**Step 1: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL with all tests passing.

**Step 2: Verify no regressions in MCP server tests**

Run: `./gradlew :mcp-server:test`
Expected: All tests PASS.

**Step 3: Commit the design doc**

```
docs: add auto-decompilation design document
```
