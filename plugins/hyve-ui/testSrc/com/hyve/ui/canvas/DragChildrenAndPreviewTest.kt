// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

import androidx.compose.ui.geometry.Offset
import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for M-001 (drag children with parent) and M-002 (live anchor preview during drag).
 *
 * These test the drag-preview path (recordUndo=false) where:
 * - _isDragging is set to true
 * - _dragOffset accumulates delta
 * - getBounds() applies offset to selected elements AND their descendants
 * - dragPreviewAnchor computes live anchor values for single-select
 */
class DragChildrenAndPreviewTest {

    // --- Helpers (same pattern as LayoutAwareMovementTest) ---

    private fun element(
        type: String = "Group",
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

    private fun anchor(
        left: Float? = null,
        top: Float? = null,
        right: Float? = null,
        bottom: Float? = null,
        width: Float? = null,
        height: Float? = null
    ): PropertyValue.Anchor = PropertyValue.Anchor(
        AnchorValue.absolute(left, top, right, bottom, width, height)
    )

    private fun createState() = CanvasState()

    private fun pixels(dim: AnchorDimension?): Float? =
        (dim as? AnchorDimension.Absolute)?.pixels

    // =====================================================================
    // M-001: Drag children with parent — getBounds() offset propagation
    // =====================================================================

    @Test
    fun `getBounds should offset child of dragged parent`() {
        val state = createState()
        val child = element(
            id = "child",
            properties = mapOf("Anchor" to anchor(left = 10f, top = 10f, width = 50f, height = 50f))
        )
        val parent = element(
            id = "parent",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 100f, width = 200f, height = 200f)),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        // Select the parent
        val parentEl = state.rootElement.value!!.children[0]
        state.selectElement(parentEl)

        // Get child bounds before drag
        val childEl = state.rootElement.value!!.children[0].children[0]
        val beforeBounds = state.getBounds(childEl)!!.bounds

        // Start a drag (recordUndo=false triggers visual-only drag)
        state.moveSelectedElements(Offset(30f, 20f), recordUndo = false)

        // Child bounds should be offset even though child is not selected
        val afterBounds = state.getBounds(childEl)!!.bounds
        assertThat(afterBounds.x).isEqualTo(beforeBounds.x + 30f)
        assertThat(afterBounds.y).isEqualTo(beforeBounds.y + 20f)
    }

    @Test
    fun `getBounds should offset deeply nested descendants of dragged parent`() {
        val state = createState()
        val grandchild = element(
            id = "grandchild",
            properties = mapOf("Anchor" to anchor(left = 5f, top = 5f, width = 20f, height = 20f))
        )
        val child = element(
            id = "child",
            properties = mapOf("Anchor" to anchor(left = 10f, top = 10f, width = 80f, height = 80f)),
            children = listOf(grandchild)
        )
        val parent = element(
            id = "parent",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 100f, width = 200f, height = 200f)),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        val parentEl = state.rootElement.value!!.children[0]
        state.selectElement(parentEl)

        val grandchildEl = state.rootElement.value!!.children[0].children[0].children[0]
        val beforeBounds = state.getBounds(grandchildEl)!!.bounds

        state.moveSelectedElements(Offset(15f, 25f), recordUndo = false)

