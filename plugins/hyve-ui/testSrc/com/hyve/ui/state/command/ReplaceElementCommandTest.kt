// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.state.command

import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ReplaceElementCommandTest {

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

    // -- Execute tests --

    @Test
    fun `execute should replace element matched by ID`() {
        // Arrange
        val old = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Old")))
        val new = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("New")))
        val root = tree(old)
        val command = ReplaceElementCommand.forElement(old, new)

        // Act
        val result = command.execute(root)

        // Assert
        assertThat(result).isNotNull
        assertThat(result!!.children).hasSize(1)
        assertThat(result.children[0].getProperty("Text")).isEqualTo(PropertyValue.Text("New"))
    }

    @Test
    fun `execute should replace element matched by reference when no ID`() {
        // Arrange
        val old = element(properties = mapOf("Text" to PropertyValue.Text("Old")))
        val new = element(properties = mapOf("Text" to PropertyValue.Text("New")))
        val root = tree(old)
        val command = ReplaceElementCommand.forElement(old, new)

        // Act
        val result = command.execute(root)

        // Assert
        assertThat(result).isNotNull
        assertThat(result!!.children).hasSize(1)
        assertThat(result.children[0].getProperty("Text")).isEqualTo(PropertyValue.Text("New"))
    }

    @Test
    fun `execute should preserve sibling elements`() {
        // Arrange
        val target = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Old")))
        val sibling = element(id = "lbl1", type = "Label", properties = mapOf("Text" to PropertyValue.Text("Keep")))
        val new = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("New")))
        val root = tree(target, sibling)
        val command = ReplaceElementCommand.forElement(target, new)

        // Act
        val result = command.execute(root)

        // Assert
        assertThat(result).isNotNull
        assertThat(result!!.children).hasSize(2)
        assertThat(result.children[0].getProperty("Text")).isEqualTo(PropertyValue.Text("New"))
        assertThat(result.children[1].getProperty("Text")).isEqualTo(PropertyValue.Text("Keep"))
    }

    @Test
    fun `execute should replace deeply nested element`() {
        // Arrange
        val deep = element(id = "deep1", properties = mapOf("Text" to PropertyValue.Text("Old")))
        val parent = element(id = "group1", type = "Group", children = listOf(deep))
        val root = tree(parent)
        val new = element(id = "deep1", properties = mapOf("Text" to PropertyValue.Text("New")))
        val command = ReplaceElementCommand.forElement(deep, new)

        // Act
        val result = command.execute(root)

        // Assert
        assertThat(result).isNotNull
        val nested = result!!.children[0].children[0]
        assertThat(nested.getProperty("Text")).isEqualTo(PropertyValue.Text("New"))
    }

    // -- Undo tests --

    @Test
    fun `undo should restore original element`() {
        // Arrange
        val old = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Old")))
        val new = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("New")))
        val root = tree(old)
        val command = ReplaceElementCommand.forElement(old, new)
        val afterExecute = command.execute(root)!!

        // Act
        val result = command.undo(afterExecute)

        // Assert
        assertThat(result).isNotNull
        assertThat(result!!.children[0].getProperty("Text")).isEqualTo(PropertyValue.Text("Old"))
    }

    @Test
    fun `undo should find element by new ID when ID was changed`() {
        // Arrange — user changed the element ID inside the Composer
        val old = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Old")))
        val new = element(id = "btnRenamed", properties = mapOf("Text" to PropertyValue.Text("New")))
        val root = tree(old)
        val command = ReplaceElementCommand.forElement(old, new)
        val afterExecute = command.execute(root)!!

        // Act
        val result = command.undo(afterExecute)

        // Assert
        assertThat(result).isNotNull
        assertThat(result!!.children[0].id).isEqualTo(ElementId("btn1"))
        assertThat(result.children[0].getProperty("Text")).isEqualTo(PropertyValue.Text("Old"))
    }

    @Test
    fun `execute then undo should restore original tree`() {
        // Arrange
        val child1 = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Hello")))
        val child2 = element(id = "lbl1", type = "Label", properties = mapOf("Text" to PropertyValue.Text("World")))
        val root = tree(child1, child2)
        val modified = element(id = "btn1", properties = mapOf("Text" to PropertyValue.Text("Changed")))
        val command = ReplaceElementCommand.forElement(child1, modified)

        // Act
        val afterExecute = command.execute(root)!!
        val afterUndo = command.undo(afterExecute)

        // Assert
        assertThat(afterUndo).isEqualTo(root)
    }

    // -- Factory tests --

    @Test
    fun `forElement factory should set correct IDs`() {
        // Arrange
        val old = element(id = "btn1")
        val new = element(id = "btnNew")

        // Act
        val command = ReplaceElementCommand.forElement(old, new)

        // Assert
        assertThat(command.oldElementId).isEqualTo(ElementId("btn1"))
        assertThat(command.newElementId).isEqualTo(ElementId("btnNew"))
        assertThat(command.elementMatcher).isNull()
    }

    @Test
    fun `forElement factory should set matcher when old element has no ID`() {
        // Arrange
        val old = element()
        val new = element(id = "btnNew")

        // Act
        val command = ReplaceElementCommand.forElement(old, new)

        // Assert
        assertThat(command.oldElementId).isNull()
        assertThat(command.newElementId).isEqualTo(ElementId("btnNew"))
        assertThat(command.elementMatcher).isNotNull
    }

    @Test
    fun `description should contain element display name`() {
        // Arrange
        val old = element(id = "header")
        val new = element(id = "header")

        // Act
        val command = ReplaceElementCommand.forElement(old, new)

        // Assert
        assertThat(command.description).contains("Button #header")
        assertThat(command.description).contains("Composer")
    }
}
