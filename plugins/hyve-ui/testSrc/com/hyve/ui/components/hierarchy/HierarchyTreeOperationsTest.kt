// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.components.hierarchy

import com.hyve.ui.canvas.CanvasState
import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.state.command.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for hierarchy tree operations that were converted from raw
 * setRootElement calls to use the command system or updateElementMetadata.
 *
 * Covers:
 * - toggleElementVisibility via updateElementMetadata
 * - toggleElementLock via updateElementMetadata
 * - wrapInGroup via CompositeCommand (undoable)
 */
class HierarchyTreeOperationsTest {

    // -- Helpers (same pattern as ReorderElementCommandTest) --

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

    private fun anchor(
        left: Float? = null,
        top: Float? = null,
        width: Float? = null,
        height: Float? = null
    ): PropertyValue.Anchor = PropertyValue.Anchor(
        AnchorValue.absolute(left, top, null, null, width, height)
    )

    private fun tree(vararg children: UIElement): UIElement {
        return element(type = "Root", id = "root", children = children.toList())
    }

    private fun createState(): CanvasState = CanvasState()

    // =====================================================================
    // Toggle Visibility via updateElementMetadata
    // =====================================================================

    @Test
    fun `updateElementMetadata should toggle visibility to hidden`() {
        val state = createState()
        val child = element(id = "btn", properties = mapOf("Anchor" to anchor(10f, 10f, 100f, 50f)))
        val root = tree(child)
        state.setRootElement(root)

        val childRef = state.rootElement.value!!.children[0]
        assertThat(childRef.metadata.visible).isTrue()

        state.updateElementMetadata(childRef) { it.copy(visible = false) }

        val updated = state.rootElement.value!!.children[0]
        assertThat(updated.metadata.visible).isFalse()
    }

    @Test
    fun `updateElementMetadata should toggle visibility back to visible`() {
        val state = createState()
        val child = element(id = "btn", properties = mapOf("Anchor" to anchor(10f, 10f, 100f, 50f)))
        val root = tree(child)
        state.setRootElement(root)

        // Hide
        val childRef = state.rootElement.value!!.children[0]
        state.updateElementMetadata(childRef) { it.copy(visible = false) }

        // Show again
        val hiddenRef = state.rootElement.value!!.children[0]
        state.updateElementMetadata(hiddenRef) { it.copy(visible = true) }

        val restored = state.rootElement.value!!.children[0]
        assertThat(restored.metadata.visible).isTrue()
    }

    @Test
    fun `updateElementMetadata should preserve other element properties`() {
        val state = createState()
        val child = element(id = "btn", properties = mapOf(
            "Anchor" to anchor(10f, 10f, 100f, 50f),
            "Text" to PropertyValue.Text("Hello")
        ))
        val root = tree(child)
        state.setRootElement(root)

        val childRef = state.rootElement.value!!.children[0]
        state.updateElementMetadata(childRef) { it.copy(visible = false) }

        val updated = state.rootElement.value!!.children[0]
        assertThat(updated.metadata.visible).isFalse()
        assertThat((updated.getProperty("Text") as? PropertyValue.Text)?.value).isEqualTo("Hello")
        assertThat(updated.id?.value).isEqualTo("btn")
    }

    @Test
    fun `updateElementMetadata should update selection to new element reference`() {
        val state = createState()
        val child = element(id = "btn", properties = mapOf("Anchor" to anchor(10f, 10f, 100f, 50f)))
        val root = tree(child)
        state.setRootElement(root)

        val childRef = state.rootElement.value!!.children[0]
        state.selectElement(childRef)
        assertThat(state.selectedElements.value).hasSize(1)

        state.updateElementMetadata(childRef) { it.copy(visible = false) }

        // Selection should still reference the updated element
        val selected = state.selectedElements.value
        assertThat(selected).hasSize(1)
        assertThat(selected.first().metadata.visible).isFalse()
    }

    // =====================================================================
    // Toggle Lock via updateElementMetadata
    // =====================================================================

    @Test
    fun `updateElementMetadata should toggle lock on`() {
        val state = createState()
        val child = element(id = "btn", properties = mapOf("Anchor" to anchor(10f, 10f, 100f, 50f)))
        val root = tree(child)
        state.setRootElement(root)

        val childRef = state.rootElement.value!!.children[0]
        assertThat(childRef.metadata.locked).isFalse()

        state.updateElementMetadata(childRef) { it.copy(locked = true) }

        val updated = state.rootElement.value!!.children[0]
        assertThat(updated.metadata.locked).isTrue()
    }

