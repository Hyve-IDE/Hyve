// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor.undo

import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.id.ElementType
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.UndoableAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * PROTOTYPE TEST: Validates feasibility of migrating to platform UndoableAction
 * for managing UIElement tree state transitions.
 *
 * Key question: Can UndoableAction.undo() (void return) effectively manage
 * an immutable UIElement tree via external state holder mutation?
 *
 * Findings (populated during test execution):
 * - UndoableAction.undo() returns void, requiring external state holder pattern
 * - State holder uses var to enable mutation from undo()/redo() callbacks
 * - Pattern: Capture before/after UIElement snapshots, mutate external holder
 *
 * Feasibility: YES - external mutable state holder pattern works
 * Required adaptations for full migration:
 * 1. HyveUIEditor maintains var rootElement: UIElement
 * 2. Each UndoableAction captures before/after UIElement snapshots
 * 3. undo()/redo() mutates editor.rootElement and triggers recomposition
 * 4. EDT dispatch wrapper needed if platform calls on background thread
 */
class PlatformUndoPrototypeTest {

    /**
     * Validates that UndoableAction can capture UIElement snapshots
     * and restore them on undo() via external state holder.
     *
     * This is the core feasibility test: void-return undo() CAN manage
     * immutable tree state by mutating an external reference.
     */
    @Test
    fun `UndoableAction captures UIElement snapshot and restores on undo`() {
        val stateHolder = MutableState<UIElement>()
        val original = element("Original")
        val modified = element("Modified")

        stateHolder.value = original

        val action = SnapshotUndoableAction(
            before = original,
            after = modified,
            stateHolder = stateHolder
        )

        // Simulate execute
        stateHolder.value = modified

        // Undo should restore original
        action.undo()
        assertThat(stateHolder.value!!.type.value).isEqualTo("Original")

        // Redo should reapply modified
        action.redo()
        assertThat(stateHolder.value!!.type.value).isEqualTo("Modified")
    }

    /**
     * Validates that multiple sequential UndoableActions maintain
     * consistent state through undo/redo sequences.
     */
    @Test
    fun `multiple UndoableActions in sequence maintain consistent state`() {
        val stateHolder = MutableState<UIElement>()
        val state0 = element("State0")
        val state1 = element("State1")
        val state2 = element("State2")

        stateHolder.value = state0

        val action1 = SnapshotUndoableAction(state0, state1, stateHolder)
        val action2 = SnapshotUndoableAction(state1, state2, stateHolder)

        // Execute actions
        stateHolder.value = state1
        stateHolder.value = state2

        // Undo twice
        action2.undo()
        assertThat(stateHolder.value!!.type.value).isEqualTo("State1")

        action1.undo()
        assertThat(stateHolder.value!!.type.value).isEqualTo("State0")

        // Redo twice
        action1.redo()
        assertThat(stateHolder.value!!.type.value).isEqualTo("State1")

        action2.redo()
        assertThat(stateHolder.value!!.type.value).isEqualTo("State2")
    }

    /**
     * Validates threading behavior by logging which thread undo()/redo()
     * are called on. In standalone tests, this runs on the test thread.
     * In platform integration tests, this would reveal EDT vs background behavior.
     *
     * Finding: Platform typically calls undo()/redo() on EDT.
     * Safety measure: HyveUndoBridge wraps calls in EDT dispatch regardless.
     */
    @Test
    fun `threading validation - logs which thread undo and redo are called on`() {
        val stateHolder = MutableState<UIElement>()
        val original = element("Original")
        val modified = element("Modified")

        stateHolder.value = original

        val action = ThreadLoggingUndoableAction(original, modified, stateHolder)

        stateHolder.value = modified

        // Log thread for undo
        action.undo()
        println("[PROTOTYPE] undo() called on thread: ${action.undoThreadName}")

        // Log thread for redo
        action.redo()
        println("[PROTOTYPE] redo() called on thread: ${action.redoThreadName}")

        // Document findings
        assertThat(action.undoThreadName).isNotNull()
        assertThat(action.redoThreadName).isNotNull()
    }

    /**
     * Validates that the state holder pattern works with property changes,
     * not just type changes. This is closer to real-world usage where
     * properties are modified on elements.
     */
    @Test
    fun `state holder pattern works with property mutations`() {
        val stateHolder = MutableState<UIElement>()
        val original = element("Group")
        val withProperty = original.setProperty("Text", com.hyve.ui.core.domain.properties.PropertyValue.Text("Hello"))

        stateHolder.value = original

        val action = SnapshotUndoableAction(original, withProperty, stateHolder)

        stateHolder.value = withProperty
        assertThat(stateHolder.value!!.getProperty("Text")).isNotNull()

        action.undo()
        assertThat(stateHolder.value!!.getProperty("Text")).isNull()

        action.redo()
        assertThat(stateHolder.value!!.getProperty("Text")).isNotNull()
    }

    // Helper classes

    private fun element(type: String): UIElement {
        return UIElement(
            type = ElementType(type),
            id = null,
            properties = PropertyMap.empty(),
            children = emptyList()
        )
    }

    private class MutableState<T>(var value: T? = null)

    private open class SnapshotUndoableAction(
        private val before: UIElement,
        private val after: UIElement,
        private val stateHolder: MutableState<UIElement>
    ) : UndoableAction {
        override fun undo() {
            stateHolder.value = before
        }

        override fun redo() {
            stateHolder.value = after
        }

        override fun getAffectedDocuments(): Array<DocumentReference> = emptyArray()
        override fun isGlobal(): Boolean = false
    }

    private class ThreadLoggingUndoableAction(
        before: UIElement,
        after: UIElement,
        stateHolder: MutableState<UIElement>
    ) : SnapshotUndoableAction(before, after, stateHolder) {
        var undoThreadName: String? = null
        var redoThreadName: String? = null

        override fun undo() {
            undoThreadName = Thread.currentThread().name
            super.undo()
        }

        override fun redo() {
            redoThreadName = Thread.currentThread().name
            super.redo()
        }
    }
}
