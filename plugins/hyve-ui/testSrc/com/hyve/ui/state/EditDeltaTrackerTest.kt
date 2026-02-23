// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.state

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for EditDeltaTracker - validates delta recording and application to raw documents.
 * Covers the dual-document architecture where edits are tracked as deltas and applied
 * to the raw document at export time.
 */
class EditDeltaTrackerTest {

    // --- Test Helpers ---

    private fun button(
        id: String,
        text: String = "Button",
        anchor: AnchorValue = AnchorValue.absolute(left = 0f, top = 0f, width = 100f, height = 30f)
    ): UIElement = UIElement(
        type = ElementType("Button"),
        id = ElementId(id),
        properties = PropertyMap.of(
            "Text" to PropertyValue.Text(text),
            "Anchor" to PropertyValue.Anchor(anchor)
        )
    )

    private fun label(
        id: String,
        text: String = "Label",
        anchor: AnchorValue = AnchorValue.absolute(left = 0f, top = 0f, width = 100f, height = 30f)
    ): UIElement = UIElement(
        type = ElementType("Label"),
        id = ElementId(id),
        properties = PropertyMap.of(
            "Text" to PropertyValue.Text(text),
            "Anchor" to PropertyValue.Anchor(anchor)
        )
    )

    private fun group(
        id: String,
        vararg children: UIElement
    ): UIElement = UIElement(
        type = ElementType("Group"),
        id = ElementId(id),
        properties = PropertyMap.of(
            "Anchor" to PropertyValue.Anchor(
                AnchorValue.absolute(left = 0f, top = 0f, width = 800f, height = 600f)
            )
        ),
        children = children.toList()
    )

    private fun document(vararg elements: UIElement): UIDocument {
        val root = UIElement(
            type = ElementType("Root"),
            id = ElementId("Root"),
            properties = PropertyMap.empty(),
            children = elements.toList()
        )
        return UIDocument(
            imports = emptyMap(),
            styles = emptyMap(),
            root = root
        )
    }

    // =====================================================================
    // Test 1: SetProperty + applyTo overwrites target property
    // =====================================================================

    @Test
    fun `SetProperty delta should overwrite target property in raw document`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        val result = tracker.applyTo(doc)

        val resultButton = result.findElementById(ElementId("btn1"))
        assertThat(resultButton).isNotNull
        assertThat(resultButton!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Updated"))
    }

