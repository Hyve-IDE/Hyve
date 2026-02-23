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

class ReorderElementCommandTest {

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

    // -- Forward (index +1) --

    @Test
    fun `forward should move element one step later in children list`() {
        val a = element(id = "a")
        val b = element(id = "b")
        val c = element(id = "c")
        val root = tree(a, b, c)
        val command = ReorderElementCommand.forRootChild(a, oldIndex = 0, newIndex = 1)

        val result = command.execute(root)

        assertThat(result).isNotNull
        assertThat(result!!.children.map { it.id!!.value }).containsExactly("b", "a", "c")
    }

    // -- Backward (index -1) --

    @Test
    fun `backward should move element one step earlier in children list`() {
        val a = element(id = "a")
        val b = element(id = "b")
        val c = element(id = "c")
        val root = tree(a, b, c)
        val command = ReorderElementCommand.forRootChild(c, oldIndex = 2, newIndex = 1)

        val result = command.execute(root)

        assertThat(result).isNotNull
        assertThat(result!!.children.map { it.id!!.value }).containsExactly("a", "c", "b")
    }

    // -- To Front (last index) --

    @Test
    fun `to front should move element to last position`() {
        val a = element(id = "a")
        val b = element(id = "b")
        val c = element(id = "c")
        val root = tree(a, b, c)
        val command = ReorderElementCommand.forRootChild(a, oldIndex = 0, newIndex = 2)

        val result = command.execute(root)

        assertThat(result).isNotNull
        assertThat(result!!.children.map { it.id!!.value }).containsExactly("b", "c", "a")
    }

    // -- To Back (index 0) --

    @Test
    fun `to back should move element to first position`() {
        val a = element(id = "a")
        val b = element(id = "b")
        val c = element(id = "c")
        val root = tree(a, b, c)
        val command = ReorderElementCommand.forRootChild(c, oldIndex = 2, newIndex = 0)

        val result = command.execute(root)

        assertThat(result).isNotNull
        assertThat(result!!.children.map { it.id!!.value }).containsExactly("c", "a", "b")
    }

    // -- Undo --

    @Test
    fun `undo should restore original order after forward`() {
        val a = element(id = "a")
        val b = element(id = "b")
        val c = element(id = "c")
        val root = tree(a, b, c)
        val command = ReorderElementCommand.forRootChild(a, oldIndex = 0, newIndex = 2)

        val afterExecute = command.execute(root)!!
        val afterUndo = command.undo(afterExecute)

        assertThat(afterUndo).isNotNull
        assertThat(afterUndo!!.children.map { it.id!!.value }).containsExactly("a", "b", "c")
    }

    @Test
    fun `undo should restore original order after backward`() {
        val a = element(id = "a")
        val b = element(id = "b")
        val c = element(id = "c")
        val root = tree(a, b, c)
        val command = ReorderElementCommand.forRootChild(c, oldIndex = 2, newIndex = 0)

        val afterExecute = command.execute(root)!!
        val afterUndo = command.undo(afterExecute)

        assertThat(afterUndo).isNotNull
        assertThat(afterUndo!!.children.map { it.id!!.value }).containsExactly("a", "b", "c")
    }

    // -- Nested elements --

    @Test
    fun `forward should work for nested children`() {
        val childA = element(id = "childA")
        val childB = element(id = "childB")
        val group = element(type = "Group", id = "group", children = listOf(childA, childB))
        val root = tree(group)
        val command = ReorderElementCommand.forElement(group, childA, oldIndex = 0, newIndex = 1)

        val result = command.execute(root)

        assertThat(result).isNotNull
        val nestedChildren = result!!.children[0].children
        assertThat(nestedChildren.map { it.id!!.value }).containsExactly("childB", "childA")
    }

    @Test
    fun `undo should work for nested children`() {
        val childA = element(id = "childA")
        val childB = element(id = "childB")
        val group = element(type = "Group", id = "group", children = listOf(childA, childB))
        val root = tree(group)
        val command = ReorderElementCommand.forElement(group, childA, oldIndex = 0, newIndex = 1)

        val afterExecute = command.execute(root)!!
        val afterUndo = command.undo(afterExecute)

        assertThat(afterUndo).isNotNull
        val nestedChildren = afterUndo!!.children[0].children
        assertThat(nestedChildren.map { it.id!!.value }).containsExactly("childA", "childB")
    }

    // -- Edge cases --

    @Test
    fun `same index should return unchanged tree`() {
        val a = element(id = "a")
        val b = element(id = "b")
        val root = tree(a, b)
        val command = ReorderElementCommand.forRootChild(a, oldIndex = 0, newIndex = 0)

        val result = command.execute(root)

        assertThat(result).isNotNull
        assertThat(result!!.children.map { it.id!!.value }).containsExactly("a", "b")
    }

    @Test
    fun `out of bounds index should return null`() {
        val a = element(id = "a")
        val root = tree(a)
        val command = ReorderElementCommand.forRootChild(a, oldIndex = 0, newIndex = 5)

        val result = command.execute(root)

        assertThat(result).isNull()
    }

    // -- Description --

    @Test
    fun `description should contain Reorder`() {
        val a = element(id = "myButton")
        val command = ReorderElementCommand.forRootChild(a, oldIndex = 0, newIndex = 1)
        assertThat(command.description).contains("Reorder")
    }
}
