package com.hyve.common.undo

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * Generic undo/redo stack manager for immutable state of type [T].
 *
 * Features:
 * - Configurable stack depth limit (default: 100)
 * - Command merging for coalescing rapid edits
 * - Observable state for Compose UI binding
 * - Dirty tracking for unsaved changes
 *
 * Thread safety: This class is NOT thread-safe. All operations
 * should be called from the UI thread.
 *
 * @param T The type of state managed by this undo manager
 */
class UndoManager<T>(
    private val maxStackSize: Int = DEFAULT_STACK_SIZE,
    /**
     * Time window (ms) for merging commands.
     * Commands executed within this window of each other may be merged.
     */
    private val mergeWindowMs: Long = DEFAULT_MERGE_WINDOW_MS
) {
    companion object {
        const val DEFAULT_STACK_SIZE = 100
        const val DEFAULT_MERGE_WINDOW_MS = 500L
    }

    // Undo stack (most recent at the end)
    private val undoStack = ArrayDeque<CommandEntry<T>>()

    // Redo stack (most recent at the end)
    private val redoStack = ArrayDeque<CommandEntry<T>>()

    // Observable state for can undo/redo
    private val _canUndo = mutableStateOf(false)
    val canUndo: State<Boolean> = _canUndo

    private val _canRedo = mutableStateOf(false)
    val canRedo: State<Boolean> = _canRedo

    // Description of the next undo/redo action
    private val _undoDescription = mutableStateOf<String?>(null)
    val undoDescription: State<String?> = _undoDescription

    private val _redoDescription = mutableStateOf<String?>(null)
    val redoDescription: State<String?> = _redoDescription

    // Dirty flag (true if there are undoable changes since last save)
    private val _isDirty = mutableStateOf(false)
    val isDirty: State<Boolean> = _isDirty

    // Track the command count at last save for dirty tracking
    private var commandCountAtSave = 0
    private var totalCommandsExecuted = 0

    /**
     * Execute a command and add it to the undo stack.
     *
     * @param command The command to execute
     * @param state The current state
     * @param allowMerge If true, attempt to merge with previous command
     * @return The new state after executing the command, or null if failed
     */
    fun execute(
        command: UndoableCommand<T>,
        state: T,
        allowMerge: Boolean = true
    ): T? {
        val newState = command.execute(state) ?: return null

        val now = System.currentTimeMillis()

        // Try to merge with previous command
        if (allowMerge && undoStack.isNotEmpty()) {
            val lastEntry = undoStack.last()
            val timeSinceLastCommand = now - lastEntry.timestamp

            if (timeSinceLastCommand < mergeWindowMs && lastEntry.command.canMergeWith(command)) {
                val mergedCommand = lastEntry.command.mergeWith(command)
                if (mergedCommand != null) {
                    // Replace last command with merged command
                    undoStack.removeLast()
                    undoStack.addLast(CommandEntry(mergedCommand, now))
                    updateState()
                    return newState
                }
            }
        }

        // Add new command to undo stack
        undoStack.addLast(CommandEntry(command, now))
        totalCommandsExecuted++

        // Clear redo stack when new command is executed
        redoStack.clear()

        // Enforce stack size limit
        while (undoStack.size > maxStackSize) {
            undoStack.removeFirst()
        }

        updateState()
        return newState
    }

    /**
     * Undo the last command.
     *
     * @param state The current state
     * @return The state after undoing, or null if undo failed
     */
    fun undo(state: T): T? {
        if (undoStack.isEmpty()) return null

        val entry = undoStack.removeLast()
        val newState = entry.command.undo(state)

        if (newState != null) {
            redoStack.addLast(entry)
        } else {
            // Failed to undo - put it back
            undoStack.addLast(entry)
        }

        updateState()
        return newState
    }

    /**
     * Redo the last undone command.
     *
     * @param state The current state
     * @return The state after redoing, or null if redo failed
     */
    fun redo(state: T): T? {
        if (redoStack.isEmpty()) return null

        val entry = redoStack.removeLast()
        val newState = entry.command.execute(state)

        if (newState != null) {
            undoStack.addLast(entry)
        } else {
            // Failed to redo - put it back
            redoStack.addLast(entry)
        }

        updateState()
        return newState
    }

    /**
     * Mark the current state as saved (clears dirty flag).
     */
    fun markSaved() {
        commandCountAtSave = undoStack.size
        updateState()
    }

    /**
     * Clear all undo/redo history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        totalCommandsExecuted = 0
        commandCountAtSave = 0
        updateState()
    }

    /**
     * Push a command to the undo stack without executing it.
     * Used when a command has already been applied directly (e.g., during drag)
     * and we just want to record it for undo purposes.
     *
     * @param command The command to push (should represent the change that was already made)
     */
    fun pushWithoutExecute(command: UndoableCommand<T>) {
        val now = System.currentTimeMillis()

        undoStack.addLast(CommandEntry(command, now))
        totalCommandsExecuted++

        // Clear redo stack when new command is added
        redoStack.clear()

        // Enforce stack size limit
        while (undoStack.size > maxStackSize) {
            undoStack.removeFirst()
        }

        updateState()
    }

    /**
     * Get the number of undoable commands.
     */
    fun undoCount(): Int = undoStack.size

    /**
     * Get the number of redoable commands.
     */
    fun redoCount(): Int = redoStack.size

    /**
     * Get all undo commands (for debugging/display).
     */
    fun undoCommands(): List<UndoableCommand<T>> = undoStack.map { it.command }

    /**
     * Get all redo commands (for debugging/display).
     */
    fun redoCommands(): List<UndoableCommand<T>> = redoStack.map { it.command }

    private fun updateState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()

        _undoDescription.value = undoStack.lastOrNull()?.command?.description
        _redoDescription.value = redoStack.lastOrNull()?.command?.description

        // Calculate dirty state - we're dirty if we're at a different position
        // than when we last saved (or if never saved and have changes)
        _isDirty.value = undoStack.size != commandCountAtSave
    }

    /**
     * Entry in the undo/redo stack with timestamp for merge window.
     */
    private data class CommandEntry<T>(
        val command: UndoableCommand<T>,
        val timestamp: Long
    )
}