    @Test
    fun `SetProperty should add new property if it doesn't exist`() {
        val tracker = EditDeltaTracker()
        val element = UIElement(
            type = ElementType("Label"),
            id = ElementId("lbl1"),
            properties = PropertyMap.of("Text" to PropertyValue.Text("Hello"))
        )
        val doc = document(element)

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("lbl1"),
                propertyName = "FontSize",
                value = PropertyValue.Number(24.0)
            )
        )

        val result = tracker.applyTo(doc)

        val resultLabel = result.findElementById(ElementId("lbl1"))
        assertThat(resultLabel!!.getProperty("FontSize")).isEqualTo(PropertyValue.Number(24.0))
    }

    // =====================================================================
    // Test 2: AddElement + applyTo inserts element at correct parent/index
    // =====================================================================

    @Test
    fun `AddElement delta should insert element at correct index in parent`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("container",
                button("btn1"),
                button("btn2")
            )
        )

        val newButton = button("btn3", text = "New Button")
        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = ElementId("container"),
                index = 1, // Insert between btn1 and btn2
                element = newButton
            )
        )

        val result = tracker.applyTo(doc)

        val container = result.findElementById(ElementId("container"))
        assertThat(container!!.children).hasSize(3)
        assertThat(container.children[0].id).isEqualTo(ElementId("btn1"))
        assertThat(container.children[1].id).isEqualTo(ElementId("btn3"))
        assertThat(container.children[2].id).isEqualTo(ElementId("btn2"))
    }

    @Test
    fun `AddElement should append when index exceeds children count`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("container", button("btn1"))
        )

        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = ElementId("container"),
                index = 999, // Out of bounds
                element = button("btn2")
            )
        )

        val result = tracker.applyTo(doc)

        val container = result.findElementById(ElementId("container"))
        assertThat(container!!.children).hasSize(2)
        assertThat(container.children[1].id).isEqualTo(ElementId("btn2"))
    }

    @Test
    fun `AddElement should insert at root level when parentId is null`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1"))

        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = null,
                index = 0,
                element = button("btn2")
            )
        )

        val result = tracker.applyTo(doc)

        assertThat(result.root.children).hasSize(2)
        assertThat(result.root.children[0].id).isEqualTo(ElementId("btn2"))
        assertThat(result.root.children[1].id).isEqualTo(ElementId("btn1"))
    }

    // =====================================================================
    // Test 3: DeleteElement + applyTo removes element from raw document
    // =====================================================================

    @Test
    fun `DeleteElement delta should remove element from parent`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("container",
                button("btn1"),
                button("btn2"),
                button("btn3")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.DeleteElement(
                elementId = ElementId("btn2")
            )
        )

        val result = tracker.applyTo(doc)

        val container = result.findElementById(ElementId("container"))
        assertThat(container!!.children).hasSize(2)
        assertThat(container.children.map { it.id }).containsExactly(
            ElementId("btn1"),
            ElementId("btn3")
        )
    }

    @Test
    fun `DeleteElement should handle element not found gracefully`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1"))

        tracker.record(
            EditDeltaTracker.EditDelta.DeleteElement(
                elementId = ElementId("nonexistent")
            )
        )

        val result = tracker.applyTo(doc)

        // Document should remain unchanged
        assertThat(result.root.children).hasSize(1)
        assertThat(result.root.children[0].id).isEqualTo(ElementId("btn1"))
    }

    // =====================================================================
    // Test 4: removeDelta reverses a previously recorded property delta
    // =====================================================================

    @Test
    fun `removeDelta should remove SetProperty delta`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        tracker.removeDelta(ElementId("btn1"), "Text")

        val result = tracker.applyTo(doc)

        // Property should remain original since delta was removed
        val resultButton = result.findElementById(ElementId("btn1"))
        assertThat(resultButton!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Original"))
    }

    @Test
    fun `removeDelta should remove MoveElement delta (Anchor property)`() {
        val tracker = EditDeltaTracker()
        val originalAnchor = AnchorValue.absolute(left = 10f, top = 20f, width = 100f, height = 30f)
        val doc = document(button("btn1", anchor = originalAnchor))

        val newAnchor = AnchorValue.absolute(left = 50f, top = 60f, width = 100f, height = 30f)
        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(newAnchor)
            )
        )

        tracker.removeDelta(ElementId("btn1"), "Anchor")

        val result = tracker.applyTo(doc)

        val resultButton = result.findElementById(ElementId("btn1"))
        assertThat(resultButton!!.getProperty("Anchor")).isEqualTo(PropertyValue.Anchor(originalAnchor))
    }

    @Test
    fun `removeDelta should remove RemoveProperty delta`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        tracker.record(
            EditDeltaTracker.EditDelta.RemoveProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text"
            )
        )

        tracker.removeDelta(ElementId("btn1"), "Text")

        val result = tracker.applyTo(doc)

        // Property should still exist since RemoveProperty delta was removed
        val resultButton = result.findElementById(ElementId("btn1"))
        assertThat(resultButton!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Original"))
    }

    // =====================================================================
    // Test 5: Last-write-wins for same (id, property)
    // =====================================================================

    @Test
    fun `multiple SetProperty deltas for same property should collapse to latest value`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("First Update")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Second Update")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Final Update")
            )
        )

        val result = tracker.applyTo(doc)

        val resultButton = result.findElementById(ElementId("btn1"))
        assertThat(resultButton!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Final Update"))
    }

    @Test
    fun `SetProperty followed by RemoveProperty should result in removal`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.RemoveProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text"
            )
        )

        val result = tracker.applyTo(doc)

        val resultButton = result.findElementById(ElementId("btn1"))
        assertThat(resultButton!!.getProperty("Text")).isNull()
    }

    // =====================================================================
    // Test 6: MoveElement + applyTo overwrites Anchor in raw document
    // =====================================================================

    @Test
    fun `MoveElement delta should update Anchor property`() {
        val tracker = EditDeltaTracker()
        val originalAnchor = AnchorValue.absolute(left = 10f, top = 20f, width = 100f, height = 30f)
        val doc = document(button("btn1", anchor = originalAnchor))

        val newAnchor = AnchorValue.absolute(left = 100f, top = 150f, width = 100f, height = 30f)
        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(newAnchor)
            )
        )

        val result = tracker.applyTo(doc)

        val resultButton = result.findElementById(ElementId("btn1"))
        assertThat(resultButton!!.getProperty("Anchor")).isEqualTo(PropertyValue.Anchor(newAnchor))
    }

    @Test
    fun `multiple MoveElement deltas should collapse to latest position`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1"))

        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(AnchorValue.absolute(left = 10f, top = 10f))
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(AnchorValue.absolute(left = 20f, top = 20f))
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(AnchorValue.absolute(left = 30f, top = 30f))
            )
        )

        val result = tracker.applyTo(doc)

        val resultButton = result.findElementById(ElementId("btn1"))
        val anchor = (resultButton!!.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat((anchor.left as com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute).pixels).isEqualTo(30f)
        assertThat((anchor.top as com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute).pixels).isEqualTo(30f)
    }

    // =====================================================================
    // Test 7: clear() resets all deltas
    // =====================================================================

    @Test
    fun `clear should remove all deltas`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = null,
                index = 0,
                element = button("btn2")
            )
        )

        tracker.clear()

        val result = tracker.applyTo(doc)

        // Document should be unchanged after clear
        assertThat(result.root.children).hasSize(1)
        val resultButton = result.findElementById(ElementId("btn1"))
        assertThat(resultButton!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Original"))
    }

    // =====================================================================
    // Test 8: hasDelta returns true/false correctly
    // =====================================================================

    @Test
    fun `hasDelta should return true for recorded SetProperty delta`() {
        val tracker = EditDeltaTracker()

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        assertThat(tracker.hasDelta(ElementId("btn1"), "Text")).isTrue()
        assertThat(tracker.hasDelta(ElementId("btn1"), "FontSize")).isFalse()
        assertThat(tracker.hasDelta(ElementId("btn2"), "Text")).isFalse()
    }

    @Test
    fun `hasDelta should return true for MoveElement delta when checking Anchor`() {
        val tracker = EditDeltaTracker()

        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(AnchorValue.absolute(left = 10f, top = 10f))
            )
        )

        assertThat(tracker.hasDelta(ElementId("btn1"), "Anchor")).isTrue()
        assertThat(tracker.hasDelta(ElementId("btn1"), "Text")).isFalse()
    }

    @Test
    fun `hasDelta should return true for RemoveProperty delta`() {
        val tracker = EditDeltaTracker()

        tracker.record(
            EditDeltaTracker.EditDelta.RemoveProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text"
            )
        )

        assertThat(tracker.hasDelta(ElementId("btn1"), "Text")).isTrue()
    }

    @Test
    fun `hasDelta should return false for structural deltas when checking properties`() {
        val tracker = EditDeltaTracker()

        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = null,
                index = 0,
                element = button("btn1")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.DeleteElement(
                elementId = ElementId("btn2")
            )
        )

        assertThat(tracker.hasDelta(ElementId("btn1"), "Text")).isFalse()
        assertThat(tracker.hasDelta(ElementId("btn2"), "Text")).isFalse()
    }

    // =====================================================================
    // Test 9: removeStructuralDelta removes add/delete deltas
    // =====================================================================

    @Test
    fun `removeStructuralDelta should remove AddElement delta`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1"))

        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = null,
                index = 0,
                element = button("btn2")
            )
        )

        tracker.removeStructuralDelta(ElementId("btn2"))

        val result = tracker.applyTo(doc)

        // btn2 should not be added since delta was removed
        assertThat(result.root.children).hasSize(1)
        assertThat(result.root.children[0].id).isEqualTo(ElementId("btn1"))
    }

    @Test
    fun `removeStructuralDelta should remove DeleteElement delta`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("container",
                button("btn1"),
                button("btn2")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.DeleteElement(
                elementId = ElementId("btn2")
            )
        )

        tracker.removeStructuralDelta(ElementId("btn2"))

        val result = tracker.applyTo(doc)

        // btn2 should still exist since delete delta was removed
        val container = result.findElementById(ElementId("container"))
        assertThat(container!!.children).hasSize(2)
        assertThat(container.children.map { it.id }).containsExactly(
            ElementId("btn1"),
            ElementId("btn2")
        )
    }

    @Test
    fun `removeStructuralDelta should not remove property deltas`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        tracker.removeStructuralDelta(ElementId("btn1"))

        val result = tracker.applyTo(doc)

        // Property delta should still be applied
        val resultButton = result.findElementById(ElementId("btn1"))
        assertThat(resultButton!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Updated"))
    }

    // =====================================================================
    // Test 10: applyTo with empty deltas returns document unchanged
    // =====================================================================

    @Test
    fun `applyTo should return document unchanged when no deltas recorded`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        val result = tracker.applyTo(doc)

        assertThat(result).isSameAs(doc) // Should return exact same instance
    }

    @Test
    fun `applyTo should return document unchanged after clear`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        tracker.clear()

        val result = tracker.applyTo(doc)

        assertThat(result).isSameAs(doc)
    }

    // =====================================================================
    // Test 11: Mixed structural + property deltas applied in correct order
    // =====================================================================

    @Test
    fun `structural deltas should be applied before property deltas`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1"))

        // Record in mixed order: property, then structural, then property
        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated btn1")
            )
        )

        val newButton = button("btn2", text = "Initial btn2")
        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = null,
                index = 1,
                element = newButton
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn2"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated btn2")
            )
        )

        val result = tracker.applyTo(doc)

        // Both structural and property deltas should be applied
        assertThat(result.root.children).hasSize(2)

        val btn1 = result.findElementById(ElementId("btn1"))
        assertThat(btn1!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Updated btn1"))

        val btn2 = result.findElementById(ElementId("btn2"))
        assertThat(btn2!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Updated btn2"))
    }

    @Test
    fun `property deltas on newly added elements should work correctly`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1"))

        // Add a new element
        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = null,
                index = 1,
                element = button("btn2", text = "Initial")
            )
        )

        // Then modify it
        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn2"),
                propertyName = "Text",
                value = PropertyValue.Text("Modified")
            )
        )

        val result = tracker.applyTo(doc)

        val btn2 = result.findElementById(ElementId("btn2"))
        assertThat(btn2).isNotNull
        assertThat(btn2!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Modified"))
    }

    @Test
    fun `delete and add of same element ID should result in the new element`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        // Delete the original
        tracker.record(
            EditDeltaTracker.EditDelta.DeleteElement(
                elementId = ElementId("btn1")
            )
        )

        // Add a new element with same ID
        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = null,
                index = 0,
                element = button("btn1", text = "Replaced")
            )
        )

        val result = tracker.applyTo(doc)

        // Should have the new element
        val btn1 = result.findElementById(ElementId("btn1"))
        assertThat(btn1).isNotNull
        assertThat(btn1!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Replaced"))
    }

    // =====================================================================
    // Test 12: Property delta on nonexistent element is a no-op
    // =====================================================================

    @Test
    fun `SetProperty on nonexistent element should be gracefully ignored`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1"))

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("nonexistent"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        val result = tracker.applyTo(doc)

        // Document should remain unchanged
        assertThat(result.root.children).hasSize(1)
        assertThat(result.root.children[0].id).isEqualTo(ElementId("btn1"))
    }

    @Test
    fun `MoveElement on nonexistent element should be gracefully ignored`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1"))

        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("nonexistent"),
                newAnchor = PropertyValue.Anchor(AnchorValue.absolute(left = 100f, top = 100f))
            )
        )

        val result = tracker.applyTo(doc)

        // Document should remain unchanged
        assertThat(result.root.children).hasSize(1)
        assertThat(result.root.children[0].id).isEqualTo(ElementId("btn1"))
    }

    @Test
    fun `RemoveProperty on nonexistent element should be gracefully ignored`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        tracker.record(
            EditDeltaTracker.EditDelta.RemoveProperty(
                elementId = ElementId("nonexistent"),
                propertyName = "Text"
            )
        )

        val result = tracker.applyTo(doc)

        // btn1 should still have its Text property
        val btn1 = result.findElementById(ElementId("btn1"))
        assertThat(btn1!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Original"))
    }

    // =====================================================================
    // Additional edge cases
    // =====================================================================

    @Test
    fun `AddElement at index 0 should prepend to children`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("container",
                button("btn1"),
                button("btn2")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = ElementId("container"),
                index = 0,
                element = button("btn0")
            )
        )

        val result = tracker.applyTo(doc)

        val container = result.findElementById(ElementId("container"))
        assertThat(container!!.children.map { it.id }).containsExactly(
            ElementId("btn0"),
            ElementId("btn1"),
            ElementId("btn2")
        )
    }

    @Test
    fun `multiple deletes should remove all specified elements`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("container",
                button("btn1"),
                button("btn2"),
                button("btn3"),
                button("btn4")
            )
        )

        tracker.record(EditDeltaTracker.EditDelta.DeleteElement(ElementId("btn2")))
        tracker.record(EditDeltaTracker.EditDelta.DeleteElement(ElementId("btn4")))

        val result = tracker.applyTo(doc)

        val container = result.findElementById(ElementId("container"))
        assertThat(container!!.children.map { it.id }).containsExactly(
            ElementId("btn1"),
            ElementId("btn3")
        )
    }

    @Test
    fun `property deltas on different properties of same element should all apply`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated Text")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "FontSize",
                value = PropertyValue.Number(24.0)
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "RenderBold",
                value = PropertyValue.Boolean(true)
            )
        )

        val result = tracker.applyTo(doc)

        val btn1 = result.findElementById(ElementId("btn1"))
        assertThat(btn1!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Updated Text"))
        assertThat(btn1.getProperty("FontSize")).isEqualTo(PropertyValue.Number(24.0))
        assertThat(btn1.getProperty("RenderBold")).isEqualTo(PropertyValue.Boolean(true))
    }

    @Test
    fun `nested element structure should be preserved through delta application`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("outer",
                group("inner",
                    button("btn1"),
                    label("lbl1")
                )
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        val result = tracker.applyTo(doc)

        val inner = result.findElementById(ElementId("inner"))
        assertThat(inner!!.children).hasSize(2)

        val btn1 = result.findElementById(ElementId("btn1"))
        assertThat(btn1!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Updated"))
    }

    // =====================================================================
    // Test 13: RenameElement delta changes element ID in raw document
    // =====================================================================

    @Test
    fun `RenameElement delta should change element ID`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Hello"))

        tracker.record(
            EditDeltaTracker.EditDelta.RenameElement(
                oldId = ElementId("btn1"),
                newId = ElementId("submitButton")
            )
        )

        val result = tracker.applyTo(doc)

        // Old ID should not be found
        assertThat(result.findElementById(ElementId("btn1"))).isNull()
        // New ID should exist with preserved properties
        val renamed = result.findElementById(ElementId("submitButton"))
        assertThat(renamed).isNotNull
        assertThat(renamed!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Hello"))
    }

    @Test
    fun `RenameElement should preserve element children`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("container",
                button("btn1"),
                button("btn2")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.RenameElement(
                oldId = ElementId("container"),
                newId = ElementId("mainPanel")
            )
        )

        val result = tracker.applyTo(doc)

        val renamed = result.findElementById(ElementId("mainPanel"))
        assertThat(renamed).isNotNull
        assertThat(renamed!!.children).hasSize(2)
        assertThat(renamed.children.map { it.id }).containsExactly(
            ElementId("btn1"),
            ElementId("btn2")
        )
    }

    @Test
    fun `RenameElement on nonexistent element should be gracefully ignored`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1"))

        tracker.record(
            EditDeltaTracker.EditDelta.RenameElement(
                oldId = ElementId("nonexistent"),
                newId = ElementId("newName")
            )
        )

        val result = tracker.applyTo(doc)

        // Document should remain unchanged
        assertThat(result.root.children).hasSize(1)
        assertThat(result.root.children[0].id).isEqualTo(ElementId("btn1"))
    }

    // =====================================================================
    // Test 14: RenameElement + subsequent property deltas use new ID
    // =====================================================================

    @Test
    fun `property deltas recorded with old ID should apply after rename`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        // Rename first
        tracker.record(
            EditDeltaTracker.EditDelta.RenameElement(
                oldId = ElementId("btn1"),
                newId = ElementId("submitBtn")
            )
        )

        // Then set property using the NEW ID (as the UI would after rename)
        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("submitBtn"),
                propertyName = "Text",
                value = PropertyValue.Text("Submit")
            )
        )

        val result = tracker.applyTo(doc)

        val renamed = result.findElementById(ElementId("submitBtn"))
        assertThat(renamed).isNotNull
        assertThat(renamed!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Submit"))
    }

    @Test
    fun `property deltas recorded before rename with old ID should still apply`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1", text = "Original"))

        // Set property using OLD ID (recorded before rename)
        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        // Then rename
        tracker.record(
            EditDeltaTracker.EditDelta.RenameElement(
                oldId = ElementId("btn1"),
                newId = ElementId("submitBtn")
            )
        )

        val result = tracker.applyTo(doc)

        val renamed = result.findElementById(ElementId("submitBtn"))
        assertThat(renamed).isNotNull
        assertThat(renamed!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Updated"))
    }

    @Test
    fun `MoveElement delta with old ID should apply after rename`() {
        val tracker = EditDeltaTracker()
        val doc = document(button("btn1"))

        // Move using old ID
        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(AnchorValue.absolute(left = 50f, top = 75f, width = 100f, height = 30f))
            )
        )

        // Then rename
        tracker.record(
            EditDeltaTracker.EditDelta.RenameElement(
                oldId = ElementId("btn1"),
                newId = ElementId("movedBtn")
            )
        )

        val result = tracker.applyTo(doc)

        val renamed = result.findElementById(ElementId("movedBtn"))
        assertThat(renamed).isNotNull
        val anchor = (renamed!!.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat((anchor.left as com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute).pixels).isEqualTo(50f)
        assertThat((anchor.top as com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute).pixels).isEqualTo(75f)
    }

    // =====================================================================
    // Test 15: RenameElement + AddElement (the original bug scenario)
    // =====================================================================

    @Test
    fun `rename followed by add element should preserve renamed ID`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("container",
                button("btn1", text = "Original")
            )
        )

        // User renames btn1 to submitButton
        tracker.record(
            EditDeltaTracker.EditDelta.RenameElement(
                oldId = ElementId("btn1"),
                newId = ElementId("submitButton")
            )
        )

        // User adds a new element
        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = ElementId("container"),
                index = 1,
                element = button("btn2", text = "New Button")
            )
        )

        val result = tracker.applyTo(doc)

        // Renamed element should keep its new name
        assertThat(result.findElementById(ElementId("btn1"))).isNull()
        val renamed = result.findElementById(ElementId("submitButton"))
        assertThat(renamed).isNotNull
        assertThat(renamed!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Original"))

        // New element should also exist
        val added = result.findElementById(ElementId("btn2"))
        assertThat(added).isNotNull
        assertThat(added!!.getProperty("Text")).isEqualTo(PropertyValue.Text("New Button"))

        // Container should have both
        val container = result.findElementById(ElementId("container"))
        assertThat(container!!.children).hasSize(2)
    }

    @Test
    fun `rename followed by delete of different element should preserve rename`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("container",
                button("btn1"),
                button("btn2")
            )
        )

        // Rename btn1
        tracker.record(
            EditDeltaTracker.EditDelta.RenameElement(
                oldId = ElementId("btn1"),
                newId = ElementId("renamedBtn")
            )
        )

        // Delete btn2
        tracker.record(
            EditDeltaTracker.EditDelta.DeleteElement(
                elementId = ElementId("btn2")
            )
        )

        val result = tracker.applyTo(doc)

        val container = result.findElementById(ElementId("container"))
        assertThat(container!!.children).hasSize(1)
        assertThat(container.children[0].id).isEqualTo(ElementId("renamedBtn"))
    }

    // =====================================================================
    // Test 16: hasChanges
    // =====================================================================

    @Test
    fun `hasChanges should return false on fresh tracker`() {
        val tracker = EditDeltaTracker()
        assertThat(tracker.hasChanges()).isFalse()
    }

    @Test
    fun `hasChanges should return true after recording a delta`() {
        val tracker = EditDeltaTracker()

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        assertThat(tracker.hasChanges()).isTrue()
    }

    @Test
    fun `hasChanges should return false after clear`() {
        val tracker = EditDeltaTracker()

        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1"),
                propertyName = "Text",
                value = PropertyValue.Text("Updated")
            )
        )

        assertThat(tracker.hasChanges()).isTrue()
        tracker.clear()
        assertThat(tracker.hasChanges()).isFalse()
    }

    @Test
    fun `hasChanges should return true for structural deltas`() {
        val tracker = EditDeltaTracker()

        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = null,
                index = 0,
                element = button("btn1")
            )
        )

        assertThat(tracker.hasChanges()).isTrue()
    }

    // =====================================================================
    // Additional edge cases
    // =====================================================================

    @Test
    fun `RemoveProperty should actually remove the property from element`() {
        val tracker = EditDeltaTracker()
        val element = UIElement(
            type = ElementType("Label"),
            id = ElementId("lbl1"),
            properties = PropertyMap.of(
                "Text" to PropertyValue.Text("Hello"),
                "FontSize" to PropertyValue.Number(14.0)
            )
        )
        val doc = document(element)

        tracker.record(
            EditDeltaTracker.EditDelta.RemoveProperty(
                elementId = ElementId("lbl1"),
                propertyName = "FontSize"
            )
        )

        val result = tracker.applyTo(doc)

        val lbl1 = result.findElementById(ElementId("lbl1"))
        assertThat(lbl1!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Hello"))
        assertThat(lbl1.getProperty("FontSize")).isNull()
    }

    // =====================================================================
    // Test 17: Duplicate element with deep-cloned IDs persists via AddElement delta
    // Regression: duplicateElement() was not recording an AddElement delta,
    // so duplicated groups and their children were lost on save.
    // =====================================================================

    @Test
    fun `AddElement delta with deep-cloned group should persist all children`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("container",
                group("TorsoBarFrame",
                    group("TorsoBarContainer",
                        button("TorsoBar", text = "Bar")
                    )
                )
            )
        )

        // Simulate what duplicateElement does: deep-clone with _copy suffix
        val original = group("TorsoBarFrame",
            group("TorsoBarContainer",
                button("TorsoBar", text = "Bar")
            )
        )
        val duplicated = original.mapDescendants { el ->
            if (el.id != null) {
                el.copy(id = ElementId("${el.id.value}_copy"))
            } else {
                el
            }
        }

        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = ElementId("container"),
                index = 1,
                element = duplicated
            )
        )

        val result = tracker.applyTo(doc)

        // Original elements should still exist
        assertThat(result.findElementById(ElementId("TorsoBarFrame"))).isNotNull
        assertThat(result.findElementById(ElementId("TorsoBarContainer"))).isNotNull
        assertThat(result.findElementById(ElementId("TorsoBar"))).isNotNull

        // Duplicated elements with _copy suffix should all exist
        assertThat(result.findElementById(ElementId("TorsoBarFrame_copy"))).isNotNull
        assertThat(result.findElementById(ElementId("TorsoBarContainer_copy"))).isNotNull
        assertThat(result.findElementById(ElementId("TorsoBar_copy"))).isNotNull

        // Container should have both original and duplicate
        val container = result.findElementById(ElementId("container"))
        assertThat(container!!.children).hasSize(2)
    }

    @Test
    fun `duplicated group children should have unique IDs that do not collide with originals`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("toolbar",
                button("btn1", text = "Save"),
                button("btn2", text = "Load")
            )
        )

        // Deep-clone the toolbar
        val original = group("toolbar",
            button("btn1", text = "Save"),
            button("btn2", text = "Load")
        )
        val duplicated = original.mapDescendants { el ->
            if (el.id != null) {
                el.copy(id = ElementId("${el.id.value}_copy"))
            } else {
                el
            }
        }

        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = null,
                index = 1,
                element = duplicated
            )
        )

        val result = tracker.applyTo(doc)

        // Collect all IDs in the document
        val allIds = mutableListOf<String>()
        result.root.visitDescendants { el ->
            el.id?.let { allIds.add(it.value) }
        }

        // No duplicates — every ID should appear exactly once
        assertThat(allIds).doesNotHaveDuplicates()
    }

    @Test
    fun `property deltas on duplicated children should apply independently from originals`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("panel",
                button("btn1", text = "Original")
            )
        )

        // Add duplicated element
        val duplicatedBtn = button("btn1_copy", text = "Original")
        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = ElementId("panel"),
                index = 1,
                element = duplicatedBtn
            )
        )

        // Modify only the copy's text
        tracker.record(
            EditDeltaTracker.EditDelta.SetProperty(
                elementId = ElementId("btn1_copy"),
                propertyName = "Text",
                value = PropertyValue.Text("Modified Copy")
            )
        )

        val result = tracker.applyTo(doc)

        // Original should be unchanged
        val original = result.findElementById(ElementId("btn1"))
        assertThat(original!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Original"))

        // Copy should have new text
        val copy = result.findElementById(ElementId("btn1_copy"))
        assertThat(copy!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Modified Copy"))
    }

    @Test
    fun `deleting duplicated child should not affect original`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("panel",
                button("btn1", text = "Keep Me")
            )
        )

        // Add duplicate
        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = ElementId("panel"),
                index = 1,
                element = button("btn1_copy", text = "Keep Me")
            )
        )

        // Delete the copy
        tracker.record(
            EditDeltaTracker.EditDelta.DeleteElement(
                elementId = ElementId("btn1_copy")
            )
        )

        val result = tracker.applyTo(doc)

        // Original should still exist
        val panel = result.findElementById(ElementId("panel"))
        assertThat(panel!!.children).hasSize(1)
        assertThat(panel.children[0].id).isEqualTo(ElementId("btn1"))
        assertThat(panel.children[0].getProperty("Text")).isEqualTo(PropertyValue.Text("Keep Me"))
    }

    @Test
    fun `renaming a duplicated child should not affect original`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("panel",
                button("btn1", text = "Hello")
            )
        )

        // Add duplicate
        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = ElementId("panel"),
                index = 1,
                element = button("btn1_copy", text = "Hello")
            )
        )

        // Rename the copy
        tracker.record(
            EditDeltaTracker.EditDelta.RenameElement(
                oldId = ElementId("btn1_copy"),
                newId = ElementId("btn2")
            )
        )

        val result = tracker.applyTo(doc)

        // Original unchanged
        val original = result.findElementById(ElementId("btn1"))
        assertThat(original).isNotNull
        assertThat(original!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Hello"))

        // Copy renamed
        assertThat(result.findElementById(ElementId("btn1_copy"))).isNull()
        val renamed = result.findElementById(ElementId("btn2"))
        assertThat(renamed).isNotNull
        assertThat(renamed!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Hello"))
    }

    // =====================================================================
    // Test 22: ReorderElement delta moves element within parent's children
    // =====================================================================

    @Test
    fun `ReorderElement delta moves child forward within parent`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("panel",
                button("btn1", text = "First"),
                button("btn2", text = "Second"),
                button("btn3", text = "Third")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.ReorderElement(
                parentId = ElementId("panel"),
                elementId = ElementId("btn1"),
                fromIndex = 0,
                toIndex = 2
            )
        )

        val result = tracker.applyTo(doc)
        val panel = result.findElementById(ElementId("panel"))
        assertThat(panel!!.children).hasSize(3)
        assertThat(panel.children[0].id).isEqualTo(ElementId("btn2"))
        assertThat(panel.children[1].id).isEqualTo(ElementId("btn3"))
        assertThat(panel.children[2].id).isEqualTo(ElementId("btn1"))
    }

    @Test
    fun `ReorderElement delta moves child backward within parent`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("panel",
                button("btn1", text = "First"),
                button("btn2", text = "Second"),
                button("btn3", text = "Third")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.ReorderElement(
                parentId = ElementId("panel"),
                elementId = ElementId("btn3"),
                fromIndex = 2,
                toIndex = 0
            )
        )

        val result = tracker.applyTo(doc)
        val panel = result.findElementById(ElementId("panel"))
        assertThat(panel!!.children).hasSize(3)
        assertThat(panel.children[0].id).isEqualTo(ElementId("btn3"))
        assertThat(panel.children[1].id).isEqualTo(ElementId("btn1"))
        assertThat(panel.children[2].id).isEqualTo(ElementId("btn2"))
    }

    @Test
    fun `ReorderElement delta at root level reorders root children`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            button("btn1", text = "First"),
            button("btn2", text = "Second"),
            button("btn3", text = "Third")
        )

        tracker.record(
            EditDeltaTracker.EditDelta.ReorderElement(
                parentId = null,
                elementId = ElementId("btn2"),
                fromIndex = 1,
                toIndex = 0
            )
        )

        val result = tracker.applyTo(doc)
        assertThat(result.root.children).hasSize(3)
        assertThat(result.root.children[0].id).isEqualTo(ElementId("btn2"))
        assertThat(result.root.children[1].id).isEqualTo(ElementId("btn1"))
        assertThat(result.root.children[2].id).isEqualTo(ElementId("btn3"))
    }

    // =====================================================================
    // Test 25: Move element (delete + add) preserves element in new parent
    // =====================================================================

    @Test
    fun `delete plus add deltas move element to a different parent`() {
        val tracker = EditDeltaTracker()
        val movedButton = button("btn1", text = "Moved")
        val doc = document(
            group("panel1", movedButton),
            group("panel2")
        )

        // Delete from panel1
        tracker.record(
            EditDeltaTracker.EditDelta.DeleteElement(
                elementId = ElementId("btn1")
            )
        )

        // Add to panel2
        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = ElementId("panel2"),
                index = 0,
                element = movedButton
            )
        )

        val result = tracker.applyTo(doc)

        val panel1 = result.findElementById(ElementId("panel1"))
        assertThat(panel1!!.children).isEmpty()

        val panel2 = result.findElementById(ElementId("panel2"))
        assertThat(panel2!!.children).hasSize(1)
        assertThat(panel2.children[0].id).isEqualTo(ElementId("btn1"))
    }

    // =====================================================================
    // Test 26: Wrap in group (delete + add) replaces element with group
    // =====================================================================

    @Test
    fun `delete plus add deltas simulate wrap-in-group operation`() {
        val tracker = EditDeltaTracker()
        val originalButton = button("btn1", text = "Wrapped")
        val doc = document(
            group("panel",
                originalButton,
                button("btn2", text = "Sibling")
            )
        )

        // Delete the original element
        tracker.record(
            EditDeltaTracker.EditDelta.DeleteElement(
                elementId = ElementId("btn1")
            )
        )

        // Add a group containing the element at the same position
        val wrappedChild = originalButton.copy(
            properties = PropertyMap.of(
                "Anchor" to PropertyValue.Anchor(AnchorValue.fill())
            )
        )
        val wrapGroup = UIElement(
            type = ElementType("Group"),
            id = ElementId("Group_wrap"),
            properties = originalButton.properties,
            children = listOf(wrappedChild)
        )
        tracker.record(
            EditDeltaTracker.EditDelta.AddElement(
                parentId = ElementId("panel"),
                index = 0,
                element = wrapGroup
            )
        )

        val result = tracker.applyTo(doc)

        val panel = result.findElementById(ElementId("panel"))
        assertThat(panel!!.children).hasSize(2)
        // Group should be at index 0 (where the original was)
        assertThat(panel.children[0].id).isEqualTo(ElementId("Group_wrap"))
        assertThat(panel.children[0].children).hasSize(1)
        assertThat(panel.children[0].children[0].id).isEqualTo(ElementId("btn1"))
        // Sibling should still be at index 1
        assertThat(panel.children[1].id).isEqualTo(ElementId("btn2"))
    }

    @Test
    fun `removeStructuralDelta removes ReorderElement deltas`() {
        val tracker = EditDeltaTracker()
        val doc = document(
            group("panel",
                button("btn1"),
                button("btn2")
            )
        )

        tracker.record(
            EditDeltaTracker.EditDelta.ReorderElement(
                parentId = ElementId("panel"),
                elementId = ElementId("btn1"),
                fromIndex = 0,
                toIndex = 1
            )
        )

        tracker.removeStructuralDelta(ElementId("btn1"))

        // After removing, applying should produce no change
        val result = tracker.applyTo(doc)
        val panel = result.findElementById(ElementId("panel"))
        assertThat(panel!!.children[0].id).isEqualTo(ElementId("btn1"))
        assertThat(panel.children[1].id).isEqualTo(ElementId("btn2"))
    }

    // =====================================================================
    // Test 27: Keyboard move (MoveElement delta) persists position change
    // Regression: moveSelectedElements() called executeCommand but didn't
    // record a MoveElement delta, so arrow-key nudges were lost on save.
    // =====================================================================

    @Test
    fun `MoveElement delta from keyboard nudge should persist new position`() {
        val tracker = EditDeltaTracker()
        val originalAnchor = AnchorValue.absolute(left = 50f, top = 100f, width = 200f, height = 40f)
        val doc = document(button("btn1", anchor = originalAnchor))

        // Simulate keyboard arrow nudge: move 1px right
        val nudgedAnchor = AnchorValue.absolute(left = 51f, top = 100f, width = 200f, height = 40f)
        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(nudgedAnchor)
            )
        )

        val result = tracker.applyTo(doc)

        val btn = result.findElementById(ElementId("btn1"))
        val anchor = (btn!!.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat((anchor.left as com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute).pixels).isEqualTo(51f)
        assertThat((anchor.top as com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute).pixels).isEqualTo(100f)
    }

    @Test
    fun `multiple keyboard nudges should collapse to final position`() {
        val tracker = EditDeltaTracker()
        val originalAnchor = AnchorValue.absolute(left = 50f, top = 100f, width = 200f, height = 40f)
        val doc = document(button("btn1", anchor = originalAnchor))

        // Simulate 3 arrow key presses (right, right, down)
        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(
                    AnchorValue.absolute(left = 51f, top = 100f, width = 200f, height = 40f)
                )
            )
        )
        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(
                    AnchorValue.absolute(left = 52f, top = 100f, width = 200f, height = 40f)
                )
            )
        )
        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(
                    AnchorValue.absolute(left = 52f, top = 101f, width = 200f, height = 40f)
                )
            )
        )

        val result = tracker.applyTo(doc)

        // Should collapse to final position
        val btn = result.findElementById(ElementId("btn1"))
        val anchor = (btn!!.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat((anchor.left as com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute).pixels).isEqualTo(52f)
        assertThat((anchor.top as com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute).pixels).isEqualTo(101f)
    }

    @Test
    fun `keyboard nudge on one element should not affect sibling positions`() {
        val tracker = EditDeltaTracker()
        val anchor1 = AnchorValue.absolute(left = 10f, top = 10f, width = 100f, height = 30f)
        val anchor2 = AnchorValue.absolute(left = 200f, top = 10f, width = 100f, height = 30f)
        val doc = document(
            button("btn1", anchor = anchor1),
            button("btn2", anchor = anchor2)
        )

        // Only move btn1
        tracker.record(
            EditDeltaTracker.EditDelta.MoveElement(
                elementId = ElementId("btn1"),
                newAnchor = PropertyValue.Anchor(
                    AnchorValue.absolute(left = 11f, top = 10f, width = 100f, height = 30f)
                )
            )
        )

        val result = tracker.applyTo(doc)

        // btn1 moved
        val btn1 = result.findElementById(ElementId("btn1"))
        val btn1Anchor = (btn1!!.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat((btn1Anchor.left as com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute).pixels).isEqualTo(11f)

        // btn2 unchanged
        val btn2 = result.findElementById(ElementId("btn2"))
        val btn2Anchor = (btn2!!.getProperty("Anchor") as PropertyValue.Anchor).anchor
        assertThat((btn2Anchor.left as com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute).pixels).isEqualTo(200f)
    }
}
