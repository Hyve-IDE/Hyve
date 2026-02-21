package com.hyve.prefab.exporter

import com.hyve.prefab.domain.PrefabDocument
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream

/**
 * Exports a [PrefabDocument] back to .prefab.json bytes using byte-splice.
 *
 * Only the entities array is re-serialized. The blocks and fluids arrays
 * are preserved byte-for-byte from the original file, ensuring no data loss
 * even for 50MB+ files with 50K+ blocks.
 */
object PrefabExporter {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * Export the document to bytes.
     *
     * If the document has a valid [PrefabDocument.entitiesByteRange],
     * performs byte-splice: replaces only the entities array in the original bytes.
     * Otherwise, builds a complete JSON document.
     */
    fun export(doc: PrefabDocument): ByteArray {
        val entitiesJson = buildEntitiesArray(doc)
        val entitiesBytes = json.encodeToString(JsonArray.serializer(), entitiesJson)
            .toByteArray(Charsets.UTF_8)

        if (doc.entitiesByteRange.isEmpty()) {
            // No entities array existed in original — need full rebuild
            return buildFullDocument(doc, entitiesBytes)
        }

        // Byte-splice: replace only the entities array
        val raw = doc.rawBytes
        val range = doc.entitiesByteRange
        val out = ByteArrayOutputStream(raw.size)
        out.write(raw, 0, range.first)
        out.write(entitiesBytes)
        out.write(raw, range.last + 1, raw.size - range.last - 1)
        return out.toByteArray()
    }

    private fun buildEntitiesArray(doc: PrefabDocument): JsonArray = buildJsonArray {
        for (entity in doc.entities) {
            add(entity.toJsonObject())
        }
    }

    /**
     * Fallback: build a complete document when we can't byte-splice
     * (e.g., the original file had no entities array).
     * This inserts an entities array before the closing '}'.
     */
    private fun buildFullDocument(doc: PrefabDocument, entitiesBytes: ByteArray): ByteArray {
        val raw = doc.rawBytes
        val rawStr = raw.toString(Charsets.UTF_8)

        // Find the last '}' to insert before it
        val lastBrace = rawStr.lastIndexOf('}')
        if (lastBrace < 0) {
            // Degenerate case — return a minimal valid document
            return buildMinimalDocument(doc, entitiesBytes)
        }

        // Check if we need a comma before the new field
        val beforeBrace = rawStr.substring(0, lastBrace).trimEnd()
        val needsComma = beforeBrace.isNotEmpty() && !beforeBrace.endsWith("{") && !beforeBrace.endsWith(",")

        val out = ByteArrayOutputStream(raw.size + entitiesBytes.size + 50)
        out.write(beforeBrace.toByteArray(Charsets.UTF_8))
        if (needsComma) out.write(",".toByteArray())
        out.write("\n  \"entities\": ".toByteArray(Charsets.UTF_8))
        out.write(entitiesBytes)
        out.write("\n}\n".toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    private fun buildMinimalDocument(doc: PrefabDocument, entitiesBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(entitiesBytes.size + 200)
        val header = """
            {
              "version": ${doc.version},
              "blockIdVersion": ${doc.blockIdVersion},
              "anchorX": ${doc.anchor.x},
              "anchorY": ${doc.anchor.y},
              "anchorZ": ${doc.anchor.z},
              "entities":
        """.trimIndent()
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(entitiesBytes)
        out.write("\n}\n".toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }
}
