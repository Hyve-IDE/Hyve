// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.index

import com.hyve.knowledge.core.db.EmbeddingCacheDatabase
import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.core.logging.StdoutLogProvider
import java.security.MessageDigest

/**
 * Orchestration layer for the shared embedding cache.
 *
 * Used by all 4 indexer tasks to avoid re-embedding unchanged content
 * across versions. Content hash is `SHA-256(embeddingText)`.
 */
class EmbeddingCacheService(
    private val cache: EmbeddingCacheDatabase,
    private val log: LogProvider = StdoutLogProvider,
) {

    data class CacheLookupResult(
        /** Map of original list index -> cached vector */
        val cached: Map<Int, FloatArray>,
        /** Indices in the original list that need embedding */
        val uncachedIndices: List<Int>,
    )

    /**
     * Looks up cached embeddings for the given texts.
     *
     * @param embeddingTexts the full list of texts to embed
     * @param modelId the embedding model identifier (e.g. "qwen3-embedding:8b")
     * @return which indices are cached and which still need embedding
     */
    fun lookup(embeddingTexts: List<String>, modelId: String): CacheLookupResult {
        if (embeddingTexts.isEmpty()) {
            return CacheLookupResult(emptyMap(), emptyList())
        }

        val hashes = embeddingTexts.map { sha256(it) }
        val found = cache.lookup(hashes, modelId)

        val cached = mutableMapOf<Int, FloatArray>()
        val uncached = mutableListOf<Int>()

        for ((idx, hash) in hashes.withIndex()) {
            val vec = found[hash]
            if (vec != null) {
                cached[idx] = vec
            } else {
                uncached.add(idx)
            }
        }

        if (cached.isNotEmpty()) {
            log.info("Embedding cache: ${cached.size} hits, ${uncached.size} misses (${embeddingTexts.size} total)")
        }

        return CacheLookupResult(cached, uncached)
    }

    /**
     * Stores newly computed embeddings in the cache.
     *
     * @param embeddingTexts the texts that were embedded (parallel with vectors)
     * @param vectors the computed embedding vectors
     * @param modelId the embedding model identifier
     */
    fun store(embeddingTexts: List<String>, vectors: List<FloatArray>, modelId: String) {
        if (embeddingTexts.isEmpty()) return
        require(embeddingTexts.size == vectors.size) {
            "embeddingTexts.size (${embeddingTexts.size}) != vectors.size (${vectors.size})"
        }

        val entries = embeddingTexts.zip(vectors).map { (text, vec) ->
            EmbeddingCacheDatabase.CacheEntry(
                contentHash = sha256(text),
                modelId = modelId,
                vector = vec,
                dimension = vec.size,
            )
        }
        cache.storeBatch(entries)
    }

    companion object {
        private val digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

        internal fun sha256(text: String): String {
            val md = digest.get()
            md.reset()
            val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
