// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.db

import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.core.logging.StdoutLogProvider
import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class KnowledgeDatabase private constructor(
    private val dbFile: File,
    private val log: LogProvider = StdoutLogProvider,
) : Closeable {

    private var connection: Connection? = null

    fun getConnection(): Connection {
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
        conn.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
        connection = conn
        migrate(conn)
        return conn
    }

    private fun migrate(conn: Connection) {
        val currentVersion = getSchemaVersion(conn)
        if (currentVersion < 1) {
            log.info("Creating knowledge database schema v1")
            conn.createStatement().use { stmt ->
                stmt.executeUpdate(SCHEMA_V1)
            }
            setSchemaVersion(conn, 1)
        }
        if (currentVersion < 2) {
            log.info("Migrating knowledge database to schema v2 (multi-corpus)")
            conn.createStatement().use { stmt ->
                stmt.executeUpdate(SCHEMA_V2)
            }
            setSchemaVersion(conn, 2)
        }
    }

    private fun getSchemaVersion(conn: Connection): Int {
        return try {
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT version FROM schema_version ORDER BY version DESC LIMIT 1").use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun setSchemaVersion(conn: Connection, version: Int) {
        conn.prepareStatement("INSERT INTO schema_version (version, applied_at) VALUES (?, datetime('now'))").use { ps ->
            ps.setInt(1, version)
            ps.executeUpdate()
        }
    }

    override fun close() {
        try {
            connection?.close()
        } catch (e: Exception) {
            log.warn("Error closing knowledge database", e)
        }
        connection = null
    }

    // ── Query helpers ────────────────────────────────────────────

    fun <T> query(sql: String, vararg params: Any?, mapper: (java.sql.ResultSet) -> T): List<T> {
        val conn = getConnection()
        return conn.prepareStatement(sql).use { ps ->
            params.forEachIndexed { i, p -> ps.setObject(i + 1, p) }
            ps.executeQuery().use { rs ->
                val results = mutableListOf<T>()
                while (rs.next()) results.add(mapper(rs))
                results
            }
        }
    }

    fun execute(sql: String, vararg params: Any?) {
        val conn = getConnection()
        conn.prepareStatement(sql).use { ps ->
            params.forEachIndexed { i, p -> ps.setObject(i + 1, p) }
            ps.executeUpdate()
        }
    }

    fun <T> inTransaction(block: (Connection) -> T): T {
        val conn = getConnection()
        conn.autoCommit = false
        return try {
            val result = block(conn)
            conn.commit()
            result
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    companion object {
        fun forFile(dbFile: File, log: LogProvider = StdoutLogProvider): KnowledgeDatabase =
            KnowledgeDatabase(dbFile, log)

        internal const val SCHEMA_V1 = """
            CREATE TABLE IF NOT EXISTS schema_version (
                version     INTEGER PRIMARY KEY,
                applied_at  TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS file_hashes (
                file_path   TEXT PRIMARY KEY,
                file_hash   TEXT NOT NULL,
                corpus_type TEXT NOT NULL DEFAULT 'java',
                indexed_at  TEXT NOT NULL DEFAULT (datetime('now'))
            );

            CREATE TABLE IF NOT EXISTS nodes (
                id            TEXT PRIMARY KEY,
                node_type     TEXT NOT NULL,
                display_name  TEXT NOT NULL,
                file_path     TEXT,
                line_start    INTEGER,
                line_end      INTEGER,
                content       TEXT,
                embedding_text TEXT,
                chunk_index   INTEGER,
                owning_file   TEXT,
                metadata      TEXT
            );

            CREATE TABLE IF NOT EXISTS edges (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                source_id       TEXT NOT NULL,
                target_id       TEXT NOT NULL,
                edge_type       TEXT NOT NULL,
                owning_file_id  TEXT,
                target_resolved INTEGER NOT NULL DEFAULT 1,
                metadata        TEXT,
                UNIQUE(source_id, target_id, edge_type)
            );

            CREATE TABLE IF NOT EXISTS communities (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                name        TEXT,
                summary     TEXT,
                level       INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS node_communities (
                node_id      TEXT NOT NULL,
                community_id INTEGER NOT NULL,
                PRIMARY KEY (node_id, community_id)
            );

            CREATE TABLE IF NOT EXISTS index_errors (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                file_path   TEXT,
                error_type  TEXT NOT NULL,
                message     TEXT NOT NULL,
                created_at  TEXT NOT NULL DEFAULT (datetime('now'))
            );

            CREATE INDEX IF NOT EXISTS idx_nodes_type ON nodes(node_type);
            CREATE INDEX IF NOT EXISTS idx_nodes_file ON nodes(file_path);
            CREATE INDEX IF NOT EXISTS idx_nodes_display ON nodes(display_name);
            CREATE INDEX IF NOT EXISTS idx_edges_source ON edges(source_id);
            CREATE INDEX IF NOT EXISTS idx_edges_target ON edges(target_id);
            CREATE INDEX IF NOT EXISTS idx_edges_type ON edges(edge_type);
            CREATE INDEX IF NOT EXISTS idx_edges_owning ON edges(owning_file_id);
            CREATE INDEX IF NOT EXISTS idx_file_hashes_corpus ON file_hashes(corpus_type);
        """

        internal const val SCHEMA_V2 = """
            ALTER TABLE nodes ADD COLUMN corpus TEXT NOT NULL DEFAULT 'code';
            ALTER TABLE nodes ADD COLUMN data_type TEXT;
            CREATE INDEX IF NOT EXISTS idx_nodes_corpus ON nodes(corpus);
            CREATE INDEX IF NOT EXISTS idx_nodes_data_type ON nodes(data_type);
            CREATE INDEX IF NOT EXISTS idx_nodes_corpus_data_type ON nodes(corpus, data_type);
        """
    }
}
