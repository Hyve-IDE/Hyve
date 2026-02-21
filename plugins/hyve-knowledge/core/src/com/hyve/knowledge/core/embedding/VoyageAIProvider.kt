// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.embedding

import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.core.logging.StdoutLogProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class VoyageAIProvider(
    private val apiKey: String,
    private val model: String = "voyage-code-3",
    private val batchSize: Int = 128,
    private val maxChars: Int = 32000,
    private val maxRetries: Int = 3,
    private val log: LogProvider = StdoutLogProvider,
) : EmbeddingProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient.newHttpClient()

    override val modelId: String get() = model

    override val dimension: Int
        get() = KNOWN_DIMENSIONS[model] ?: 1024

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (apiKey.isBlank()) throw EmbeddingException.InvalidApiKey()

        val results = mutableListOf<FloatArray>()
        val batches = texts.chunked(batchSize)

        for ((batchIdx, batch) in batches.withIndex()) {
            if (batchIdx > 0) delay(100)
            val truncated = batch.map { it.take(maxChars) }
            val embeddings = embedBatch(truncated, "document")
            results.addAll(embeddings)
        }

        return results
    }

    override suspend fun embedQuery(query: String): FloatArray {
        if (apiKey.isBlank()) throw EmbeddingException.InvalidApiKey()
        return embedBatch(listOf(query.take(maxChars)), "query").first()
    }

    override suspend fun validate() {
        if (apiKey.isBlank()) throw EmbeddingException.InvalidApiKey()
        embedBatch(listOf("test"), "query")
    }

    private suspend fun embedBatch(
        texts: List<String>,
        inputType: String,
    ): List<FloatArray> = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject {
            put("model", model)
            put("input", JsonArray(texts.map { JsonPrimitive(it) }))
            put("input_type", inputType)
        }.toString()

        var lastException: Exception? = null
        for (attempt in 0 until maxRetries) {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer $apiKey")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())

                when (response.statusCode()) {
                    200 -> {
                        val parsed = json.parseToJsonElement(response.body()).jsonObject
                        return@withContext parsed["data"]!!.jsonArray
                            .sortedBy { it.jsonObject["index"]!!.jsonPrimitive.int }
                            .map { entry ->
                                entry.jsonObject["embedding"]!!.jsonArray
                                    .map { it.jsonPrimitive.float }
                                    .toFloatArray()
                            }
                    }
                    401 -> throw EmbeddingException.InvalidApiKey()
                    429 -> {
                        val retryAfter = response.headers().firstValue("retry-after")
                            .map { it.toLongOrNull()?.times(1000) ?: 5000L }
                            .orElse(5000L)
                        log.warn("VoyageAI rate limited, retrying in ${retryAfter}ms")
                        delay(retryAfter)
                        lastException = EmbeddingException.RateLimited(retryAfter)
                        continue
                    }
                    else -> throw EmbeddingException.ApiError(response.statusCode(), response.body())
                }
            } catch (e: EmbeddingException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    val backoff = (1000L shl attempt)
                    delay(backoff)
                }
            }
        }
        throw EmbeddingException.ConnectionFailed(API_URL, lastException)
    }

    companion object {
        private const val API_URL = "https://api.voyageai.com/v1/embeddings"

        private val KNOWN_DIMENSIONS = mapOf(
            "voyage-code-3" to 1024,
            "voyage-3-large" to 1024,
            "voyage-4-large" to 1024,
        )
    }
}
