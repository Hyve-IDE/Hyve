// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.bridge

import com.hyve.knowledge.core.db.EmbeddingPurpose
import com.hyve.knowledge.core.embedding.EmbeddingProvider
import com.hyve.knowledge.settings.KnowledgeSettings

/**
 * IDE-side factory that bridges EmbeddingProvider.fromConfig() with KnowledgeSettings.
 */
object EmbeddingProviderFactory {
    fun fromSettings(purpose: EmbeddingPurpose = EmbeddingPurpose.CODE): EmbeddingProvider {
        val config = KnowledgeSettings.getInstance().toConfig()
        return EmbeddingProvider.fromConfig(config, purpose)
    }
}
