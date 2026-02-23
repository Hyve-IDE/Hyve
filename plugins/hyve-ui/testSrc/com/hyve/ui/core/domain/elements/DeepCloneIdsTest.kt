// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.core.domain.elements

import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests that duplicating an element via [UIElement.mapDescendants] produces
 * unique IDs for the entire subtree, not just the top-level element.
 *
 * Regression: previously only the top-level element got `_copy` appended,
 * causing duplicate child IDs that corrupted rename, selection, and delete.
 */
class DeepCloneIdsTest {

    // -- Helpers --

    private fun element(
        type: String = "Button",
        id: String? = null,
        children: List<UIElement> = emptyList()
    ): UIElement {
        return UIElement(
            type = ElementType(type),
            id = id?.let { ElementId(it) },
            properties = PropertyMap.empty(),
            children = children
        )
    }

    /** Mirrors the deep-clone logic in HierarchyTree.duplicateElement */
    private fun deepClone(element: UIElement): UIElement {
        return element.mapDescendants { el ->
            if (el.id != null) {
                el.copy(id = ElementId("${el.id.value}_copy"))
            } else {
                el
            }
        }
    }

    // -- Tests --

    @Test
    fun `deep clone should suffix top-level ID`() {
        // Arrange
        val original = element(id = "frame")

        // Act
        val clone = deepClone(original)

        // Assert
        assertThat(clone.id).isEqualTo(ElementId("frame_copy"))
    }

    @Test
    fun `deep clone should suffix all child IDs`() {
        // Arrange
        val child = element(id = "bar", type = "ProgressBar")
        val container = element(id = "container", type = "Group", children = listOf(child))
        val frame = element(id = "frame", type = "Group", children = listOf(container))

        // Act
        val clone = deepClone(frame)

        // Assert
        assertThat(clone.id).isEqualTo(ElementId("frame_copy"))
        assertThat(clone.children[0].id).isEqualTo(ElementId("container_copy"))
        assertThat(clone.children[0].children[0].id).isEqualTo(ElementId("bar_copy"))
    }

    @Test
    fun `deep clone should not modify original element`() {
        // Arrange
        val child = element(id = "child")
        val parent = element(id = "parent", type = "Group", children = listOf(child))

        // Act
        deepClone(parent)

        // Assert — original is unchanged (UIElement is a data class, immutable)
        assertThat(parent.id).isEqualTo(ElementId("parent"))
        assertThat(parent.children[0].id).isEqualTo(ElementId("child"))
    }

    @Test
    fun `deep clone should preserve elements without IDs`() {
        // Arrange
        val withId = element(id = "labeled")
        val withoutId = element() // no ID
        val root = element(id = "root", type = "Group", children = listOf(withId, withoutId))

        // Act
        val clone = deepClone(root)

        // Assert
        assertThat(clone.id).isEqualTo(ElementId("root_copy"))
        assertThat(clone.children[0].id).isEqualTo(ElementId("labeled_copy"))
        assertThat(clone.children[1].id).isNull()
    }

    @Test
    fun `deep clone should preserve tree structure`() {
        // Arrange
        val a = element(id = "a")
        val b = element(id = "b")
        val group = element(id = "group", type = "Group", children = listOf(a, b))

        // Act
        val clone = deepClone(group)

        // Assert
        assertThat(clone.children).hasSize(2)
        assertThat(clone.type).isEqualTo(ElementType("Group"))
        assertThat(clone.children[0].type).isEqualTo(ElementType("Button"))
        assertThat(clone.children[1].type).isEqualTo(ElementType("Button"))
    }

    @Test
    fun `deep clone should handle deeply nested hierarchy`() {
        // Arrange — 4 levels deep, mimicking TorsoBarFrame → TorsoBarContainer → TorsoBar
        val level3 = element(id = "TorsoBar", type = "ProgressBar")
        val level2 = element(id = "TorsoBarContainer", type = "Group", children = listOf(level3))
        val level1 = element(id = "TorsoBarFrame", type = "Group", children = listOf(level2))

        // Act
        val clone = deepClone(level1)

        // Assert — every level has _copy suffix
        assertThat(clone.id).isEqualTo(ElementId("TorsoBarFrame_copy"))
        assertThat(clone.children[0].id).isEqualTo(ElementId("TorsoBarContainer_copy"))
        assertThat(clone.children[0].children[0].id).isEqualTo(ElementId("TorsoBar_copy"))
    }

    @Test
    fun `cloned IDs should not collide with original IDs`() {
        // Arrange
        val child1 = element(id = "btn1")
        val child2 = element(id = "btn2")
        val group = element(id = "toolbar", type = "Group", children = listOf(child1, child2))

        // Act
        val clone = deepClone(group)

        // Assert — collect all IDs and verify no overlap
        val originalIds = listOf("toolbar", "btn1", "btn2")
        val clonedIds = listOf(
            clone.id!!.value,
            clone.children[0].id!!.value,
            clone.children[1].id!!.value
        )
        assertThat(clonedIds).doesNotContainAnyElementsOf(originalIds)
    }

    @Test
    fun `deep clone should preserve properties`() {
        // Arrange
        val original = element(id = "styled", type = "Label")
            .setProperty("Text", com.hyve.ui.core.domain.properties.PropertyValue.Text("Hello"))

        // Act
        val clone = deepClone(original)

        // Assert
        assertThat(clone.id).isEqualTo(ElementId("styled_copy"))
        assertThat(clone.getProperty("Text"))
            .isEqualTo(com.hyve.ui.core.domain.properties.PropertyValue.Text("Hello"))
    }
}
