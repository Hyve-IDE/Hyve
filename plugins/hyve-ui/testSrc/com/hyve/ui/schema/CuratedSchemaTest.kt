// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.schema

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for curated schema loading and merging.
 */
class CuratedSchemaTest {

    @Test
    fun `loadFromResource returns non-empty registry`() {
        val registry = RuntimeSchemaRegistry.loadFromResource()

        assertThat(registry.isLoaded).isTrue()
        assertThat(registry.getAllElementTypes()).isNotEmpty()
    }

    @Test
    fun `Group element has Anchor property with type ANCHOR`() {
        val registry = RuntimeSchemaRegistry.loadFromResource()

        val groupSchema = registry.getElementSchema("Group")
        assertThat(groupSchema).isNotNull()

        val anchorProperty = groupSchema!!.getProperty("Anchor")
        assertThat(anchorProperty).isNotNull()
        assertThat(anchorProperty!!.type).isEqualTo(PropertyType.ANCHOR)
    }

    @Test
    fun `Padding property has type ANCHOR`() {
        val registry = RuntimeSchemaRegistry.loadFromResource()

        val groupSchema = registry.getElementSchema("Group")
        assertThat(groupSchema).isNotNull()

        val paddingProperty = groupSchema!!.getProperty("Padding")
        assertThat(paddingProperty).isNotNull()
        assertThat(paddingProperty!!.type).isEqualTo(PropertyType.ANCHOR)
    }

    @Test
    fun `merge with empty overlay returns curated types`() {
        val curated = RuntimeSchemaRegistry.loadFromResource()
        val empty = RuntimeSchemaRegistry.empty()

        val merged = curated.merge(empty)

        assertThat(merged.getAllElementTypes()).isEqualTo(curated.getAllElementTypes())
    }

    @Test
    fun `merge with overlay containing new element includes both curated and overlay`() {
        val curated = RuntimeSchemaRegistry.loadFromResource()

        val overlayJson = """
        {
          "version": "1.0.0",
          "sourceFiles": 1,
          "totalElements": 1,
          "uniqueElementTypes": 1,
          "discoveredAt": "2026-02-09T00:00:00Z",
          "elements": [
            {"type": "NewElement", "category": "OTHER", "canHaveChildren": false, "occurrences": 1, "properties": []}
          ]
        }
        """.trimIndent()
        val overlay = RuntimeSchemaRegistry.loadFromJson(overlayJson)

        val merged = curated.merge(overlay)

        assertThat(merged.getElementSchema("NewElement")).isNotNull()
        assertThat(merged.getElementSchema("Group")).isNotNull()
    }

    @Test
    fun `malformed JSON falls back to empty registry`() {
        // Note: This intentionally triggers a "Failed to parse schema JSON" warning — that's the behavior under test
        val registry = RuntimeSchemaRegistry.loadFromJson("not valid json")

        assertThat(registry.isLoaded).isFalse()
    }
}
