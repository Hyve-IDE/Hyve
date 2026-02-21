package com.hyve.common.undo

/**
 * Generic command interface for undo/redo support.
 *
 * Commands encapsulate changes to an immutable state object of type [T].
 * Each command can execute (apply) and undo (revert) its changes,
 * returning a new state instance.
 *
 * Commands are immutable and must capture all state needed to
 * execute and undo themselves.
 *
 * @param T The type of state this command operates on
 */
interface UndoableCommand<T> {
    /**
     * Execute the command on the given state.
     * Returns the new state, or null if the command couldn't be applied.
     */
    fun execute(state: T): T?

    /**
     * Undo the command, reverting the state to its previous value.
     * Returns the reverted state, or null if undo couldn't be applied.
     */
    fun undo(state: T): T?

    /**
     * Human-readable description of what this command does.
     * Used for display in Edit menu (e.g., "Undo: Move Button #Header")
     */
    val description: String

    /**
     * Whether this command can be merged with [other].
     * Used for coalescing rapid typing or dragging into single undo entries.
     */
    fun canMergeWith(other: UndoableCommand<T>): Boolean = false

    /**
     * Merge this command with [other].
     * Returns the merged command, or null if merging isn't possible.
     */
    fun mergeWith(other: UndoableCommand<T>): UndoableCommand<T>? = null
}
