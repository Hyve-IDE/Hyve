// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.bridge

import com.hyve.knowledge.core.config.KnowledgeConfig
import com.hyve.knowledge.settings.KnowledgeSettings

fun KnowledgeSettings.toConfig(): KnowledgeConfig {
    val s = this.state
    return KnowledgeConfig(
        embeddingProvider = s.embeddingProvider,
        ollamaBaseUrl = s.ollamaBaseUrl,
        ollamaCodeModel = s.ollamaCodeModel,
        ollamaTextModel = s.ollamaTextModel,
        voyageApiKey = s.voyageApiKey,
        voyageCodeModel = s.voyageCodeModel,
        voyageTextModel = s.voyageTextModel,
        indexPath = s.indexPath,
        resultsPerCorpus = s.resultsPerCorpus,
        maxRelatedConnections = s.maxRelatedConnections,
    )
}
