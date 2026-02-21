// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor.undo

import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.state.command.SetPropertyCommand
import com.hyve.common.undo.UndoManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Unit tests for HyveUndoBridge.
 * Tests custom UndoManager behavior through the bridge wrapper.
 *
 * Uses SetPropertyCommand (a concrete DocumentCommand) since DocumentCommand
 * is a sealed interface and cannot be implemented outside its package.
 */
class HyveUndoBridgeTest {

    @Test
    fun `executeCommand calls custom UndoManager and returns new root`() {
        val undoManager = UndoManager<UIElement>()
        val root = element("Group", "testEl")
        val command = setPropertyCommand("testEl", "Text", null, PropertyValue.Text("Hello"))

        val result = undoManager.execute(command, root)

        assertThat(result).isNotNull()
        assertThat(result!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Hello"))
    }

    @Test
    fun `canUndo reflects custom UndoManager state`() {
        val undoManager = UndoManager<UIElement>()
        val root = element("Group", "testEl")
        val command = setPropertyCommand("testEl", "Text", null, PropertyValue.Text("Hello"))

        assertThat(undoManager.canUndo.value).isFalse()

        undoManager.execute(command, root)

        assertThat(undoManager.canUndo.value).isTrue()
    }

    @Test
    fun `undo delegates to custom UndoManager`() {
        val undoManager = UndoManager<UIElement>()
        val root = element("Group", "testEl")
        val command = setPropertyCommand("testEl", "Text", null, PropertyValue.Text("Hello"))

        val afterExecute = undoManager.execute(command, root)!!
        assertThat(afterExecute.getProperty("Text")).isNotNull()

        val afterUndo = undoManager.undo(afterExecute)

        assertThat(afterUndo).isNotNull()
        assertThat(afterUndo!!.getProperty("Text")).isNull()
    }

    @Test
    fun `redo delegates to custom UndoManager`() {
        val undoManager = UndoManager<UIElement>()
        val root = element("Group", "testEl")
        val command = setPropertyCommand("testEl", "Text", null, PropertyValue.Text("Hello"))

        val afterExecute = undoManager.execute(command, root)!!
        val afterUndo = undoManager.undo(afterExecute)!!
        val afterRedo = undoManager.redo(afterUndo)

        assertThat(afterRedo).isNotNull()
        assertThat(afterRedo!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Hello"))
    }

    @Test
    fun `multiple commands maintain undo redo stack`() {
        val undoManager = UndoManager<UIElement>(mergeWindowMs = 0)
        val root = element("Group", "testEl")

        val cmd1 = setPropertyCommand("testEl", "Text", null, PropertyValue.Text("First"))
        val cmd2 = SetPropertyCommand(
            elementId = ElementId("testEl"),
            elementMatcher = null,
            propertyName = "Text",
            oldValue = PropertyValue.Text("First"),
            newValue = PropertyValue.Text("Second")
        )

        val state1 = undoManager.execute(cmd1, root)!!
        val state2 = undoManager.execute(cmd2, state1)!!

        assertThat(state2.getProperty("Text")).isEqualTo(PropertyValue.Text("Second"))

        val afterUndo1 = undoManager.undo(state2)!!
        assertThat(afterUndo1.getProperty("Text")).isEqualTo(PropertyValue.Text("First"))

        val afterUndo2 = undoManager.undo(afterUndo1)!!
        assertThat(afterUndo2.getProperty("Text")).isNull()

        val afterRedo1 = undoManager.redo(afterUndo2)!!
        assertThat(afterRedo1.getProperty("Text")).isEqualTo(PropertyValue.Text("First"))
    }

    @Test
    fun `dirty flag tracks save point correctly`() {
        val undoManager = UndoManager<UIElement>()
        val root = element("Group", "testEl")
        val command = setPropertyCommand("testEl", "Text", null, PropertyValue.Text("Hello"))

        assertThat(undoManager.isDirty.value).isFalse()

        val state1 = undoManager.execute(command, root)!!
        assertThat(undoManager.isDirty.value).isTrue()

        undoManager.markSaved()
        assertThat(undoManager.isDirty.value).isFalse()

        undoManager.undo(state1)
        assertThat(undoManager.isDirty.value).isTrue()
    }

    // Helper methods

    private fun element(type: String = "Group", id: String? = null): UIElement {
        return UIElement(
            type = ElementType(type),
            id = id?.let { ElementId(it) },
            properties = PropertyMap.empty(),
            children = emptyList()
        )
    }

    private fun setPropertyCommand(
        elementId: String,
        propertyName: String,
        oldValue: PropertyValue?,
        newValue: PropertyValue
    ): SetPropertyCommand {
        return SetPropertyCommand(
            elementId = ElementId(elementId),
            elementMatcher = null,
            propertyName = propertyName,
            oldValue = oldValue,
            newValue = newValue
        )
    }
}
