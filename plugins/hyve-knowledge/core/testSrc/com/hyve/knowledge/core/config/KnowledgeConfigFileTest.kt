// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class KnowledgeConfigFileTest {

    private lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("knowledge_config_test_").toFile()
        tempDir.deleteOnExit()
    }

    @Test
    fun `writeToFile and loadFromFile round-trips correctly`() {
        val original = KnowledgeConfig(
            embeddingProvider = "voyage",
            ollamaBaseUrl = "http://custom:11434",
            ollamaCodeModel = "custom-code-model",
            ollamaTextModel = "custom-text-model",
            voyageApiKey = "test-api-key",
            voyageCodeModel = "voyage-code-3",
            voyageTextModel = "voyage-3-large",
            indexPath = "/custom/index/path",
            resultsPerCorpus = 15,
            maxRelatedConnections = 8,
        )

        val configFile = File(tempDir, "mcp-config.json")
        KnowledgeConfig.writeToFile(original, configFile)

        val loaded = KnowledgeConfig.loadFromFile(configFile)
        assertNotNull(loaded)
        assertEquals(original.embeddingProvider, loaded!!.embeddingProvider)
        assertEquals(original.ollamaBaseUrl, loaded.ollamaBaseUrl)
        assertEquals(original.ollamaCodeModel, loaded.ollamaCodeModel)
        assertEquals(original.ollamaTextModel, loaded.ollamaTextModel)
        assertEquals(original.voyageApiKey, loaded.voyageApiKey)
        assertEquals(original.voyageCodeModel, loaded.voyageCodeModel)
        assertEquals(original.voyageTextModel, loaded.voyageTextModel)
        assertEquals(original.indexPath, loaded.indexPath)
        assertEquals(original.resultsPerCorpus, loaded.resultsPerCorpus)
        assertEquals(original.maxRelatedConnections, loaded.maxRelatedConnections)
    }

    @Test
    fun `loadFromFile returns null when file does not exist`() {
        val nonExistent = File(tempDir, "does-not-exist.json")
        val loaded = KnowledgeConfig.loadFromFile(nonExistent)
        assertNull(loaded)
    }

    @Test
    fun `loadFromFile returns null for malformed JSON`() {
        val configFile = File(tempDir, "bad.json")
        configFile.writeText("not json at all {{{")
        val loaded = KnowledgeConfig.loadFromFile(configFile)
        assertNull(loaded)
    }

    @Test
    fun `loadFromFile populates defaults for missing fields`() {
        val configFile = File(tempDir, "partial.json")
        configFile.writeText("""{ "embeddingProvider": "voyage" }""")

        val loaded = KnowledgeConfig.loadFromFile(configFile)
        assertNotNull(loaded)
        val defaults = KnowledgeConfig()
        assertEquals("voyage", loaded!!.embeddingProvider)
        assertEquals(defaults.ollamaBaseUrl, loaded.ollamaBaseUrl)
        assertEquals(defaults.ollamaCodeModel, loaded.ollamaCodeModel)
        assertEquals(defaults.resultsPerCorpus, loaded.resultsPerCorpus)
    }

    @Test
    fun `configFilePath points to hyve knowledge directory`() {
        val path = KnowledgeConfig.configFilePath().absolutePath
        assertTrue(
            path.contains(".hyve") && path.contains("knowledge") && path.endsWith("mcp-config.json"),
            "Config path should be ~/.hyve/knowledge/mcp-config.json, got: $path"
        )
    }

    @Test
    fun `resolvedIndexPath returns custom path when set`() {
        val config = KnowledgeConfig(indexPath = "/my/custom/path")
        assertEquals(File("/my/custom/path"), config.resolvedIndexPath())
    }

    @Test
    fun `resolvedIndexPath returns default when indexPath is blank`() {
        val config = KnowledgeConfig(indexPath = "")
        val resolved = config.resolvedIndexPath()
        assertTrue(
            resolved.absolutePath.endsWith(".hyve/knowledge") ||
                resolved.absolutePath.endsWith(".hyve\\knowledge"),
        )
    }
}
