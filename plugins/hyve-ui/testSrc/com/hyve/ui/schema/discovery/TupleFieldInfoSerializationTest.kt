// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.schema.discovery

import kotlinx.serialization.json.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for JSON round-trip serialization of [TupleFieldInfo] and
 * [DiscoveredProperty.tupleFields].
 */
class TupleFieldInfoSerializationTest {

    @Test
    fun `TupleFieldInfo round-trips through JSON`() {
        val original = TupleFieldInfo(
            name = "FontSize",
            inferredType = "NUMBER",
            occurrences = 42,
            observedValues = listOf("14", "16", "24")
        )

        val json = original.toJsonObject()
        val restored = TupleFieldInfo.fromJsonObject(json)

        assertThat(restored.name).isEqualTo(original.name)
        assertThat(restored.inferredType).isEqualTo(original.inferredType)
        assertThat(restored.occurrences).isEqualTo(original.occurrences)
        assertThat(restored.observedValues).isEqualTo(original.observedValues)
    }

    @Test
    fun `fromJsonObject with missing observedValues defaults to emptyList`() {
        val json = buildJsonObject {
            put("name", "X")
            put("type", "NUMBER")
            put("occurrences", 1)
        }

        val field = TupleFieldInfo.fromJsonObject(json)

        assertThat(field.name).isEqualTo("X")
        assertThat(field.inferredType).isEqualTo("NUMBER")
        assertThat(field.occurrences).isEqualTo(1)
        assertThat(field.observedValues).isEmpty()
    }

    @Test
    fun `DiscoveredProperty with tupleFields serializes and deserializes`() {
        val tupleFields = listOf(
            TupleFieldInfo("FontSize", "NUMBER", 10, listOf("14", "18")),
            TupleFieldInfo("TextColor", "COLOR", 5, listOf("#ffffff"))
        )

        val original = DiscoveredProperty(
            name = "Style",
            type = "TUPLE",
            required = false,
            observedValues = emptyList(),
            occurrences = 15,
            tupleFields = tupleFields
        )

        val json = original.toJsonObject()
        val restored = DiscoveredProperty.fromJsonObject(json)

        assertThat(restored.tupleFields).hasSize(2)
        assertThat(restored.tupleFields[0].name).isEqualTo("FontSize")
        assertThat(restored.tupleFields[0].inferredType).isEqualTo("NUMBER")
        assertThat(restored.tupleFields[0].observedValues).containsExactly("14", "18")
        assertThat(restored.tupleFields[1].name).isEqualTo("TextColor")
    }

    @Test
    fun `DiscoveredProperty without tupleFields key defaults to emptyList`() {
        // Simulate old-format JSON that pre-dates tupleFields
        val json = buildJsonObject {
            put("name", "Background")
            put("type", "TUPLE")
            put("required", false)
            putJsonArray("observedValues") {}
            put("occurrences", 3)
            // no "tupleFields" key
        }

        val restored = DiscoveredProperty.fromJsonObject(json)

        assertThat(restored.tupleFields).isEmpty()
    }

    @Test
    fun `full DiscoveryResult without tupleFields loads without error`() {
        // Old cache format: elements have properties but no tupleFields
        val jsonString = """
        {
            "version": "1.0.0",
            "discoveredAt": "2026-01-01T00:00:00Z",
            "sourceFiles": 5,
            "totalElements": 10,
            "uniqueElementTypes": 3,
            "totalProperties": 20,
            "parseErrors": 0,
            "elements": [
                {
                    "type": "Label",
                    "category": "TEXT",
                    "canHaveChildren": false,
                    "occurrences": 5,
                    "properties": [
                        {
                            "name": "Style",
                            "type": "TUPLE",
                            "required": false,
                            "observedValues": [],
                            "occurrences": 4
                        }
                    ]
                }
            ],
            "errors": []
        }
        """.trimIndent()

        val result = DiscoveryResult.fromJson(jsonString)

        assertThat(result.elements).hasSize(1)
        assertThat(result.elements[0].properties[0].tupleFields).isEmpty()
    }
}
