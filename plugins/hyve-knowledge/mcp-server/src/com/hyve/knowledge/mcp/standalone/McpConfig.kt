// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.mcp.standalone

import com.hyve.knowledge.core.config.KnowledgeConfig
import java.io.File

/**
 * Config loader for the standalone MCP server.
 * Priority: env vars > ~/.hyve/knowledge/mcp-config.json > defaults.
 */
object McpConfig {

    fun load(): KnowledgeConfig {
        val fileConfig = KnowledgeConfig.loadFromFile()
        val defaults = KnowledgeConfig()

        return KnowledgeConfig(
            embeddingProvider = env("HYVE_EMBEDDING_PROVIDER")
                ?: fileConfig?.embeddingProvider
                ?: defaults.embeddingProvider,
            ollamaBaseUrl = env("HYVE_OLLAMA_URL")
                ?: fileConfig?.ollamaBaseUrl
                ?: defaults.ollamaBaseUrl,
            ollamaCodeModel = env("HYVE_OLLAMA_CODE_MODEL")
                ?: fileConfig?.ollamaCodeModel
                ?: defaults.ollamaCodeModel,
            ollamaTextModel = env("HYVE_OLLAMA_TEXT_MODEL")
                ?: fileConfig?.ollamaTextModel
                ?: defaults.ollamaTextModel,
            voyageApiKey = env("VOYAGE_API_KEY")
                ?: fileConfig?.voyageApiKey
                ?: defaults.voyageApiKey,
            voyageCodeModel = env("HYVE_VOYAGE_CODE_MODEL")
                ?: fileConfig?.voyageCodeModel
                ?: defaults.voyageCodeModel,
            voyageTextModel = env("HYVE_VOYAGE_TEXT_MODEL")
                ?: fileConfig?.voyageTextModel
                ?: defaults.voyageTextModel,
            indexPath = env("HYVE_INDEX_PATH")
                ?: fileConfig?.indexPath
                ?: defaults.indexPath,
            resultsPerCorpus = fileConfig?.resultsPerCorpus
                ?: defaults.resultsPerCorpus,
            maxRelatedConnections = fileConfig?.maxRelatedConnections
                ?: defaults.maxRelatedConnections,
        )
    }

    fun configFilePath(): File = KnowledgeConfig.configFilePath()

    fun writeConfig(config: KnowledgeConfig) = KnowledgeConfig.writeToFile(config)

    private fun env(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }
}
