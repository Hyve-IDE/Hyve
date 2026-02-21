// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.state.command.ReplaceElementCommand
import com.hyve.common.undo.UndoManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for the Composer entry/exit flow logic (spec 10 FR-2, FR-3).
 *
 * These tests verify the data flow between the canvas and the Composer modal:
 * - No-change detection (unchanged element produces no undo entry)
 * - Undo/redo integration (Composer changes are a single undo step)
 * - Element-deleted-while-open detection
 */
class ComposerEntryFlowTest {

    // -- Helpers --

    private fun element(
        type: String = "Button",
        id: String? = null,
        properties: Map<String, PropertyValue> = emptyMap(),
        children: List<UIElement> = emptyList()
    ): UIElement {
        val propMap = properties.entries.fold(PropertyMap.empty()) { map, (k, v) ->
            map.set(PropertyName(k), v)
        }
        return UIElement(
            type = ElementType(type),
            id = id?.let { ElementId(it) },
            properties = propMap,
            children = children
        )
    }

    private fun tree(vararg children: UIElement): UIElement {
        return element(type = "Root", id = "root", children = children.toList())
    }

    // -- No-change detection --

    @Test
    fun `should detect no changes when element is identical`() {
        // Arrange
        val original = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Hello")))

        // Act — simulate applyTo returning an identical element
        val afterComposer = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Hello")))

        // Assert — UIElement data class equality detects no change
        assertThat(afterComposer).isEqualTo(original)
    }

    @Test
    fun `should detect changes when property value differs`() {
        // Arrange
        val original = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Hello")))

        // Act
        val afterComposer = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("World")))

        // Assert
        assertThat(afterComposer).isNotEqualTo(original)
    }

    @Test
    fun `should detect changes when ID is modified`() {
        // Arrange
        val original = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Hello")))

        // Act
        val afterComposer = element(id = "btnRenamed", properties = mapOf("Text" to PropertyValue.Text("Hello")))

        // Assert
        assertThat(afterComposer).isNotEqualTo(original)
    }

    // -- Undo integration --

    @Test
    fun `should create single undo entry for Composer changes`() {
        // Arrange
        val original = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Old")))
        val root = tree(original)
        val modified = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("New")))
        val command = ReplaceElementCommand.forElement(original, modified)
        val undoManager = UndoManager<UIElement>()

        // Act
        val afterExecute = undoManager.execute(command, root, allowMerge = false)

        // Assert — one undo entry, element is updated
        assertThat(afterExecute).isNotNull
        assertThat(afterExecute!!.children[0].getProperty("Text")).isEqualTo(PropertyValue.Text("New"))
        assertThat(undoManager.canUndo.value).isTrue()
    }

    @Test
    fun `undo should restore original element after Composer edit`() {
        // Arrange
        val original = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Old")))
        val root = tree(original)
        val modified = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("New")))
        val command = ReplaceElementCommand.forElement(original, modified)
        val undoManager = UndoManager<UIElement>()
        val afterExecute = undoManager.execute(command, root, allowMerge = false)!!

        // Act
        val afterUndo = undoManager.undo(afterExecute)

        // Assert
        assertThat(afterUndo).isNotNull
        assertThat(afterUndo!!.children[0].getProperty("Text")).isEqualTo(PropertyValue.Text("Old"))
        assertThat(undoManager.canRedo.value).isTrue()
    }

    @Test
    fun `redo should re-apply Composer changes after undo`() {
        // Arrange
        val original = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Old")))
        val root = tree(original)
        val modified = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("New")))
        val command = ReplaceElementCommand.forElement(original, modified)
        val undoManager = UndoManager<UIElement>()
        val afterExecute = undoManager.execute(command, root, allowMerge = false)!!
        val afterUndo = undoManager.undo(afterExecute)!!

        // Act
        val afterRedo = undoManager.redo(afterUndo)

        // Assert
        assertThat(afterRedo).isNotNull
        assertThat(afterRedo!!.children[0].getProperty("Text")).isEqualTo(PropertyValue.Text("New"))
    }

    // -- Element-deleted-while-open --

    @Test
    fun `should detect element still exists in tree by ID`() {
        // Arrange
        val target = element(id = "btn1")
        val root = tree(target)

        // Act & Assert — element exists
        assertThat(root.findDescendantById(ElementId("btn1"))).isNotNull
    }

    @Test
    fun `should detect element was deleted from tree`() {
        // Arrange
        val root = tree() // empty root, btn1 was deleted

        // Act & Assert — element no longer exists
        assertThat(root.findDescendantById(ElementId("btn1"))).isNull()
    }

    @Test
    fun `should handle ID change in undo round-trip`() {
        // Arrange — user changed element ID from btn1 to btnNew in Composer
        val original = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Old")))
        val root = tree(original)
        val modified = element(id = "btnNew", properties = mapOf("Text" to PropertyValue.Text("New")))
        val command = ReplaceElementCommand.forElement(original, modified)
        val undoManager = UndoManager<UIElement>()

        // Act
        val afterExecute = undoManager.execute(command, root, allowMerge = false)!!
        val afterUndo = undoManager.undo(afterExecute)!!

        // Assert — original ID and value restored
        assertThat(afterUndo.children[0].id).isEqualTo(ElementId("btn1"))
        assertThat(afterUndo.children[0].getProperty("Text")).isEqualTo(PropertyValue.Text("Old"))
    }
}
