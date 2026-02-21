// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EditorMetadataTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // -- Helpers --

    private fun uiFile(): File = File(tempFolder.root, "test.ui").also { it.createNewFile() }

    // -- Tests --

    @Test
    fun `load returns defaults when sidecar does not exist`() {
        val file = uiFile()
        val metadata = EditorMetadataIO.load(file)

        assertThat(metadata.editorState.showGrid).isTrue()
        assertThat(metadata.editorState.zoom).isEqualTo(1.0f)
        assertThat(metadata.editorState.scrollX).isEqualTo(0f)
        assertThat(metadata.editorState.scrollY).isEqualTo(0f)
        assertThat(metadata.elementMetadata).isEmpty()
    }

    @Test
    fun `save then load round-trips editor state`() {
        val file = uiFile()
        val original = EditorMetadata(
            editorState = EditorViewState(
                showGrid = false,
                zoom = 2.0f,
                scrollX = 100f,
                scrollY = 200f
            )
        )

        EditorMetadataIO.save(file, original)
        val loaded = EditorMetadataIO.load(file)

        assertThat(loaded.editorState.showGrid).isFalse()
        assertThat(loaded.editorState.zoom).isEqualTo(2.0f)
        assertThat(loaded.editorState.scrollX).isEqualTo(100f)
        assertThat(loaded.editorState.scrollY).isEqualTo(200f)
    }

    @Test
    fun `save then load round-trips element metadata`() {
        val file = uiFile()
        val original = EditorMetadata(
            elementMetadata = mapOf(
                "element_1" to ElementEditorData(previewItemId = "item_sword"),
                "element_2" to ElementEditorData(previewItemId = "item_shield")
            )
        )

        EditorMetadataIO.save(file, original)
        val loaded = EditorMetadataIO.load(file)

        assertThat(loaded.elementMetadata).hasSize(2)
        assertThat(loaded.elementMetadata["element_1"]?.previewItemId).isEqualTo("item_sword")
        assertThat(loaded.elementMetadata["element_2"]?.previewItemId).isEqualTo("item_shield")
    }

    @Test
    fun `save then load round-trips empty element metadata`() {
        val file = uiFile()
        val original = EditorMetadata(elementMetadata = emptyMap())

        EditorMetadataIO.save(file, original)
        val loaded = EditorMetadataIO.load(file)

        assertThat(loaded.elementMetadata).isEmpty()
    }

    @Test
    fun `load ignores corrupted sidecar file`() {
        val file = uiFile()
        val sidecar = EditorMetadataIO.sidecarFile(file)
        sidecar.writeText("this is not valid json {{{")

        val metadata = EditorMetadataIO.load(file)

        assertThat(metadata).isEqualTo(EditorMetadata())
    }

    @Test
    fun `load ignores unknown JSON keys`() {
        val file = uiFile()
        val sidecar = EditorMetadataIO.sidecarFile(file)
        sidecar.writeText("""
            {
                "editorState": {
                    "showGrid": false,
                    "zoom": 3.0,
                    "scrollX": 50.0,
                    "scrollY": 75.0,
                    "futureFeature": true
                },
                "elementMetadata": {},
                "unknownTopLevel": "hello"
            }
        """.trimIndent())

        val metadata = EditorMetadataIO.load(file)

        assertThat(metadata.editorState.showGrid).isFalse()
        assertThat(metadata.editorState.zoom).isEqualTo(3.0f)
        assertThat(metadata.editorState.scrollX).isEqualTo(50.0f)
        assertThat(metadata.editorState.scrollY).isEqualTo(75.0f)
    }

    @Test
    fun `sidecar file path is derived from ui file path`() {
        val file = File("/some/path/to/screen.ui")
        val sidecar = EditorMetadataIO.sidecarFile(file)

        assertThat(sidecar.name).isEqualTo("screen.ui.meta")
        assertThat(sidecar.absolutePath).endsWith("screen.ui.meta")
    }

    @Test
    fun `null previewItemId is omitted from JSON`() {
        val file = uiFile()
        val original = EditorMetadata(
            elementMetadata = mapOf(
                "elem1" to ElementEditorData(previewItemId = null)
            )
        )

        EditorMetadataIO.save(file, original)
        val rawJson = EditorMetadataIO.sidecarFile(file).readText()

        // The element entry should exist but previewItemId key should be absent
        assertThat(rawJson).contains("elem1")
        assertThat(rawJson).doesNotContain("previewItemId")
    }
}
