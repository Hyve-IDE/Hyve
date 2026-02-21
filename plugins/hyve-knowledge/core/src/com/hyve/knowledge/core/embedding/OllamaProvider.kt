// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.embedding

import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.core.logging.StdoutLogProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class OllamaProvider(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "nomic-embed-text",
    private val batchSize: Int = 10,
    private val maxChars: Int = 4000,
    private val log: LogProvider = StdoutLogProvider,
) : EmbeddingProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient.newHttpClient()

    override val modelId: String get() = model

    override val dimension: Int
        get() = KNOWN_DIMENSIONS[model] ?: 768

    override suspend fun embed(texts: List<String>): List<FloatArray> = coroutineScope {
        val results = mutableListOf<FloatArray>()
        val batches = texts.chunked(batchSize)

        for ((batchIdx, batch) in batches.withIndex()) {
            if (batchIdx > 0) delay(50)

            val embeddings = batch.map { text ->
                async(Dispatchers.IO) {
                    embedSingle(text.take(maxChars))
                }
            }.awaitAll()

            results.addAll(embeddings)
        }

        results
    }

    override suspend fun embedQuery(query: String): FloatArray {
        return embedSingle(query.take(maxChars))
    }

    override suspend fun validate() {
        withContext(Dispatchers.IO) {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrl/api/tags"))
                    .GET()
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 200) {
                    throw EmbeddingException.ConnectionFailed(baseUrl)
                }

                val body = response.body()
                if (!body.contains(model)) {
                    throw EmbeddingException.ModelNotFound(model)
                }
            } catch (e: EmbeddingException) {
                throw e
            } catch (e: Exception) {
                throw EmbeddingException.ConnectionFailed(baseUrl, e)
            }
        }
    }

    private suspend fun embedSingle(text: String): FloatArray = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject {
            put("model", model)
            put("input", text)
            put("truncate", true)
        }.toString()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/embed"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw EmbeddingException.ApiError(response.statusCode(), response.body())
        }

        val parsed = json.parseToJsonElement(response.body()).jsonObject
        parsed["embeddings"]!!.jsonArray[0].jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
    }

    companion object {
        private val KNOWN_DIMENSIONS = mapOf(
            "qwen3-embedding" to 4096,
            "qwen3-embedding:8b" to 4096,
            "qwen3-embedding:4b" to 2560,
            "qwen3-embedding:0.6b" to 1024,
            "nomic-embed-text" to 768,
            "nomic-embed-text-v2-moe" to 768,
            "nomic-embed-code" to 3584,
            "jina-code-embeddings-1.5b" to 1536,
            "jina-code-embeddings-0.5b" to 896,
            "snowflake-arctic-embed2" to 1024,
            "mxbai-embed-large" to 1024,
            "all-minilm" to 384,
        )
    }
}
