// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.diff

import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.core.logging.StdoutLogProvider
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Paths

/**
 * Caches diff results at `~/.hyve/knowledge/diffs/{vA}--{vB}.diff.json`.
 *
 * Invalidates when either version's `version_meta.json` or `knowledge.db`
 * has been updated since the diff was computed.
 */
class DiffCache(
    private val basePath: File,
    private val log: LogProvider = StdoutLogProvider,
) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val diffsDir: File get() = File(basePath, "diffs")

    fun get(versionA: String, versionB: String): VersionDiff? {
        val file = diffFile(versionA, versionB)
        if (!file.exists()) return null

        val cacheTime = file.lastModified()

        // Check invalidation: version_meta.json or knowledge.db newer than cache
        for ((version, label) in listOf(versionA to "A", versionB to "B")) {
            val meta = versionMetaFile(version)
            val db = versionDbFile(version)
            if (meta.exists() && meta.lastModified() > cacheTime) {
                log.info("Diff cache invalidated: $version meta was updated")
                file.delete()
                return null
            }
            if (db.exists() && db.lastModified() > cacheTime) {
                log.info("Diff cache invalidated: $version database was updated")
                file.delete()
                return null
            }
        }

        return try {
            json.decodeFromString(VersionDiff.serializer(), file.readText())
        } catch (e: Exception) {
            log.warn("Failed to read diff cache: ${e.message}")
            file.delete()
            null
        }
    }

    fun put(diff: VersionDiff) {
        diffsDir.mkdirs()
        val file = diffFile(diff.versionA, diff.versionB)
        file.writeText(json.encodeToString(VersionDiff.serializer(), diff))
    }

    private fun diffFile(versionA: String, versionB: String): File {
        val safeName = "${versionA}--${versionB}.diff.json"
        return File(diffsDir, safeName)
    }

    private fun versionMetaFile(version: String): File {
        return Paths.get(basePath.absolutePath, "versions", version, "version_meta.json").toFile()
    }

    private fun versionDbFile(version: String): File {
        return Paths.get(basePath.absolutePath, "versions", version, "knowledge.db").toFile()
    }

    companion object {
        fun forDefaultPath(): DiffCache {
            val home = System.getProperty("user.home")
            return DiffCache(Paths.get(home, ".hyve", "knowledge").toFile())
        }
    }
}
