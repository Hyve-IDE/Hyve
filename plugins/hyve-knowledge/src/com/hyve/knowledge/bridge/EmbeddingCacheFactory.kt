// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.bridge

import com.hyve.knowledge.core.db.EmbeddingCacheDatabase
import com.hyve.knowledge.core.index.EmbeddingCacheService
import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.settings.KnowledgeSettings

/**
 * IDE-side singleton factory for the shared embedding cache.
 * The cache DB lives at `~/.hyve/knowledge/embedding-cache.db` (shared across versions).
 */
object EmbeddingCacheFactory {
    private val log: LogProvider = IntelliJLogProvider(EmbeddingCacheFactory::class.java)
    private var cacheDb: EmbeddingCacheDatabase? = null
    private var service: EmbeddingCacheService? = null

    fun getService(): EmbeddingCacheService {
        val existing = service
        if (existing != null) return existing

        val settings = KnowledgeSettings.getInstance()
        val cacheFile = java.io.File(settings.resolvedBasePath(), "embedding-cache.db")
        val db = EmbeddingCacheDatabase.forFile(cacheFile, log)
        cacheDb = db

        val svc = EmbeddingCacheService(db, log)
        service = svc
        return svc
    }

    fun resetInstance() {
        service = null
        cacheDb?.close()
        cacheDb = null
    }
}
