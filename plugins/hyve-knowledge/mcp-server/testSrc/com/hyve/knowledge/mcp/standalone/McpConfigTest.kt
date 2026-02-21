// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.mcp.standalone

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class McpConfigTest {

    @Test
    fun `load returns defaults when no config file or env vars exist`() {
        val config = McpConfig.load()
        assertEquals("ollama", config.embeddingProvider)
        assertEquals("http://localhost:11434", config.ollamaBaseUrl)
        assertEquals("qwen3-embedding:8b", config.ollamaCodeModel)
        assertEquals("nomic-embed-text-v2-moe", config.ollamaTextModel)
        assertEquals("voyage-code-3", config.voyageCodeModel)
        assertEquals("voyage-3-large", config.voyageTextModel)
        assertEquals(10, config.resultsPerCorpus)
        assertEquals(5, config.maxRelatedConnections)
    }

    @Test
    fun `configFilePath points to hyve knowledge directory`() {
        val path = McpConfig.configFilePath().absolutePath
        assertTrue(
            path.contains(".hyve") && path.contains("knowledge") && path.endsWith("mcp-config.json"),
            "Config path should be ~/.hyve/knowledge/mcp-config.json, got: $path"
        )
    }
}
