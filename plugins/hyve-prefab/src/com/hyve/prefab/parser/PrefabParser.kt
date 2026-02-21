package com.hyve.prefab.parser

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.hyve.prefab.domain.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.StringWriter

/**
 * Streaming parser for .prefab.json files using Jackson core.
 *
 * Designed for large prefab files (50MB+, 50K+ blocks):
 * - Streams through blocks/fluids arrays counting entries without materialization
 * - Fully materializes entities array (typically 10-100 entries)
 * - Tracks byte offsets for the entities array to enable byte-splice export
 *
 * Memory: ~2MB overhead for a 50MB file (entity data + block histogram + byte offsets).
 */
object PrefabParser {

    private val jsonFactory = JsonFactory()
    private val kotlinxJson = Json { ignoreUnknownKeys = true }

    /**
     * Parse a .prefab.json file from raw bytes.
     *
     * @param bytes The raw file bytes
     * @return Parsed [PrefabDocument]
     * @throws PrefabParseException if the file format is invalid
     */
    fun parse(bytes: ByteArray): PrefabDocument {
        var version = 0
        var blockIdVersion = 0
        var anchorX = 0
        var anchorY = 0
        var anchorZ = 0
        var blockCount = 0
        val blockTypeCounts = mutableMapOf<String, Int>()
        var fluidCount = 0
        val fluidTypes = mutableSetOf<String>()
        val entities = mutableListOf<PrefabEntity>()
        val componentBlocks = mutableListOf<PrefabEntity>()
        var entitiesStartOffset = -1L
        var entitiesEndOffset = -1L
        var entityIdCounter = 0L

        val parser = jsonFactory.createParser(bytes)
        parser.use { jp ->
            expectToken(jp, jp.nextToken(), JsonToken.START_OBJECT, "root")

            while (jp.nextToken() != JsonToken.END_OBJECT) {
                val fieldName = jp.currentName()
                jp.nextToken() // move to value

                when (fieldName) {
                    "version" -> version = jp.intValue
                    "blockIdVersion" -> blockIdVersion = jp.intValue
                    "anchorX" -> anchorX = jp.intValue
                    "anchorY" -> anchorY = jp.intValue
                    "anchorZ" -> anchorZ = jp.intValue

                    "blocks" -> {
                        expectToken(jp, jp.currentToken, JsonToken.START_ARRAY, "blocks")
                        while (jp.nextToken() != JsonToken.END_ARRAY) {
                            blockCount++
                            var blockName: String? = null
                            var blockX = 0
                            var blockY = 0
                            var blockZ = 0
                            var componentsJson: String? = null
                            var compStartOffset = -1L
                            var compEndOffset = -1L

                            while (jp.nextToken() != JsonToken.END_OBJECT) {
                                val blockField = jp.currentName()
                                jp.nextToken()
                                when (blockField) {
                                    "name" -> blockName = jp.text
                                    "x" -> blockX = jp.intValue
                                    "y" -> blockY = jp.intValue
                                    "z" -> blockZ = jp.intValue
                                    "components" -> {
                                        compStartOffset = findObjectStartByte(bytes, jp.currentLocation().byteOffset)
                                        val writer = StringWriter(512)
                                        val gen = jsonFactory.createGenerator(writer)
                                        gen.copyCurrentStructure(jp)
                                        gen.flush()
                                        gen.close()
                                        compEndOffset = jp.currentLocation().byteOffset - 1
                                        componentsJson = writer.toString()
                                    }
                                    else -> jp.skipChildren()
                                }
                            }
                            if (blockName != null) {
                                blockTypeCounts[blockName] = (blockTypeCounts[blockName] ?: 0) + 1
                            }
                            if (componentsJson != null && blockName != null) {
                                val jsonObj = kotlinxJson.parseToJsonElement(componentsJson).jsonObject
                                val entityId = EntityId(entityIdCounter++)
                                componentBlocks.add(
                                    PrefabEntity.fromJsonObject(jsonObj, entityId).copy(
                                        blockOrigin = BlockOrigin(blockName, BlockPos(blockX, blockY, blockZ)),
                                        sourceByteRange = compStartOffset.toInt()..compEndOffset.toInt(),
                                    )
                                )
                            }
                        }
                    }

                    "fluids" -> {
                        expectToken(jp, jp.currentToken, JsonToken.START_ARRAY, "fluids")
                        while (jp.nextToken() != JsonToken.END_ARRAY) {
                            fluidCount++
                            while (jp.nextToken() != JsonToken.END_OBJECT) {
                                val fluidField = jp.currentName()
                                jp.nextToken()
                                if (fluidField == "name") {
                                    fluidTypes.add(jp.text)
                                } else {
                                    jp.skipChildren()
                                }
                            }
                        }
                    }

                    "entities" -> {
                        expectToken(jp, jp.currentToken, JsonToken.START_ARRAY, "entities")
                        entitiesStartOffset = jp.currentLocation().byteOffset
                        // Find the '[' — we need byte offset of the array start
                        // Jackson reports location after the token, so the '[' is at (offset - 1)
                        entitiesStartOffset = findArrayStartByte(bytes, entitiesStartOffset)

                        while (jp.nextToken() != JsonToken.END_ARRAY) {
                            val entityStartOffset = findObjectStartByte(bytes, jp.currentLocation().byteOffset)

                            // Copy current entity object to string for kotlinx parsing
                            val writer = StringWriter(512)
                            val gen = jsonFactory.createGenerator(writer)
                            gen.copyCurrentStructure(jp)
                            gen.flush()
                            gen.close()

                            val entityEndOffset = jp.currentLocation().byteOffset - 1

                            val entityJsonStr = writer.toString()
                            val jsonElement = kotlinxJson.parseToJsonElement(entityJsonStr)
                            val jsonObj = jsonElement.jsonObject

                            val entityId = EntityId(entityIdCounter++)
                            entities.add(
                                PrefabEntity.fromJsonObject(jsonObj, entityId).copy(
                                    sourceByteRange = entityStartOffset.toInt()..entityEndOffset.toInt()
                                )
                            )
                        }

                        // The ']' end offset — Jackson points past the ']'
                        entitiesEndOffset = jp.currentLocation().byteOffset - 1
                    }

                    else -> jp.skipChildren()
                }
            }
        }

        // If no entities array was found, we need to handle that
        val byteRange = if (entitiesStartOffset >= 0 && entitiesEndOffset >= 0) {
            entitiesStartOffset.toInt()..entitiesEndOffset.toInt()
        } else {
            // No entities array — the range will be empty; exporter will insert one
            0..(-1)
        }

        return PrefabDocument(
            version = version,
            blockIdVersion = blockIdVersion,
            anchor = BlockPos(anchorX, anchorY, anchorZ),
            entities = entities,
            fluidSummary = PrefabFluidSummary(fluidCount, fluidTypes),
            blockData = StreamedBlockData(blockCount, blockTypeCounts),
            rawBytes = bytes,
            entitiesByteRange = byteRange,
            entityIdCounter = entityIdCounter,
            componentBlocks = componentBlocks,
        )
    }

    /**
     * Find the byte offset of the '[' that starts the array.
     * Jackson's location after reading START_ARRAY points past the '[',
     * so we scan backwards from the reported offset.
     */
    private fun findArrayStartByte(bytes: ByteArray, reportedOffset: Long): Long {
        var i = (reportedOffset - 1).toInt().coerceIn(0, bytes.size - 1)
        while (i >= 0 && bytes[i].toInt().toChar() != '[') {
            i--
        }
        return i.toLong()
    }

    /**
     * Find the byte offset of the '{' that starts the current entity object.
     * Jackson's location after reading START_OBJECT points past the '{',
     * so we scan backwards from the reported offset.
     */
    private fun findObjectStartByte(bytes: ByteArray, reportedOffset: Long): Long {
        var i = (reportedOffset - 1).toInt().coerceIn(0, bytes.size - 1)
        while (i >= 0 && bytes[i].toInt().toChar() != '{') {
            i--
        }
        return i.toLong()
    }

    private fun expectToken(jp: JsonParser, actual: JsonToken?, expected: JsonToken, context: String) {
        if (actual != expected) {
            throw PrefabParseException(
                "Expected $expected at $context but got $actual at ${jp.currentLocation()}"
            )
        }
    }
}

class PrefabParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
