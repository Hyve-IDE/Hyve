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
}
