// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.mcp.standalone

import com.hyve.knowledge.core.db.Corpus
import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.core.index.CorpusIndexManager
import com.hyve.knowledge.core.logging.StdoutLogProvider
import com.hyve.knowledge.core.search.KnowledgeSearchService
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() {
    val log = StdoutLogProvider

    // 1. Load config
    log.info("Loading configuration...")
    val config = McpConfig.load()
    log.info("Embedding provider: ${config.embeddingProvider}")
    log.info("Index path: ${config.resolvedIndexPath().absolutePath}")

    // 2. Validate knowledge.db exists
    val dbFile = File(config.resolvedIndexPath(), "knowledge.db")
    if (!dbFile.exists()) {
        log.error("Knowledge database not found at: ${dbFile.absolutePath}")
        log.error("Run 'Build All Knowledge Indices' from the Hyve IDE first to create the index.")
        System.exit(1)
    }

    // 3. Initialize search stack
    log.info("Opening knowledge database: ${dbFile.absolutePath}")
    val db = KnowledgeDatabase.forFile(dbFile, log)
    val indexManager = CorpusIndexManager(config, log)

    // 4. Check HNSW indices per corpus (warn, don't fail)
    for (corpus in Corpus.entries) {
        val hnswPath = indexManager.hnswPath(corpus)
        if (hnswPath.toFile().exists()) {
            log.info("HNSW index found for ${corpus.displayName}: $hnswPath")
        } else {
            log.warn("HNSW index missing for ${corpus.displayName}: $hnswPath — vector search will be unavailable for this corpus")
        }
    }

    val searchService = KnowledgeSearchService(db, indexManager, log)

    // 5. Create and run MCP server
    val server = HytaleKnowledgeServer(searchService)
    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down...")
        searchService.close()
        db.close()
    })

    runBlocking {
        server.run()
    }
}
