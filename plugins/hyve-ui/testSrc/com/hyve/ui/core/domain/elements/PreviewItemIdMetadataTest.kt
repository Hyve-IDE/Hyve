// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.core.domain.elements

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.result.Result
import com.hyve.ui.exporter.UIExporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PreviewItemIdMetadataTest {

    // -- Helpers --

    private fun element(
        type: String = "Group",
        id: String? = null,
        properties: PropertyMap = PropertyMap.empty(),
        children: List<UIElement> = emptyList(),
        metadata: ElementMetadata = ElementMetadata()
    ): UIElement = UIElement(
        type = ElementType(type),
        id = id?.let { ElementId(it) },
        properties = properties,
        children = children,
        metadata = metadata
    )

    private fun document(root: UIElement): UIDocument = UIDocument(
        imports = emptyMap(),
        styles = emptyMap(),
        root = root
    )

    private fun export(doc: UIDocument): String {
        val result = UIExporter().export(doc)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        return (result as Result.Success).value
    }

    // -- Tests --

    @Test
    fun `ElementMetadata default previewItemId is null`() {
        val metadata = ElementMetadata()
        assertThat(metadata.previewItemId).isNull()
    }

    @Test
    fun `ElementMetadata copy sets previewItemId`() {
        val metadata = ElementMetadata().copy(previewItemId = "item_sword")
        assertThat(metadata.previewItemId).isEqualTo("item_sword")
    }

    @Test
    fun `UIElement with metadata previewItemId does not include it in property map`() {
        val elem = element(
            type = "Group",
            id = "ItemPreview",
            metadata = ElementMetadata(previewItemId = "item_sword")
        )

        assertThat(elem.getProperty("PreviewItemId")).isNull()
        assertThat(elem.metadata.previewItemId).isEqualTo("item_sword")
    }

    @Test
    fun `exporter does not emit PreviewItemId from metadata`() {
        val root = element(
            type = "Group",
            id = "ItemPreview",
            metadata = ElementMetadata(previewItemId = "item_sword")
        )
        val doc = document(root)

        val output = export(doc)

        assertThat(output).doesNotContain("PreviewItemId")
        assertThat(output).doesNotContain("item_sword")
    }

    @Test
    fun `exporter emits PreviewItemId only when present in property map`() {
        // Element has previewItemId in BOTH metadata and property map.
        // Exporter should only see the property map value.
        val root = element(
            type = "Group",
            id = "ItemPreview",
            properties = PropertyMap.of(
                "PreviewItemId" to PropertyValue.Text("item_from_property")
            ),
            metadata = ElementMetadata(previewItemId = "item_from_metadata")
        )
        val doc = document(root)

        val output = export(doc)

        // The property map value gets exported
        assertThat(output).contains("PreviewItemId")
        assertThat(output).contains("item_from_property")
        // The metadata value does NOT leak into export
        assertThat(output).doesNotContain("item_from_metadata")
    }
}
