// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Paths

data class KnowledgeConfig(
    val embeddingProvider: String = "ollama",
    val ollamaBaseUrl: String = "http://localhost:11434",
    val ollamaCodeModel: String = "qwen3-embedding:8b",
    val ollamaTextModel: String = "nomic-embed-text-v2-moe",
    val voyageApiKey: String = "",
    val voyageCodeModel: String = "voyage-code-3",
    val voyageTextModel: String = "voyage-3-large",
    val indexPath: String = "",
    val resultsPerCorpus: Int = 10,
    val maxRelatedConnections: Int = 5,
) {
    fun resolvedIndexPath(): File {
        if (indexPath.isNotBlank()) return File(indexPath)
        val home = System.getProperty("user.home")
        return Paths.get(home, ".hyve", "knowledge").toFile()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        fun configFilePath(): File {
            val home = System.getProperty("user.home")
            return Paths.get(home, ".hyve", "knowledge", "mcp-config.json").toFile()
        }

        fun writeToFile(config: KnowledgeConfig, file: File = configFilePath()) {
            val fileConfig = FileConfig(
                embeddingProvider = config.embeddingProvider,
                ollamaBaseUrl = config.ollamaBaseUrl,
                ollamaCodeModel = config.ollamaCodeModel,
                ollamaTextModel = config.ollamaTextModel,
                voyageApiKey = config.voyageApiKey,
                voyageCodeModel = config.voyageCodeModel,
                voyageTextModel = config.voyageTextModel,
                indexPath = config.indexPath,
                resultsPerCorpus = config.resultsPerCorpus,
                maxRelatedConnections = config.maxRelatedConnections,
            )
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(FileConfig.serializer(), fileConfig))
        }

        fun loadFromFile(file: File = configFilePath()): KnowledgeConfig? {
            if (!file.exists()) return null
            return try {
                val fc = json.decodeFromString(FileConfig.serializer(), file.readText())
                val defaults = KnowledgeConfig()
                KnowledgeConfig(
                    embeddingProvider = fc.embeddingProvider ?: defaults.embeddingProvider,
                    ollamaBaseUrl = fc.ollamaBaseUrl ?: defaults.ollamaBaseUrl,
                    ollamaCodeModel = fc.ollamaCodeModel ?: defaults.ollamaCodeModel,
                    ollamaTextModel = fc.ollamaTextModel ?: defaults.ollamaTextModel,
                    voyageApiKey = fc.voyageApiKey ?: defaults.voyageApiKey,
                    voyageCodeModel = fc.voyageCodeModel ?: defaults.voyageCodeModel,
                    voyageTextModel = fc.voyageTextModel ?: defaults.voyageTextModel,
                    indexPath = fc.indexPath ?: defaults.indexPath,
                    resultsPerCorpus = fc.resultsPerCorpus ?: defaults.resultsPerCorpus,
                    maxRelatedConnections = fc.maxRelatedConnections ?: defaults.maxRelatedConnections,
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    @Serializable
    internal data class FileConfig(
        val embeddingProvider: String? = null,
        val ollamaBaseUrl: String? = null,
        val ollamaCodeModel: String? = null,
        val ollamaTextModel: String? = null,
        val voyageApiKey: String? = null,
        val voyageCodeModel: String? = null,
        val voyageTextModel: String? = null,
        val indexPath: String? = null,
        val resultsPerCorpus: Int? = null,
        val maxRelatedConnections: Int? = null,
    )
}
