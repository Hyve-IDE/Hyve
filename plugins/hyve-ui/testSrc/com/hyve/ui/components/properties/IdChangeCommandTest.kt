// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.components.properties

import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.common.undo.UndoManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for property inspector ID change: validation, command creation,
 * and undo integration via ReplaceElementCommand.
 */
class IdChangeCommandTest {

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

    private var lastError: String? = null
    private val errorCallback: (String) -> Unit = { lastError = it }

    // -- Valid ID change --

    @Test
    fun `should create command for valid ID change`() {
        // Arrange
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("btn2", el, setOf("btn1"), errorCallback)

        // Assert
        assertThat(command).isNotNull
        assertThat(command!!.newElement.id).isEqualTo(ElementId("btn2"))
        assertThat(command.oldElement.id).isEqualTo(ElementId("btn1"))
        assertThat(lastError).isNull()
    }

    @Test
    fun `should trim whitespace from ID`() {
        // Arrange
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("  myButton  ", el, setOf("btn1"), errorCallback)

        // Assert
        assertThat(command).isNotNull
        assertThat(command!!.newElement.id).isEqualTo(ElementId("myButton"))
    }

    @Test
    fun `should allow underscore-prefixed ID`() {
        // Arrange
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("_private", el, setOf("btn1"), errorCallback)

        // Assert
        assertThat(command).isNotNull
        assertThat(command!!.newElement.id).isEqualTo(ElementId("_private"))
    }

    @Test
    fun `should allow ID with numbers after first character`() {
        // Arrange
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("button123", el, setOf("btn1"), errorCallback)

        // Assert
        assertThat(command).isNotNull
        assertThat(command!!.newElement.id).isEqualTo(ElementId("button123"))
    }

    // -- Empty ID (removal) --

    @Test
    fun `should create command to remove ID when empty string`() {
        // Arrange
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("", el, setOf("btn1"), errorCallback)

        // Assert
        assertThat(command).isNotNull
        assertThat(command!!.newElement.id).isNull()
        assertThat(lastError).isNull()
    }

    @Test
    fun `should create command to remove ID when whitespace only`() {
        // Arrange
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("   ", el, setOf("btn1"), errorCallback)

        // Assert
        assertThat(command).isNotNull
        assertThat(command!!.newElement.id).isNull()
    }

    // -- Invalid ID format --

    @Test
    fun `should reject ID starting with number`() {
        // Arrange
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("123btn", el, setOf("btn1"), errorCallback)

        // Assert
        assertThat(command).isNull()
        assertThat(lastError).contains("letter")
    }

    @Test
    fun `should reject ID with spaces`() {
        // Arrange
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("my button", el, setOf("btn1"), errorCallback)

        // Assert
        assertThat(command).isNull()
        assertThat(lastError).isNotNull
    }

    @Test
    fun `should reject ID with special characters`() {
        // Arrange
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("btn-1", el, setOf("btn1"), errorCallback)

        // Assert
        assertThat(command).isNull()
        assertThat(lastError).isNotNull
    }

    @Test
    fun `should reject ID with dot`() {
        // Arrange
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("btn.name", el, setOf("btn1"), errorCallback)

        // Assert
        assertThat(command).isNull()
        assertThat(lastError).isNotNull
    }

    // -- Duplicate ID --

    @Test
    fun `should reject duplicate ID`() {
        // Arrange
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("existingId", el, setOf("btn1", "existingId"), errorCallback)

        // Assert
        assertThat(command).isNull()
        assertThat(lastError).contains("already exists")
    }

    @Test
    fun `should allow reusing own current ID`() {
        // Arrange — user types the same ID back
        val el = element(id = "btn1")

        // Act
        val command = buildIdChangeCommand("btn1", el, setOf("btn1", "lbl1"), errorCallback)

        // Assert
        assertThat(command).isNotNull
        assertThat(command!!.newElement.id).isEqualTo(ElementId("btn1"))
        assertThat(lastError).isNull()
    }

    @Test
    fun `should check duplicates when element has no current ID`() {
        // Arrange — element without an ID tries to use an existing one
        val el = element()

        // Act
        val command = buildIdChangeCommand("existingId", el, setOf("existingId"), errorCallback)

        // Assert
        assertThat(command).isNull()
        assertThat(lastError).contains("already exists")
    }

    @Test
    fun `should allow new ID when element has no current ID and no conflict`() {
        // Arrange
        val el = element()

        // Act
        val command = buildIdChangeCommand("newId", el, setOf("otherId"), errorCallback)

        // Assert
        assertThat(command).isNotNull
        assertThat(command!!.newElement.id).isEqualTo(ElementId("newId"))
    }

    // -- Undo integration --

    @Test
    fun `ID rename should be undoable via command system`() {
        // Arrange
        val original = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Hello")))
        val root = tree(original)
        val command = buildIdChangeCommand("btnRenamed", original, setOf("btn1"), errorCallback)!!
        val undoManager = UndoManager<UIElement>()

        // Act
        val afterExecute = undoManager.execute(command, root, allowMerge = false)!!
        val afterUndo = undoManager.undo(afterExecute)

        // Assert — original ID restored
        assertThat(afterUndo).isNotNull
        assertThat(afterUndo!!.children[0].id).isEqualTo(ElementId("btn1"))
        assertThat(afterUndo.children[0].getProperty("Text")).isEqualTo(PropertyValue.Text("Hello"))
    }

    @Test
    fun `ID removal should be undoable via command system`() {
        // Arrange
        val original = element(id = "btn1")
        val root = tree(original)
        val command = buildIdChangeCommand("", original, setOf("btn1"), errorCallback)!!
        val undoManager = UndoManager<UIElement>()

        // Act
        val afterExecute = undoManager.execute(command, root, allowMerge = false)!!
        val afterUndo = undoManager.undo(afterExecute)

        // Assert — original ID restored
        assertThat(afterUndo).isNotNull
        assertThat(afterUndo!!.children[0].id).isEqualTo(ElementId("btn1"))
    }

    @Test
    fun `ID rename should support redo after undo`() {
        // Arrange
        val original = element(id = "btn1")
        val root = tree(original)
        val command = buildIdChangeCommand("btnNew", original, setOf("btn1"), errorCallback)!!
        val undoManager = UndoManager<UIElement>()
        val afterExecute = undoManager.execute(command, root, allowMerge = false)!!
        val afterUndo = undoManager.undo(afterExecute)!!

        // Act
        val afterRedo = undoManager.redo(afterUndo)

        // Assert — renamed ID restored
        assertThat(afterRedo).isNotNull
        assertThat(afterRedo!!.children[0].id).isEqualTo(ElementId("btnNew"))
    }

    // -- Command preserves element data --

    @Test
    fun `command should preserve all element properties except ID`() {
        // Arrange
        val el = element(
            id = "btn1",
            type = "Button",
            properties = mapOf(
                "Text" to PropertyValue.Text("Click me"),
                "Background" to PropertyValue.Text("#FF0000")
            )
        )

        // Act
        val command = buildIdChangeCommand("btnRenamed", el, setOf("btn1"), errorCallback)!!

        // Assert
        assertThat(command.newElement.type).isEqualTo(ElementType("Button"))
        assertThat(command.newElement.getProperty("Text")).isEqualTo(PropertyValue.Text("Click me"))
        assertThat(command.newElement.getProperty("Background")).isEqualTo(PropertyValue.Text("#FF0000"))
        assertThat(command.newElement.id).isEqualTo(ElementId("btnRenamed"))
    }
}