    @Test
    fun `updateElementMetadata should toggle lock off`() {
        val state = createState()
        val child = element(id = "btn", properties = mapOf("Anchor" to anchor(10f, 10f, 100f, 50f)))
        val root = tree(child)
        state.setRootElement(root)

        // Lock
        val childRef = state.rootElement.value!!.children[0]
        state.updateElementMetadata(childRef) { it.copy(locked = true) }

        // Unlock
        val lockedRef = state.rootElement.value!!.children[0]
        state.updateElementMetadata(lockedRef) { it.copy(locked = false) }

        val restored = state.rootElement.value!!.children[0]
        assertThat(restored.metadata.locked).isFalse()
    }

    // =====================================================================
    // Wrap in Group — command-level tests
    // =====================================================================

    @Test
    fun `wrap in group should replace element with group containing it`() {
        val btn = element(id = "btn", properties = mapOf("Anchor" to anchor(10f, 20f, 100f, 50f)))
        val root = tree(btn)

        val group = UIElement(
            type = ElementType("Group"),
            id = ElementId("wrap_group"),
            properties = btn.properties,
            children = listOf(btn.copy(
                properties = PropertyMap.of("Anchor" to PropertyValue.Anchor(AnchorValue.fill()))
            ))
        )

        val deleteCmd = DeleteElementCommand.fromRoot(btn, root, 0)
        val addCmd = AddElementCommand.toRoot(group, 0)
        val command = CompositeCommand(listOf(deleteCmd, addCmd), "Wrap btn in Group")

        val result = command.execute(root)

        assertThat(result).isNotNull
        assertThat(result!!.children).hasSize(1)
        val wrapper = result.children[0]
        assertThat(wrapper.type.value).isEqualTo("Group")
        assertThat(wrapper.id?.value).isEqualTo("wrap_group")
        assertThat(wrapper.children).hasSize(1)
        assertThat(wrapper.children[0].id?.value).isEqualTo("btn")
    }

    @Test
    fun `wrap in group should preserve original anchor on the group`() {
        val btnAnchor = anchor(10f, 20f, 100f, 50f)
        val btn = element(id = "btn", properties = mapOf("Anchor" to btnAnchor))
        val root = tree(btn)

        val group = UIElement(
            type = ElementType("Group"),
            id = ElementId("wrap_group"),
            properties = btn.properties,
            children = listOf(btn.copy(
                properties = PropertyMap.of("Anchor" to PropertyValue.Anchor(AnchorValue.fill()))
            ))
        )

        val deleteCmd = DeleteElementCommand.fromRoot(btn, root, 0)
        val addCmd = AddElementCommand.toRoot(group, 0)
        val command = CompositeCommand(listOf(deleteCmd, addCmd), "Wrap btn in Group")

        val result = command.execute(root)!!
        val wrapper = result.children[0]

        // Group should have the original element's anchor
        val groupAnchor = wrapper.getProperty("Anchor") as PropertyValue.Anchor
        val origAnchor = btnAnchor.anchor
        assertThat((groupAnchor.anchor.left as? AnchorDimension.Absolute)?.pixels)
            .isEqualTo((origAnchor.left as? AnchorDimension.Absolute)?.pixels)
        assertThat((groupAnchor.anchor.top as? AnchorDimension.Absolute)?.pixels)
            .isEqualTo((origAnchor.top as? AnchorDimension.Absolute)?.pixels)
    }

