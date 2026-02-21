// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.embedding

import com.hyve.knowledge.core.config.KnowledgeConfig
import com.hyve.knowledge.core.db.EmbeddingPurpose

interface EmbeddingProvider {
    val modelId: String
    val dimension: Int

    suspend fun embed(texts: List<String>): List<FloatArray>
    suspend fun embedQuery(query: String): FloatArray
    suspend fun validate()

    companion object {
        fun fromConfig(config: KnowledgeConfig, purpose: EmbeddingPurpose = EmbeddingPurpose.CODE): EmbeddingProvider {
            return when (config.embeddingProvider) {
                "voyage" -> VoyageAIProvider(
                    apiKey = config.voyageApiKey,
                    model = when (purpose) {
                        EmbeddingPurpose.CODE -> config.voyageCodeModel
                        EmbeddingPurpose.TEXT -> config.voyageTextModel
                    },
                )
                else -> OllamaProvider(
                    baseUrl = config.ollamaBaseUrl,
                    model = when (purpose) {
                        EmbeddingPurpose.CODE -> config.ollamaCodeModel
                        EmbeddingPurpose.TEXT -> config.ollamaTextModel
                    },
                )
            }
        }
    }
}

sealed class EmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ConnectionFailed(url: String, cause: Throwable? = null) :
        EmbeddingException("Failed to connect to embedding provider at $url", cause)

    class ModelNotFound(model: String) :
        EmbeddingException("Embedding model '$model' not found. Pull it first with: ollama pull $model")

    class ApiError(status: Int, body: String) :
        EmbeddingException("Embedding API error ($status): $body")

    class InvalidApiKey :
        EmbeddingException("Invalid or missing API key for VoyageAI")

    class RateLimited(retryAfterMs: Long?) :
        EmbeddingException("Rate limited${retryAfterMs?.let { ", retry after ${it}ms" } ?: ""}")
}
