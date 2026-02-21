// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.index

import com.hyve.knowledge.core.config.KnowledgeConfig
import com.hyve.knowledge.core.db.Corpus
import com.hyve.knowledge.core.embedding.EmbeddingProvider
import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.core.logging.StdoutLogProvider
import java.nio.file.Path
import java.nio.file.Paths

class CorpusIndexManager(
    private val config: KnowledgeConfig = KnowledgeConfig(),
    private val log: LogProvider = StdoutLogProvider,
) {
    private val indices = mutableMapOf<Corpus, HnswIndex>()
    private val providers = mutableMapOf<Corpus, EmbeddingProvider>()

    fun getIndex(corpus: Corpus): HnswIndex? {
        indices[corpus]?.let { if (it.isLoaded()) return it }

        val path = hnswPath(corpus)
        if (!path.toFile().exists()) return null

        val provider = getProvider(corpus)
        val index = HnswIndex(provider.dimension, log = log)
        try {
            index.load(path)
            indices[corpus] = index
            return index
        } catch (e: Exception) {
            log.warn("Failed to load HNSW index for ${corpus.displayName}", e)
            return null
        }
    }

    fun getProvider(corpus: Corpus): EmbeddingProvider {
        providers[corpus]?.let { return it }
        val provider = EmbeddingProvider.fromConfig(config, corpus.embeddingPurpose)
        providers[corpus] = provider
        return provider
    }

    fun hnswPath(corpus: Corpus): Path {
        return Paths.get(config.resolvedIndexPath().absolutePath, "hnsw", corpus.hnswFileName)
    }

    fun closeCorpus(corpus: Corpus) {
        indices.remove(corpus)?.close()
        providers.remove(corpus)
    }

    fun closeAll() {
        indices.values.forEach { it.close() }
        indices.clear()
        providers.clear()
    }
}
