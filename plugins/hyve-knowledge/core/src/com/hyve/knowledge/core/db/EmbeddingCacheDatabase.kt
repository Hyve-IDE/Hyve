// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.db

import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.core.logging.StdoutLogProvider
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.Connection
import java.sql.DriverManager

/**
 * Shared embedding cache database at `~/.hyve/knowledge/embedding-cache.db`.
 *
 * Caches `(content_hash, model_id) -> vector` so that unchanged content
 * doesn't need to be re-embedded when indexing multiple versions.
 */
class EmbeddingCacheDatabase private constructor(
    private val dbFile: File,
    private val log: LogProvider = StdoutLogProvider,
) : Closeable {

    private var connection: Connection? = null

    private fun getConnection(): Connection {
        val conn = connection
        if (conn != null && !conn.isClosed) return conn
        return openConnection()
    }

    private fun openConnection(): Connection {
        dbFile.parentFile?.mkdirs()
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        conn.autoCommit = true
        conn.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
        connection = conn
        migrate(conn)
        return conn
    }

    private fun migrate(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(SCHEMA)
        }
    }

    data class CacheEntry(
        val contentHash: String,
        val modelId: String,
        val vector: FloatArray,
        val dimension: Int,
    )

    /**
     * Batch lookup: returns a map of content_hash -> vector for all found entries.
     */
    fun lookup(hashes: List<String>, modelId: String): Map<String, FloatArray> {
        if (hashes.isEmpty()) return emptyMap()
        val conn = getConnection()
        val result = mutableMapOf<String, FloatArray>()

        // Process in batches to avoid SQLite variable limit
        for (batch in hashes.chunked(500)) {
            val placeholders = batch.joinToString(",") { "?" }
            val sql = "SELECT content_hash, vector, dimension FROM embedding_cache WHERE content_hash IN ($placeholders) AND model_id = ?"
            conn.prepareStatement(sql).use { ps ->
                batch.forEachIndexed { i, hash -> ps.setString(i + 1, hash) }
                ps.setString(batch.size + 1, modelId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val hash = rs.getString("content_hash")
                        val blob = rs.getBytes("vector")
                        val dimension = rs.getInt("dimension")
                        result[hash] = blobToFloatArray(blob, dimension)
                    }
                }
            }
        }
        return result
    }

    /**
     * Store a single embedding in the cache.
     */
    fun store(hash: String, modelId: String, vector: FloatArray, dimension: Int) {
        val conn = getConnection()
        conn.prepareStatement(INSERT_SQL).use { ps ->
            ps.setString(1, hash)
            ps.setString(2, modelId)
            ps.setBytes(3, floatArrayToBlob(vector))
            ps.setInt(4, dimension)
            ps.executeUpdate()
        }
    }

    /**
     * Batch store embeddings in the cache.
     */
    fun storeBatch(entries: List<CacheEntry>) {
        if (entries.isEmpty()) return
        val conn = getConnection()
        conn.autoCommit = false
        try {
            conn.prepareStatement(INSERT_SQL).use { ps ->
                for (entry in entries) {
                    ps.setString(1, entry.contentHash)
                    ps.setString(2, entry.modelId)
                    ps.setBytes(3, floatArrayToBlob(entry.vector))
                    ps.setInt(4, entry.dimension)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    override fun close() {
        try {
            connection?.close()
        } catch (e: Exception) {
            log.warn("Error closing embedding cache database", e)
        }
        connection = null
    }

    companion object {
        fun forFile(dbFile: File, log: LogProvider = StdoutLogProvider): EmbeddingCacheDatabase =
            EmbeddingCacheDatabase(dbFile, log)

        /** Default cache location: ~/.hyve/knowledge/embedding-cache.db */
        fun defaultFile(): File {
            val home = System.getProperty("user.home")
            return File(home, ".hyve/knowledge/embedding-cache.db")
        }

        private const val SCHEMA = """
            CREATE TABLE IF NOT EXISTS embedding_cache (
                content_hash  TEXT NOT NULL,
                model_id      TEXT NOT NULL,
                vector        BLOB NOT NULL,
                dimension     INTEGER NOT NULL,
                created_at    TEXT NOT NULL DEFAULT (datetime('now')),
                PRIMARY KEY (content_hash, model_id)
            )
        """

        private const val INSERT_SQL =
            "INSERT OR IGNORE INTO embedding_cache (content_hash, model_id, vector, dimension) VALUES (?, ?, ?, ?)"

        internal fun floatArrayToBlob(arr: FloatArray): ByteArray {
            val buf = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in arr) buf.putFloat(f)
            return buf.array()
        }

        internal fun blobToFloatArray(blob: ByteArray, dimension: Int): FloatArray {
            val buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(dimension) { buf.float }
        }
    }
}
