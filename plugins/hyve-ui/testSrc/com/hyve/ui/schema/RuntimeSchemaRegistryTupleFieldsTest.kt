// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.schema

import com.hyve.ui.schema.discovery.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for integration of [TupleFieldResult] into [RuntimeSchemaRegistry].
 */
class RuntimeSchemaRegistryTupleFieldsTest {

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun discoveryResultWith(vararg props: DiscoveredProperty): DiscoveryResult {
        return DiscoveryResult(
            version = "1.0.0",
            discoveredAt = "2026-01-01T00:00:00Z",
            sourceFiles = 1,
            totalElements = 1,
            uniqueElementTypes = 1,
            totalProperties = props.size,
            parseErrors = 0,
            elements = listOf(
                DiscoveredElement(
                    type = "TestElement",
                    category = "OTHER",
                    canHaveChildren = false,
                    occurrences = 1,
                    properties = props.toList()
                )
            )
        )
    }

    private fun tupleResultWith(
        propertyName: String,
        fields: List<TupleFieldInfo>
    ): TupleFieldResult {
        return TupleFieldResult(
            fieldsByProperty = mapOf(propertyName to fields),
            sourceFiles = 1
        )
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    fun `fromDiscoveryResult attaches tupleFields to TUPLE properties`() {
        val result = discoveryResultWith(
            DiscoveredProperty("Style", "TUPLE", false, emptyList(), 10)
        )
        val tupleResult = tupleResultWith("Style", listOf(
            TupleFieldInfo("FontSize", "NUMBER", 5, listOf("14", "18")),
            TupleFieldInfo("TextColor", "COLOR", 3)
        ))

        val registry = RuntimeSchemaRegistry.fromDiscoveryResult(result, tupleResult)
        val schema = registry.getElementSchema("TestElement")

        assertThat(schema).isNotNull()
        val styleProp = schema!!.getProperty("Style")
        assertThat(styleProp).isNotNull()
        assertThat(styleProp!!.tupleFields).hasSize(2)
        assertThat(styleProp.tupleFields[0].name).isEqualTo("FontSize")
        assertThat(styleProp.tupleFields[1].name).isEqualTo("TextColor")
    }

    @Test
    fun `fromDiscoveryResult skips non-TUPLE properties`() {
        val result = discoveryResultWith(
            DiscoveredProperty("Text", "TEXT", false, listOf("hello"), 5)
        )
        val tupleResult = tupleResultWith("Text", listOf(
            TupleFieldInfo("FontSize", "NUMBER", 3)
        ))

        val registry = RuntimeSchemaRegistry.fromDiscoveryResult(result, tupleResult)
        val schema = registry.getElementSchema("TestElement")

        assertThat(schema).isNotNull()
        val textProp = schema!!.getProperty("Text")
        assertThat(textProp).isNotNull()
        // TEXT property should NOT have tuple fields attached (not TUPLE or ANY)
        assertThat(textProp!!.tupleFields).isEmpty()
    }

    @Test
    fun `fromDiscoveryResult with null tupleResult uses DiscoveredProperty tupleFields`() {
        val prePopulated = listOf(
            TupleFieldInfo("FontSize", "NUMBER", 10),
            TupleFieldInfo("RenderBold", "BOOLEAN", 5)
        )

        val result = discoveryResultWith(
            DiscoveredProperty("Style", "TUPLE", false, emptyList(), 15, tupleFields = prePopulated)
        )

        // No live TupleFieldResult — should use pre-populated tupleFields from cache
        val registry = RuntimeSchemaRegistry.fromDiscoveryResult(result, tupleResult = null)
        val schema = registry.getElementSchema("TestElement")

        assertThat(schema).isNotNull()
        val styleProp = schema!!.getProperty("Style")
        assertThat(styleProp).isNotNull()
        assertThat(styleProp!!.tupleFields).hasSize(2)
        assertThat(styleProp.tupleFields[0].name).isEqualTo("FontSize")
        assertThat(styleProp.tupleFields[1].name).isEqualTo("RenderBold")
    }

    @Test
    fun `merge preserves tupleFields from overlay`() {
        // Curated registry: Style with no tuple fields
        val curatedJson = """
        {
            "version": "1.0.0",
            "sourceFiles": 1,
            "totalElements": 1,
            "uniqueElementTypes": 1,
            "discoveredAt": "2026-01-01T00:00:00Z",
            "elements": [
                {
                    "type": "Label",
                    "category": "TEXT",
                    "canHaveChildren": false,
                    "occurrences": 1,
                    "properties": [
                        {"name": "Style", "type": "TUPLE", "required": false, "observedValues": [], "occurrences": 1}
                    ]
                }
            ]
        }
        """.trimIndent()
        val curated = RuntimeSchemaRegistry.loadFromJson(curatedJson)

        // Overlay: Style WITH tuple fields
        val overlayResult = DiscoveryResult(
            version = "1.0.0",
            discoveredAt = "2026-01-01T00:00:00Z",
            sourceFiles = 5,
            totalElements = 10,
            uniqueElementTypes = 1,
            totalProperties = 1,
            parseErrors = 0,
            elements = listOf(
                DiscoveredElement(
                    type = "Label",
                    category = "TEXT",
                    canHaveChildren = false,
                    occurrences = 10,
                    properties = listOf(
                        DiscoveredProperty("Style", "TUPLE", false, emptyList(), 8)
                    )
                )
            )
        )
        val tupleResult = tupleResultWith("Style", listOf(
            TupleFieldInfo("FontSize", "NUMBER", 6, listOf("14", "18"))
        ))
        val overlay = RuntimeSchemaRegistry.fromDiscoveryResult(overlayResult, tupleResult)

        val merged = curated.merge(overlay)
        val schema = merged.getElementSchema("Label")

        assertThat(schema).isNotNull()
        val styleProp = schema!!.getProperty("Style")
        assertThat(styleProp).isNotNull()
        assertThat(styleProp!!.tupleFields).hasSize(1)
        assertThat(styleProp.tupleFields[0].name).isEqualTo("FontSize")
    }

    @Test
    fun `loadFromJson preserves tupleFields from cache`() {
        val jsonString = """
        {
            "version": "1.0.0",
            "discoveredAt": "2026-01-01T00:00:00Z",
            "sourceFiles": 5,
            "totalElements": 10,
            "uniqueElementTypes": 1,
            "totalProperties": 1,
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
                            "occurrences": 4,
                            "tupleFields": [
                                {"name": "FontSize", "type": "NUMBER", "occurrences": 3, "observedValues": ["14", "18"]},
                                {"name": "TextColor", "type": "COLOR", "occurrences": 2}
                            ]
                        }
                    ]
                }
            ],
            "errors": []
        }
        """.trimIndent()

        val registry = RuntimeSchemaRegistry.loadFromJson(jsonString)
        val schema = registry.getElementSchema("Label")

        assertThat(schema).isNotNull()
        val styleProp = schema!!.getProperty("Style")
        assertThat(styleProp).isNotNull()
        assertThat(styleProp!!.tupleFields).hasSize(2)
        assertThat(styleProp.tupleFields[0].name).isEqualTo("FontSize")
        assertThat(styleProp.tupleFields[0].inferredType).isEqualTo("NUMBER")
        assertThat(styleProp.tupleFields[0].observedValues).containsExactly("14", "18")
        assertThat(styleProp.tupleFields[1].name).isEqualTo("TextColor")
        assertThat(styleProp.tupleFields[1].inferredType).isEqualTo("COLOR")
    }
}