    @Test
    fun `wrap in group should set fill anchor on wrapped child`() {
        val btn = element(id = "btn", properties = mapOf("Anchor" to anchor(10f, 20f, 100f, 50f)))
        val root = tree(btn)

        val group = UIElement(
            type = ElementType("Group"),
            id = ElementId("wrap_group"),
            properties = btn.properties,
            children = listOf(btn.copy(
                properties = PropertyMap.of("Anchor" to PropertyValue.Anchor(AnchorValue.fill()))
            ))
        )

        val deleteCmd = DeleteElementCommand.fromRoot(btn, root, 0)
        val addCmd = AddElementCommand.toRoot(group, 0)
        val command = CompositeCommand(listOf(deleteCmd, addCmd), "Wrap btn in Group")

        val result = command.execute(root)!!
        val wrappedChild = result.children[0].children[0]
        val childAnchor = (wrappedChild.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat(childAnchor).isEqualTo(AnchorValue.fill())
    }

    @Test
    fun `wrap in group undo should restore original element`() {
        val btn = element(id = "btn", properties = mapOf("Anchor" to anchor(10f, 20f, 100f, 50f)))
        val root = tree(btn)

        val group = UIElement(
            type = ElementType("Group"),
            id = ElementId("wrap_group"),
            properties = btn.properties,
            children = listOf(btn.copy(
                properties = PropertyMap.of("Anchor" to PropertyValue.Anchor(AnchorValue.fill()))
            ))
        )

        val deleteCmd = DeleteElementCommand.fromRoot(btn, root, 0)
        val addCmd = AddElementCommand.toRoot(group, 0)
        val command = CompositeCommand(listOf(deleteCmd, addCmd), "Wrap btn in Group")

        val afterExecute = command.execute(root)!!
        val afterUndo = command.undo(afterExecute)

        assertThat(afterUndo).isNotNull
        assertThat(afterUndo!!.children).hasSize(1)
        val restored = afterUndo.children[0]
        assertThat(restored.id?.value).isEqualTo("btn")
        assertThat(restored.type.value).isEqualTo("Button")
        // Should NOT be wrapped in a group
        assertThat(restored.children).isEmpty()
    }

    @Test
    fun `wrap in group should preserve sibling order`() {
        val a = element(id = "a", properties = mapOf("Anchor" to anchor(0f, 0f, 50f, 50f)))
        val b = element(id = "b", properties = mapOf("Anchor" to anchor(60f, 0f, 50f, 50f)))
        val c = element(id = "c", properties = mapOf("Anchor" to anchor(120f, 0f, 50f, 50f)))
        val root = tree(a, b, c)

        // Wrap "b" (index 1)
        val group = UIElement(
            type = ElementType("Group"),
            id = ElementId("wrap_group"),
            properties = b.properties,
            children = listOf(b.copy(
                properties = PropertyMap.of("Anchor" to PropertyValue.Anchor(AnchorValue.fill()))
            ))
        )

        val deleteCmd = DeleteElementCommand.fromRoot(b, root, 1)
        val addCmd = AddElementCommand.toRoot(group, 1)
        val command = CompositeCommand(listOf(deleteCmd, addCmd), "Wrap b in Group")

        val result = command.execute(root)!!

        assertThat(result.children).hasSize(3)
        assertThat(result.children[0].id?.value).isEqualTo("a")
        assertThat(result.children[1].id?.value).isEqualTo("wrap_group")
        assertThat(result.children[1].type.value).isEqualTo("Group")
        assertThat(result.children[2].id?.value).isEqualTo("c")
    }

    // =====================================================================
    // Wrap in Group — CanvasState integration (undo/redo)
    // =====================================================================

    @Test
    fun `wrap in group via executeCommand should be undoable`() {
        val state = createState()
        val btn = element(id = "btn", properties = mapOf("Anchor" to anchor(10f, 20f, 100f, 50f)))
        val root = tree(btn)
        state.setRootElement(root)

        val btnRef = state.rootElement.value!!.children[0]

        val group = UIElement(
            type = ElementType("Group"),
            id = ElementId("wrap_group"),
            properties = btnRef.properties,
            children = listOf(btnRef.copy(
                properties = PropertyMap.of("Anchor" to PropertyValue.Anchor(AnchorValue.fill()))
            ))
        )

        val deleteCmd = DeleteElementCommand.fromRoot(btnRef, state.rootElement.value!!, 0)
        val addCmd = AddElementCommand.toRoot(group, 0)
        val command = CompositeCommand(listOf(deleteCmd, addCmd), "Wrap btn in Group")

        // Execute
        val executed = state.executeCommand(command, allowMerge = false)
        assertThat(executed).isTrue()

        val afterWrap = state.rootElement.value!!
        assertThat(afterWrap.children).hasSize(1)
        assertThat(afterWrap.children[0].type.value).isEqualTo("Group")

        // Undo
        val undone = state.undo()
        assertThat(undone).isTrue()

        val afterUndo = state.rootElement.value!!
        assertThat(afterUndo.children).hasSize(1)
        assertThat(afterUndo.children[0].id?.value).isEqualTo("btn")
        assertThat(afterUndo.children[0].type.value).isEqualTo("Button")
    }

    @Test
    fun `wrap in group via executeCommand should be redoable`() {
        val state = createState()
        val btn = element(id = "btn", properties = mapOf("Anchor" to anchor(10f, 20f, 100f, 50f)))
        val root = tree(btn)
        state.setRootElement(root)

        val btnRef = state.rootElement.value!!.children[0]

        val group = UIElement(
            type = ElementType("Group"),
            id = ElementId("wrap_group"),
            properties = btnRef.properties,
            children = listOf(btnRef.copy(
                properties = PropertyMap.of("Anchor" to PropertyValue.Anchor(AnchorValue.fill()))
            ))
        )

        val deleteCmd = DeleteElementCommand.fromRoot(btnRef, state.rootElement.value!!, 0)
        val addCmd = AddElementCommand.toRoot(group, 0)
        val command = CompositeCommand(listOf(deleteCmd, addCmd), "Wrap btn in Group")

        state.executeCommand(command, allowMerge = false)
        state.undo()

        // Redo
        val redone = state.redo()
        assertThat(redone).isTrue()

        val afterRedo = state.rootElement.value!!
        assertThat(afterRedo.children).hasSize(1)
        assertThat(afterRedo.children[0].type.value).isEqualTo("Group")
        assertThat(afterRedo.children[0].children).hasSize(1)
        assertThat(afterRedo.children[0].children[0].id?.value).isEqualTo("btn")
    }

    // =====================================================================
    // Wrap in Group — nested element
    // =====================================================================

    @Test
    fun `wrap in group should work for nested element`() {
        val child = element(id = "child", properties = mapOf("Anchor" to anchor(5f, 5f, 40f, 40f)))
        val parent = element(id = "parent", type = "Group",
            properties = mapOf("Anchor" to anchor(100f, 100f, 200f, 200f)),
            children = listOf(child)
        )
        val root = tree(parent)

        val group = UIElement(
            type = ElementType("Group"),
            id = ElementId("wrap_group"),
            properties = child.properties,
            children = listOf(child.copy(
                properties = PropertyMap.of("Anchor" to PropertyValue.Anchor(AnchorValue.fill()))
            ))
        )

        val deleteCmd = DeleteElementCommand.forElement(child, parent, 0)
        val addCmd = AddElementCommand.toParent(parent, group, 0)
        val command = CompositeCommand(listOf(deleteCmd, addCmd), "Wrap child in Group")

        val result = command.execute(root)

        assertThat(result).isNotNull
        val parentResult = result!!.children[0]
        assertThat(parentResult.children).hasSize(1)
        val wrapper = parentResult.children[0]
        assertThat(wrapper.type.value).isEqualTo("Group")
        assertThat(wrapper.children[0].id?.value).isEqualTo("child")
    }

    @Test
    fun `wrap in group undo should restore nested element`() {
        val child = element(id = "child", properties = mapOf("Anchor" to anchor(5f, 5f, 40f, 40f)))
        val parent = element(id = "parent", type = "Group",
            properties = mapOf("Anchor" to anchor(100f, 100f, 200f, 200f)),
            children = listOf(child)
        )
        val root = tree(parent)

        val group = UIElement(
            type = ElementType("Group"),
            id = ElementId("wrap_group"),
            properties = child.properties,
            children = listOf(child.copy(
                properties = PropertyMap.of("Anchor" to PropertyValue.Anchor(AnchorValue.fill()))
            ))
        )

        val deleteCmd = DeleteElementCommand.forElement(child, parent, 0)
        val addCmd = AddElementCommand.toParent(parent, group, 0)
        val command = CompositeCommand(listOf(deleteCmd, addCmd), "Wrap child in Group")

        val afterExecute = command.execute(root)!!
        val afterUndo = command.undo(afterExecute)

        assertThat(afterUndo).isNotNull
        val parentResult = afterUndo!!.children[0]
        assertThat(parentResult.children).hasSize(1)
        assertThat(parentResult.children[0].id?.value).isEqualTo("child")
        assertThat(parentResult.children[0].type.value).isEqualTo("Button")
    }

    // =====================================================================
    // Description
    // =====================================================================

    @Test
    fun `wrap in group command description should contain Wrap`() {
        val btn = element(id = "btn")
        val root = tree(btn)
        val group = element(type = "Group", id = "wrap")

        val deleteCmd = DeleteElementCommand.fromRoot(btn, root, 0)
        val addCmd = AddElementCommand.toRoot(group, 0)
        val command = CompositeCommand(listOf(deleteCmd, addCmd), "Wrap btn in Group")

        assertThat(command.description).contains("Wrap")
        assertThat(command.description).contains("Group")
    }
}
