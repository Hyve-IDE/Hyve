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

class LayoutAwareMovementTest {

    // --- Helpers ---

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
    ): PropertyValue.Anchor = PropertyValue.Anchor(AnchorValue.absolute(left, top, right, bottom, width, height))

    private fun createState() = CanvasState()

    private fun pixels(dim: AnchorDimension?): Float? = (dim as? AnchorDimension.Absolute)?.pixels

    // =====================================================================
    // FR-1: Move handles all anchor edges
    // =====================================================================

    @Test
    fun `move should update Bottom anchor correctly`() {
        // Arrange: element anchored to bottom edge
        val state = createState()
        val child = element(
            id = "mana",
            properties = mapOf("Anchor" to anchor(bottom = 120f, width = 260f, height = 52f))
        )
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Act: move right 50, up 30
        state.moveSelectedElements(Offset(50f, -30f))

        // Assert
        val moved = state.rootElement.value!!.children[0]
        val movedAnchor = (moved.getProperty("Anchor") as PropertyValue.Anchor).anchor
        // Bottom decreases when moving up (delta.y is negative, so -(-30) = +30)
        assertThat(pixels(movedAnchor.bottom)).isEqualTo(150f)
        assertThat(pixels(movedAnchor.width)).isEqualTo(260f)
        assertThat(pixels(movedAnchor.height)).isEqualTo(52f)
        // Left/Top should remain null (element only has Bottom+Width+Height)
        assertThat(movedAnchor.left).isNull()
        assertThat(movedAnchor.top).isNull()
    }

    @Test
    fun `move should update Right anchor correctly`() {
        // Arrange: element anchored to right edge
        val state = createState()
        val child = element(
            id = "sidebar",
            properties = mapOf("Anchor" to anchor(right = 10f, width = 200f, height = 50f))
        )
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Act: move right 30
        state.moveSelectedElements(Offset(30f, 0f))

        // Assert
        val moved = state.rootElement.value!!.children[0]
        val movedAnchor = (moved.getProperty("Anchor") as PropertyValue.Anchor).anchor
        // Right decreases when moving right (closer to right edge)
        assertThat(pixels(movedAnchor.right)).isEqualTo(-20f)
        assertThat(pixels(movedAnchor.width)).isEqualTo(200f)
    }

    @Test
    fun `move should update Left and Top together`() {
        // Arrange: standard Left+Top+Width+Height element
        val state = createState()
        val child = element(
            id = "btn",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 200f, width = 120f, height = 40f))
        )
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Act
        state.moveSelectedElements(Offset(10f, 20f))

        // Assert
        val moved = state.rootElement.value!!.children[0]
        val movedAnchor = (moved.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat(pixels(movedAnchor.left)).isEqualTo(110f)
        assertThat(pixels(movedAnchor.top)).isEqualTo(220f)
        assertThat(pixels(movedAnchor.width)).isEqualTo(120f)
        assertThat(pixels(movedAnchor.height)).isEqualTo(40f)
    }

    @Test
    fun `move should update Left and Right together for stretch mode`() {
        // Arrange: element stretched between edges (Left + Right, no explicit Width)
        val state = createState()
        val child = element(
            id = "bar",
            properties = mapOf("Anchor" to anchor(left = 10f, right = 10f, height = 30f))
        )
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Act: move right 5
        state.moveSelectedElements(Offset(5f, 0f))

        // Assert: both edges shift, maintaining the stretch
        val moved = state.rootElement.value!!.children[0]
        val movedAnchor = (moved.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat(pixels(movedAnchor.left)).isEqualTo(15f)
        assertThat(pixels(movedAnchor.right)).isEqualTo(5f)
    }

    @Test
    fun `move should not modify Width or Height`() {
        val state = createState()
        val child = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(bottom = 50f, right = 20f, width = 100f, height = 80f))
        )
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        state.moveSelectedElements(Offset(10f, 10f))

        val moved = state.rootElement.value!!.children[0]
        val movedAnchor = (moved.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat(pixels(movedAnchor.width)).isEqualTo(100f)
        assertThat(pixels(movedAnchor.height)).isEqualTo(80f)
    }

    // =====================================================================
    // FR-2: Resize handles Right/Bottom anchors
    // =====================================================================

    @Test
    fun `resize right handle on Right-anchored element should adjust Right and Width`() {
        val state = createState()
        val child = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(right = 10f, width = 200f, height = 50f))
        )
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Act: drag right handle +30px to the right
        state.resizeSelectedElement(
            state.rootElement.value!!.children[0],
            CanvasState.ResizeHandle.RIGHT_CENTER,
            Offset(30f, 0f)
        )

        val resized = state.rootElement.value!!.children[0]
        val resizedAnchor = (resized.getProperty("Anchor") as PropertyValue.Anchor).anchor
        // Right decreases (handle moved right = closer to right edge)
        assertThat(pixels(resizedAnchor.right)).isEqualTo(-20f)
        assertThat(pixels(resizedAnchor.width)).isEqualTo(230f)
    }

    @Test
    fun `resize bottom handle on Bottom-anchored element should adjust Bottom and Height`() {
        val state = createState()
        val child = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(bottom = 120f, width = 260f, height = 52f))
        )
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Act: drag bottom handle +20px down
        state.resizeSelectedElement(
            state.rootElement.value!!.children[0],
            CanvasState.ResizeHandle.BOTTOM_CENTER,
            Offset(0f, 20f)
        )

        val resized = state.rootElement.value!!.children[0]
        val resizedAnchor = (resized.getProperty("Anchor") as PropertyValue.Anchor).anchor
        // Bottom decreases when handle moves down
        assertThat(pixels(resizedAnchor.bottom)).isEqualTo(100f)
        assertThat(pixels(resizedAnchor.height)).isEqualTo(72f)
    }

    @Test
    fun `resize left handle on Right-anchored element should only adjust Width`() {
        val state = createState()
        val child = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(right = 10f, width = 200f, height = 50f))
        )
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Act: drag left handle -20px to the left (expands width)
        state.resizeSelectedElement(
            state.rootElement.value!!.children[0],
            CanvasState.ResizeHandle.LEFT_CENTER,
            Offset(-20f, 0f)
        )

        val resized = state.rootElement.value!!.children[0]
        val resizedAnchor = (resized.getProperty("Anchor") as PropertyValue.Anchor).anchor
        // Right unchanged, width grows
        assertThat(pixels(resizedAnchor.right)).isEqualTo(10f)
        assertThat(pixels(resizedAnchor.width)).isEqualTo(220f)
    }

    @Test
    fun `resize top handle on Bottom-anchored element should only adjust Height`() {
        val state = createState()
        val child = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(bottom = 120f, width = 260f, height = 52f))
        )
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Act: drag top handle -10px up (expands height)
        state.resizeSelectedElement(
            state.rootElement.value!!.children[0],
            CanvasState.ResizeHandle.TOP_CENTER,
            Offset(0f, -10f)
        )

        val resized = state.rootElement.value!!.children[0]
        val resizedAnchor = (resized.getProperty("Anchor") as PropertyValue.Anchor).anchor
        // Bottom unchanged, height grows
        assertThat(pixels(resizedAnchor.bottom)).isEqualTo(120f)
        assertThat(pixels(resizedAnchor.height)).isEqualTo(62f)
    }

    @Test
    fun `resize should respect minimum size of 20px`() {
        val state = createState()
        val child = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(right = 10f, width = 30f, height = 30f))
        )
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Act: drag right handle -50px left (would make width negative)
        state.resizeSelectedElement(
            state.rootElement.value!!.children[0],
            CanvasState.ResizeHandle.RIGHT_CENTER,
            Offset(-50f, 0f)
        )

        val resized = state.rootElement.value!!.children[0]
        val resizedAnchor = (resized.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat(pixels(resizedAnchor.width)).isEqualTo(20f) // clamped to min
    }

    @Test
    fun `resize on Left+Right stretch should adjust Right edge directly`() {
        val state = createState()
        val child = element(
            id = "el",
            properties = mapOf("Anchor" to anchor(left = 10f, right = 10f, height = 40f))
        )
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)
        state.selectElement(state.rootElement.value!!.children[0])

        // Act: drag right handle +15px right
        state.resizeSelectedElement(
            state.rootElement.value!!.children[0],
            CanvasState.ResizeHandle.RIGHT_CENTER,
            Offset(15f, 0f)
        )

        val resized = state.rootElement.value!!.children[0]
        val resizedAnchor = (resized.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat(pixels(resizedAnchor.left)).isEqualTo(10f) // unchanged
        assertThat(pixels(resizedAnchor.right)).isEqualTo(-5f) // moved right
    }

    // =====================================================================
    // FR-3: isLayoutManaged detection
    // =====================================================================

    @Test
    fun `should detect element in LayoutMode Top parent as layout-managed`() {
        val state = createState()
        val child = element(id = "label1", properties = mapOf("Anchor" to anchor(height = 20f)))
        val parent = element(
            type = "Group", id = "container",
            properties = mapOf("LayoutMode" to PropertyValue.Text("Top")),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        val label = state.rootElement.value!!.children[0].children[0]
        assertThat(state.isLayoutManaged(label)).isTrue()
    }

    @Test
    fun `should detect element in LayoutMode Left parent as layout-managed`() {
        val state = createState()
        val child = element(id = "btn1", properties = mapOf("Anchor" to anchor(width = 100f)))
        val parent = element(
            type = "Group", id = "row",
            properties = mapOf("LayoutMode" to PropertyValue.Text("Left")),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        val btn = state.rootElement.value!!.children[0].children[0]
        assertThat(state.isLayoutManaged(btn)).isTrue()
    }

    @Test
    fun `should not consider element in LayoutMode Middle as layout-managed`() {
        val state = createState()
        val child = element(id = "dialog", properties = mapOf("Anchor" to anchor(width = 400f)))
        val parent = element(
            type = "Group", id = "center",
            properties = mapOf("LayoutMode" to PropertyValue.Text("Middle")),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        val dialog = state.rootElement.value!!.children[0].children[0]
        assertThat(state.isLayoutManaged(dialog)).isFalse()
    }

    @Test
    fun `should not consider element in parent without LayoutMode as layout-managed`() {
        val state = createState()
        val child = element(id = "free", properties = mapOf("Anchor" to anchor(left = 10f, top = 20f)))
        val parent = element(type = "Group", id = "container", children = listOf(child))
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        val free = state.rootElement.value!!.children[0].children[0]
        assertThat(state.isLayoutManaged(free)).isFalse()
    }

    @Test
    fun `root element should not be layout-managed`() {
        val state = createState()
        val root = element(type = "Root", id = "root")
        state.setRootElement(root)

        assertThat(state.isLayoutManaged(state.rootElement.value!!)).isFalse()
    }

    @Test
    fun `direct child of root without LayoutMode should not be layout-managed`() {
        val state = createState()
        val child = element(id = "hud", properties = mapOf("Anchor" to anchor(bottom = 120f)))
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)

        val hud = state.rootElement.value!!.children[0]
        assertThat(state.isLayoutManaged(hud)).isFalse()
    }

    // =====================================================================
    // FR-3: getParentLayoutMode
    // =====================================================================

    @Test
    fun `getParentLayoutMode should return Top for Top-layout parent`() {
        val state = createState()
        val child = element(id = "c1")
        val parent = element(
            id = "p1",
            properties = mapOf("LayoutMode" to PropertyValue.Text("Top")),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        val c = state.rootElement.value!!.children[0].children[0]
        assertThat(state.getParentLayoutMode(c)).isEqualTo("Top")
    }

    @Test
    fun `getParentLayoutMode should return null for Middle-layout parent`() {
        val state = createState()
        val child = element(id = "c1")
        val parent = element(
            id = "p1",
            properties = mapOf("LayoutMode" to PropertyValue.Text("Middle")),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        val c = state.rootElement.value!!.children[0].children[0]
        assertThat(state.getParentLayoutMode(c)).isNull()
    }

    @Test
    fun `getParentLayoutMode should return null for free element`() {
        val state = createState()
        val child = element(id = "free")
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)

        val c = state.rootElement.value!!.children[0]
        assertThat(state.getParentLayoutMode(c)).isNull()
    }

    // =====================================================================
    // FR-4: Prevent move on layout-managed elements
    // =====================================================================

    @Test
    fun `move should not change anchor of layout-managed element`() {
        val state = createState()
        val child = element(id = "label1", properties = mapOf("Anchor" to anchor(height = 20f)))
        val parent = element(
            type = "Group", id = "stack",
            properties = mapOf(
                "LayoutMode" to PropertyValue.Text("Top"),
                "Anchor" to anchor(left = 0f, top = 0f, width = 200f, height = 100f)
            ),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        // Select the layout-managed child
        val label = state.rootElement.value!!.children[0].children[0]
        state.selectElement(label)

        // Act: attempt to move
        state.moveSelectedElements(Offset(50f, 50f))

        // Assert: anchor unchanged
        val after = state.rootElement.value!!.children[0].children[0]
        val afterAnchor = (after.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat(pixels(afterAnchor.height)).isEqualTo(20f)
        assertThat(afterAnchor.left).isNull()
        assertThat(afterAnchor.top).isNull()
    }

    @Test
    fun `move should still work on free elements when selection is mixed`() {
        val state = createState()
        // Free child (direct child of root, no parent LayoutMode)
        val freeChild = element(
            id = "free",
            properties = mapOf("Anchor" to anchor(left = 100f, top = 100f, width = 50f, height = 50f))
        )
        // Layout-managed child
        val managedChild = element(id = "managed", properties = mapOf("Anchor" to anchor(height = 20f)))
        val layoutParent = element(
            type = "Group", id = "stack",
            properties = mapOf(
                "LayoutMode" to PropertyValue.Text("Top"),
                "Anchor" to anchor(left = 0f, top = 0f, width = 200f, height = 100f)
            ),
            children = listOf(managedChild)
        )
        val root = element(type = "Root", id = "root", children = listOf(freeChild, layoutParent))
        state.setRootElement(root)

        // Select both elements
        val free = state.rootElement.value!!.children[0]
        val managed = state.rootElement.value!!.children[1].children[0]
        state.selectElement(free)
        state.addToSelection(managed)

        // Act
        state.moveSelectedElements(Offset(10f, 10f))

        // Assert: free element moved, managed element didn't
        val movedFree = state.rootElement.value!!.children[0]
        val movedFreeAnchor = (movedFree.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat(pixels(movedFreeAnchor.left)).isEqualTo(110f)
        assertThat(pixels(movedFreeAnchor.top)).isEqualTo(110f)

        val stillManaged = state.rootElement.value!!.children[1].children[0]
        val managedAnchor = (stillManaged.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat(pixels(managedAnchor.height)).isEqualTo(20f)
        assertThat(managedAnchor.left).isNull()
    }

    // =====================================================================
    // FR-5: Allowed resize handles
    // =====================================================================

    @Test
    fun `free element should have all 8 resize handles`() {
        val state = createState()
        val child = element(id = "free", properties = mapOf("Anchor" to anchor(left = 10f, top = 10f)))
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)

        val el = state.rootElement.value!!.children[0]
        assertThat(state.allowedResizeHandles(el)).hasSize(8)
        assertThat(state.allowedResizeHandles(el)).isEqualTo(CanvasState.ResizeHandle.entries.toSet())
    }

    @Test
    fun `element in Top-layout parent should only have Bottom, BottomRight, Right handles`() {
        val state = createState()
        val child = element(id = "label", properties = mapOf("Anchor" to anchor(height = 20f)))
        val parent = element(
            id = "stack",
            properties = mapOf("LayoutMode" to PropertyValue.Text("Top")),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        val el = state.rootElement.value!!.children[0].children[0]
        val allowed = state.allowedResizeHandles(el)
        assertThat(allowed).containsExactlyInAnyOrder(
            CanvasState.ResizeHandle.BOTTOM_CENTER,
            CanvasState.ResizeHandle.BOTTOM_RIGHT,
            CanvasState.ResizeHandle.RIGHT_CENTER
        )
    }

    @Test
    fun `element in Left-layout parent should only have Right, BottomRight, Bottom handles`() {
        val state = createState()
        val child = element(id = "btn", properties = mapOf("Anchor" to anchor(width = 100f)))
        val parent = element(
            id = "row",
            properties = mapOf("LayoutMode" to PropertyValue.Text("Left")),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(parent))
        state.setRootElement(root)

        val el = state.rootElement.value!!.children[0].children[0]
        val allowed = state.allowedResizeHandles(el)
        assertThat(allowed).containsExactlyInAnyOrder(
            CanvasState.ResizeHandle.RIGHT_CENTER,
            CanvasState.ResizeHandle.BOTTOM_RIGHT,
            CanvasState.ResizeHandle.BOTTOM_CENTER
        )
    }

    // =====================================================================
    // ResizeHandle helpers
    // =====================================================================

    // =====================================================================
    // findDraggableAncestor: bubble-up drag for layout-managed children
    // =====================================================================

    @Test
    fun `findDraggableAncestor should return parent container for layout-managed child`() {
        val state = createState()
        val child = element(id = "label1", properties = mapOf("Anchor" to anchor(height = 20f)))
        val container = element(
            id = "hud",
            properties = mapOf(
                "LayoutMode" to PropertyValue.Text("Top"),
                "Anchor" to anchor(bottom = 120f, width = 260f, height = 52f)
            ),
            children = listOf(child)
        )
        val root = element(type = "Root", id = "root", children = listOf(container))
        state.setRootElement(root)

        val label = state.rootElement.value!!.children[0].children[0]
        val ancestor = state.findDraggableAncestor(label)
        assertThat(ancestor).isNotNull
        assertThat(ancestor!!.id?.value).isEqualTo("hud")
    }

    @Test
    fun `findDraggableAncestor should return null for element whose parent is root`() {
        val state = createState()
        val child = element(id = "hud", properties = mapOf("Anchor" to anchor(bottom = 120f)))
        val root = element(type = "Root", id = "root",
            properties = mapOf("LayoutMode" to PropertyValue.Text("Top")),
            children = listOf(child))
        state.setRootElement(root)

        // child is layout-managed but its parent is root — no draggable ancestor
        val hud = state.rootElement.value!!.children[0]
        assertThat(state.findDraggableAncestor(hud)).isNull()
    }

    @Test
    fun `findDraggableAncestor should walk up through nested layout-managed elements`() {
        val state = createState()
        val innerChild = element(id = "text", properties = mapOf("Anchor" to anchor(height = 14f)))
        val innerContainer = element(
            id = "inner",
            properties = mapOf(
                "LayoutMode" to PropertyValue.Text("Top"),
                "Anchor" to anchor(height = 30f)
            ),
            children = listOf(innerChild)
        )
        val outerContainer = element(
            id = "outer",
            properties = mapOf(
                "LayoutMode" to PropertyValue.Text("Top"),
                "Anchor" to anchor(bottom = 100f, width = 200f, height = 80f)
            ),
            children = listOf(innerContainer)
        )
        val root = element(type = "Root", id = "root", children = listOf(outerContainer))
        state.setRootElement(root)

        // innerChild → inner (layout-managed by outer) → outer (free, not root)
        val text = state.rootElement.value!!.children[0].children[0].children[0]
        val ancestor = state.findDraggableAncestor(text)
        assertThat(ancestor).isNotNull
        assertThat(ancestor!!.id?.value).isEqualTo("outer")
    }

    @Test
    fun `findDraggableAncestor should return null for free element`() {
        val state = createState()
        val child = element(id = "free", properties = mapOf("Anchor" to anchor(left = 10f, top = 20f)))
        val root = element(type = "Root", id = "root", children = listOf(child))
        state.setRootElement(root)

        // Free element is not layout-managed, so no ancestor needed
        val el = state.rootElement.value!!.children[0]
        assertThat(state.findDraggableAncestor(el)).isNull()
    }

    @Test
    fun `findDraggableAncestor should skip locked ancestors`() {
        val state = createState()
        val child = element(id = "label1", properties = mapOf("Anchor" to anchor(height = 20f)))
        val lockedContainer = element(
            id = "locked",
            properties = mapOf(
                "LayoutMode" to PropertyValue.Text("Top"),
                "Anchor" to anchor(left = 0f, top = 0f, width = 200f, height = 100f)
            ),
            children = listOf(child)
        )
        // Lock the container
        val lockedMeta = lockedContainer.metadata.copy(locked = true)
        val lockedWithMeta = lockedContainer.copy(metadata = lockedMeta)
        val root = element(type = "Root", id = "root", children = listOf(lockedWithMeta))
        state.setRootElement(root)

        val label = state.rootElement.value!!.children[0].children[0]
        // Parent is locked, and its parent is root — no draggable ancestor
        assertThat(state.findDraggableAncestor(label)).isNull()
    }

    @Test
    fun `findDraggableAncestor should match DemoHud scenario`() {
        // This test mirrors the actual DemoHud.ui structure:
        // Root Group (no LayoutMode) → #HyveHud (LayoutMode: Top) → #ManaTitle (layout-managed)
        val state = createState()
        val manaTitle = element(id = "ManaTitle", properties = mapOf("Anchor" to anchor(height = 14f)))
        val spacer = element(properties = mapOf("Anchor" to anchor(height = 4f)))
        val manaBarFill = element(id = "ManaBarFill", properties = mapOf("Anchor" to anchor(left = 0f, width = 192f, height = 14f)))
        val manaBarTrack = element(id = "ManaBarTrack", properties = mapOf("Anchor" to anchor(height = 18f)), children = listOf(manaBarFill))
        val spacer2 = element(properties = mapOf("Anchor" to anchor(height = 2f)))
        val manaLabel = element(id = "ManaLabel", properties = mapOf("Anchor" to anchor(height = 14f)))

        val hyveHud = element(
            id = "HyveHud",
            properties = mapOf(
                "LayoutMode" to PropertyValue.Text("Top"),
                "Anchor" to anchor(bottom = 120f, width = 260f, height = 52f)
            ),
            children = listOf(manaTitle, spacer, manaBarTrack, spacer2, manaLabel)
        )
        val root = element(type = "Group", id = null, children = listOf(hyveHud))
        state.setRootElement(root)

        // Click on ManaTitle (layout-managed child) → should bubble up to HyveHud
        val clickedTitle = state.rootElement.value!!.children[0].children[0]
        val ancestor = state.findDraggableAncestor(clickedTitle)
        assertThat(ancestor).isNotNull
        assertThat(ancestor!!.id?.value).isEqualTo("HyveHud")

        // Click on ManaBarFill (nested inside ManaBarTrack, which is layout-managed)
        val clickedFill = state.rootElement.value!!.children[0].children[2].children[0]
        val ancestor2 = state.findDraggableAncestor(clickedFill)
        assertThat(ancestor2).isNotNull
        assertThat(ancestor2!!.id?.value).isEqualTo("HyveHud")
    }

    // =====================================================================
    // ResizeHandle helpers
    // =====================================================================

    @Test
    fun `affectsRight should be true for right-side handles`() {
        assertThat(CanvasState.ResizeHandle.TOP_RIGHT.affectsRight()).isTrue()
        assertThat(CanvasState.ResizeHandle.RIGHT_CENTER.affectsRight()).isTrue()
        assertThat(CanvasState.ResizeHandle.BOTTOM_RIGHT.affectsRight()).isTrue()
        assertThat(CanvasState.ResizeHandle.TOP_LEFT.affectsRight()).isFalse()
        assertThat(CanvasState.ResizeHandle.LEFT_CENTER.affectsRight()).isFalse()
    }

    @Test
    fun `affectsBottom should be true for bottom-side handles`() {
        assertThat(CanvasState.ResizeHandle.BOTTOM_LEFT.affectsBottom()).isTrue()
        assertThat(CanvasState.ResizeHandle.BOTTOM_CENTER.affectsBottom()).isTrue()
        assertThat(CanvasState.ResizeHandle.BOTTOM_RIGHT.affectsBottom()).isTrue()
        assertThat(CanvasState.ResizeHandle.TOP_LEFT.affectsBottom()).isFalse()
        assertThat(CanvasState.ResizeHandle.TOP_CENTER.affectsBottom()).isFalse()
    }
}