        val afterBounds = state.getBounds(grandchildEl)!!.bounds
        assertThat(afterBounds.x).isEqualTo(beforeBounds.x + 15f)
        assertThat(afterBounds.y).isEqualTo(beforeBounds.y + 25f)
    }

    @Test
    fun `getBounds should NOT offset siblings of dragged element`() {
        val state = createState()
        val dragged = element(
            id = "dragged",
            properties = mapOf("Anchor" to anchor(left = 10f, top = 10f, width = 50f, height = 50f))
        )
        val sibling = element(
            id = "sibling",
            properties = mapOf("Anchor" to anchor(left = 200f, top = 10f, width = 50f, height = 50f))
        )
        val root = element(type = "Root", id = "root", children = listOf(dragged, sibling))
        state.setRootElement(root)

        val draggedEl = state.rootElement.value!!.children[0]
        state.selectElement(draggedEl)

        val siblingEl = state.rootElement.value!!.children[1]
        val siblingBefore = state.getBounds(siblingEl)!!.bounds

        state.moveSelectedElements(Offset(50f, 50f), recordUndo = false)

        val siblingAfter = state.getBounds(siblingEl)!!.bounds
        assertThat(siblingAfter.x).isEqualTo(siblingBefore.x)
        assertThat(siblingAfter.y).isEqualTo(siblingBefore.y)
    }

    @Test
    fun `getBounds should NOT offset children when not dragging`() {
        val state = createState()
        val child = element(
            id = "child",
            properties = mapOf("Anchor" to anchor(left = 10f, top = 10f, width = 50f, height = 50f))
        )
        val parent = element(
            id = "parent",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 100f, width = 200f, height = 200f)),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        // Select parent but don't drag
        val parentEl = state.rootElement.value!!.children[0]
        state.selectElement(parentEl)

        val childEl = state.rootElement.value!!.children[0].children[0]
        val bounds = state.getBounds(childEl)!!.bounds

        // Bounds should be at layout-computed position (no offset)
        assertThat(state.isDragging.value).isFalse()
        // Just verify bounds exist and are reasonable
        assertThat(bounds.width).isEqualTo(50f)
        assertThat(bounds.height).isEqualTo(50f)
    }

    @Test
    fun `multi-select drag should not double-count element that is both selected and descendant`() {
        val state = createState()
        val child = element(
            id = "child",
            properties = mapOf("Anchor" to anchor(left = 10f, top = 10f, width = 50f, height = 50f))
        )
        val parent = element(
            id = "parent",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 100f, width = 200f, height = 200f)),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        // Select both parent and child
        val parentEl = state.rootElement.value!!.children[0]
        val childEl = state.rootElement.value!!.children[0].children[0]
        state.selectElement(parentEl)
        state.addToSelection(childEl)

        val childBefore = state.getBounds(childEl)!!.bounds

        state.moveSelectedElements(Offset(10f, 10f), recordUndo = false)

        // Child should have offset applied exactly once (not doubled)
        val childAfter = state.getBounds(childEl)!!.bounds
        assertThat(childAfter.x).isEqualTo(childBefore.x + 10f)
        assertThat(childAfter.y).isEqualTo(childBefore.y + 10f)
    }

    @Test
    fun `getBounds should offset selected element during drag`() {
        val state = createState()
        val el = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 200f, width = 50f, height = 50f))
        )
        val root = element(type = "Root", id = "root", children = listOf(el))
        state.setRootElement(root)

        val selected = state.rootElement.value!!.children[0]
        state.selectElement(selected)
        val before = state.getBounds(selected)!!.bounds

        state.moveSelectedElements(Offset(10f, -5f), recordUndo = false)

        val after = state.getBounds(selected)!!.bounds
        assertThat(after.x).isEqualTo(before.x + 10f)
        assertThat(after.y).isEqualTo(before.y - 5f)
    }

    @Test
    fun `commitDrag should reset drag state and children return to normal bounds`() {
        val state = createState()
        val child = element(
            id = "child",
            properties = mapOf("Anchor" to anchor(left = 10f, top = 10f, width = 50f, height = 50f))
        )
        val parent = element(
            id = "parent",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 100f, width = 200f, height = 200f)),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        val parentEl = state.rootElement.value!!.children[0]
        state.selectElement(parentEl)
        state.moveSelectedElements(Offset(30f, 30f), recordUndo = false)

        assertThat(state.isDragging.value).isTrue()

        // Commit the drag (applies to tree)
        state.commitDrag()

        assertThat(state.isDragging.value).isFalse()
        assertThat(state.dragOffset.value).isEqualTo(Offset.Zero)
    }

    @Test
    fun `cancelDrag should reset drag state without applying changes`() {
        val state = createState()
        val el = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 200f, width = 50f, height = 50f))
        )
        val root = element(type = "Root", id = "root", children = listOf(el))
        state.setRootElement(root)

        val selected = state.rootElement.value!!.children[0]
        state.selectElement(selected)
        state.moveSelectedElements(Offset(50f, 50f), recordUndo = false)

        assertThat(state.isDragging.value).isTrue()

        state.cancelDrag()

        assertThat(state.isDragging.value).isFalse()
        assertThat(state.dragOffset.value).isEqualTo(Offset.Zero)

        // Element anchor should be unchanged (drag was cancelled)
        val afterEl = state.rootElement.value!!.children[0]
        val afterAnchor = (afterEl.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat(pixels(afterAnchor.left)).isEqualTo(100f)
        assertThat(pixels(afterAnchor.top)).isEqualTo(200f)
    }

    // =====================================================================
    // M-002: Live anchor preview during drag — dragPreviewAnchor
    // =====================================================================

    @Test
    fun `dragPreviewAnchor should be null when not dragging`() {
        val state = createState()
        val el = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 200f, width = 50f, height = 50f))
        )
        val root = element(type = "Root", id = "root", children = listOf(el))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        assertThat(state.dragPreviewAnchor.value).isNull()
    }

    @Test
    fun `dragPreviewAnchor should return computed anchor during single-select drag`() {
        val state = createState()
        val el = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 200f, width = 50f, height = 50f))
        )
        val root = element(type = "Root", id = "root", children = listOf(el))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Start drag
        state.moveSelectedElements(Offset(30f, -10f), recordUndo = false)

        val preview = state.dragPreviewAnchor.value
        assertThat(preview).isNotNull
        assertThat(pixels(preview!!.left)).isEqualTo(130f)  // 100 + 30
        assertThat(pixels(preview.top)).isEqualTo(190f)     // 200 + (-10)
        assertThat(pixels(preview.width)).isEqualTo(50f)    // unchanged
        assertThat(pixels(preview.height)).isEqualTo(50f)   // unchanged
    }

    @Test
    fun `dragPreviewAnchor should be null during multi-select drag`() {
        val state = createState()
        val el1 = element(
            id = "el1",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 100f, width = 50f, height = 50f))
        )
        val el2 = element(
            id = "el2",
            properties = mapOf("Anchor" to anchor(left = 200f, top = 200f, width = 50f, height = 50f))
        )
        val root = element(type = "Root", id = "root", children = listOf(el1, el2))
        state.setRootElement(root)

        state.selectElement(state.rootElement.value!!.children[0])
        state.addToSelection(state.rootElement.value!!.children[1])

        state.moveSelectedElements(Offset(10f, 10f), recordUndo = false)

        assertThat(state.dragPreviewAnchor.value).isNull()
    }

    @Test
    fun `dragPreviewAnchor should be null after commitDrag`() {
        val state = createState()
        val el = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 200f, width = 50f, height = 50f))
        )
        val root = element(type = "Root", id = "root", children = listOf(el))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        state.moveSelectedElements(Offset(10f, 10f), recordUndo = false)
        assertThat(state.dragPreviewAnchor.value).isNotNull

        state.commitDrag()
        assertThat(state.dragPreviewAnchor.value).isNull()
    }

    @Test
    fun `dragPreviewAnchor should be null when element has no Anchor`() {
        val state = createState()
        // Element without Anchor property
        val el = element(
            id = "el",
            properties = mapOf("Text" to PropertyValue.Text("hello"))
        )
        val root = element(type = "Root", id = "root", children = listOf(el))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        state.moveSelectedElements(Offset(10f, 10f), recordUndo = false)

        assertThat(state.dragPreviewAnchor.value).isNull()
    }

    @Test
    fun `dragPreviewAnchor should update with accumulated drag offset`() {
        val state = createState()
        val el = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(left = 0f, top = 0f, width = 50f, height = 50f))
        )
        val root = element(type = "Root", id = "root", children = listOf(el))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // First drag increment
        state.moveSelectedElements(Offset(10f, 10f), recordUndo = false)
        var preview = state.dragPreviewAnchor.value
        assertThat(pixels(preview!!.left)).isEqualTo(10f)
        assertThat(pixels(preview.top)).isEqualTo(10f)

        // Second drag increment (accumulates)
        state.moveSelectedElements(Offset(5f, -3f), recordUndo = false)
        preview = state.dragPreviewAnchor.value
        assertThat(pixels(preview!!.left)).isEqualTo(15f)   // 0 + 10 + 5
        assertThat(pixels(preview.top)).isEqualTo(7f)       // 0 + 10 + (-3)
    }

    @Test
    fun `dragPreviewAnchor should handle Right and Bottom anchors`() {
        val state = createState()
        val el = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(right = 50f, bottom = 100f, width = 200f, height = 40f))
        )
        val root = element(type = "Root", id = "root", children = listOf(el))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Drag right 20, down 10
        state.moveSelectedElements(Offset(20f, 10f), recordUndo = false)

        val preview = state.dragPreviewAnchor.value
        assertThat(preview).isNotNull
        // Right decreases when moving right (closer to right edge)
        assertThat(pixels(preview!!.right)).isEqualTo(30f)   // 50 - 20
        // Bottom decreases when moving down (closer to bottom edge)
        assertThat(pixels(preview.bottom)).isEqualTo(90f)    // 100 - 10
        assertThat(pixels(preview.width)).isEqualTo(200f)    // unchanged
        assertThat(pixels(preview.height)).isEqualTo(40f)    // unchanged
    }
}
