// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.index

import com.hyve.knowledge.core.db.KnowledgeDatabase
import java.io.File
import java.security.MessageDigest

/**
 * Tracks file hashes for incremental indexing.
 * Detects which files have been added, changed, deleted, or remain unchanged.
 * Ported from TypeScript `ingest.ts` computeChanges().
 */
class FileHashTracker(private val db: KnowledgeDatabase) {

    data class ChangeSet(
        val added: Set<String>,
        val changed: Set<String>,
        val deleted: Set<String>,
        val unchanged: Set<String>,
        /** All current file hashes (path→hash). Save these to prevent no-chunk files from appearing as "added" on next run. */
        val currentHashes: Map<String, String> = emptyMap(),
    ) {
        val hasChanges: Boolean get() = added.isNotEmpty() || changed.isNotEmpty() || deleted.isNotEmpty()
        val totalChanged: Int get() = added.size + changed.size
    }

    /**
     * Compute SHA-256 hash of a file's content.
     */
    fun computeHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute SHA-256 hash of a byte array.
     */
    fun computeHash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Scan a directory and detect changes relative to the stored hashes.
     *
     * @param sourceDir Root directory to scan
     * @param corpusType Type key for the file_hashes table (e.g. "java")
     * @param extensionFilter If non-null, only include files with these extensions (e.g. setOf("java"))
     * @param fileFilter Optional filter on relative paths (e.g. only com/hypixel/hytale/)
     */
    fun detectChanges(
        sourceDir: File,
        corpusType: String = "java",
        extensionFilter: Set<String>? = null,
        fileFilter: ((String) -> Boolean)? = null,
    ): ChangeSet {
        // Load existing hashes from DB
        val existingHashes = loadStoredHashes(corpusType)

        // Scan current files
        val currentHashes = mutableMapOf<String, String>()
        sourceDir.walkTopDown()
            .filter { file ->
                file.isFile && (extensionFilter == null || file.extension in extensionFilter)
            }
            .forEach { file ->
                val relativePath = file.relativeTo(sourceDir).path.replace('\\', '/')
                if (fileFilter == null || fileFilter(relativePath)) {
                    currentHashes[relativePath] = computeHash(file)
                }
            }

        return computeChangeSet(existingHashes, currentHashes)
    }

    /**
     * Detect changes for corpora whose files come from a non-filesystem source (e.g. ZIP entries).
     * The caller supplies a pre-computed map of path → hash.
     *
     * @param hashes Map of relative file path → SHA-256 hash (computed by caller)
     * @param corpusType Type key for the file_hashes table
     */
    fun computeChangesFromMap(hashes: Map<String, String>, corpusType: String): ChangeSet {
        val existingHashes = loadStoredHashes(corpusType)
        return computeChangeSet(existingHashes, hashes)
    }

    private fun computeChangeSet(
        existingHashes: Map<String, String>,
        currentHashes: Map<String, String>,
    ): ChangeSet {
        val added = mutableSetOf<String>()
        val changed = mutableSetOf<String>()
        val unchanged = mutableSetOf<String>()

        for ((path, hash) in currentHashes) {
            val existingHash = existingHashes[path]
            when {
                existingHash == null -> added.add(path)
                existingHash != hash -> changed.add(path)
                else -> unchanged.add(path)
            }
        }

        val deleted = existingHashes.keys - currentHashes.keys

        return ChangeSet(added, changed, deleted, unchanged, currentHashes)
    }

    /**
     * Update stored hashes for files that were successfully indexed.
     */
    fun updateHashes(fileHashes: Map<String, String>, corpusType: String = "java") {
        db.inTransaction { conn ->
            val ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO file_hashes (file_path, file_hash, corpus_type) VALUES (?, ?, ?)"
            )
            for ((path, hash) in fileHashes) {
                ps.setString(1, path)
                ps.setString(2, hash)
                ps.setString(3, corpusType)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    /**
     * Remove stored hashes for deleted/changed files.
     */
    fun removeHashes(filePaths: Set<String>) {
        if (filePaths.isEmpty()) return
        db.inTransaction { conn ->
            val ps = conn.prepareStatement("DELETE FROM file_hashes WHERE file_path = ?")
            for (path in filePaths) {
                ps.setString(1, path)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun loadStoredHashes(corpusType: String): Map<String, String> {
        return db.query(
            "SELECT file_path, file_hash FROM file_hashes WHERE corpus_type = ?",
            corpusType,
        ) { rs ->
            rs.getString("file_path") to rs.getString("file_hash")
        }.toMap()
    }
}
