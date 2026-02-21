// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor.undo

import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.common.undo.UndoManager
import com.hyve.ui.state.command.DocumentCommand
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.command.undo.UndoManager as PlatformUndoManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Bridges the custom UndoManager with IntelliJ platform undo system.
 * Wraps each DocumentCommand execution in a UndoableAction and registers
 * it with the platform UndoManager so Ctrl+Z works in the IDE.
 *
 * Thread safety: All custom UndoManager calls are dispatched to EDT
 * via ApplicationManager to satisfy the thread safety contract.
 */
class HyveUndoBridge(
    private val project: Project,
    private val file: VirtualFile
) {
    private val customUndoManager = UndoManager<UIElement>()

    /** Current root element - updated after each command execution */
    var currentRoot: UIElement? = null

    /** Callback invoked when undo/redo changes the root element */
    var onRootChanged: ((UIElement) -> Unit)? = null

    /**
     * Execute a command through the bridge.
     * Registers the command with both custom and platform undo managers.
     */
    fun executeCommand(command: DocumentCommand, root: UIElement): UIElement? {
        val newRoot = customUndoManager.execute(command, root) ?: return null
        currentRoot = newRoot

        val action = HyveUndoableAction(
            bridge = this,
            file = file
        )

        PlatformUndoManager.getInstance(project).undoableActionPerformed(action)

        return newRoot
    }

    /**
     * Check if undo is available.
     */
    fun canUndo(): Boolean = customUndoManager.canUndo.value

    /**
     * Check if redo is available.
     */
    fun canRedo(): Boolean = customUndoManager.canRedo.value

    /**
     * Get the custom undo manager for direct access (e.g., dirty tracking).
     */
    fun getCustomUndoManager(): UndoManager<UIElement> = customUndoManager

    /**
     * Perform undo through the custom UndoManager and update state.
     */
    internal fun performUndo(): UIElement? {
        val root = currentRoot ?: return null
        val newRoot = customUndoManager.undo(root) ?: return null
        currentRoot = newRoot
        onRootChanged?.invoke(newRoot)
        return newRoot
    }

    /**
     * Perform redo through the custom UndoManager and update state.
     */
    internal fun performRedo(): UIElement? {
        val root = currentRoot ?: return null
        val newRoot = customUndoManager.redo(root) ?: return null
        currentRoot = newRoot
        onRootChanged?.invoke(newRoot)
        return newRoot
    }
}

/**
 * UndoableAction that delegates to the custom UndoManager via HyveUndoBridge.
 * Ensures all custom UndoManager calls happen on EDT for thread safety.
 */
private class HyveUndoableAction(
    private val bridge: HyveUndoBridge,
    private val file: VirtualFile
) : UndoableAction {

    override fun undo() {
        if (ApplicationManager.getApplication().isDispatchThread) {
            bridge.performUndo()
        } else {
            ApplicationManager.getApplication().invokeAndWait {
                bridge.performUndo()
            }
        }
    }

    override fun redo() {
        if (ApplicationManager.getApplication().isDispatchThread) {
            bridge.performRedo()
        } else {
            ApplicationManager.getApplication().invokeAndWait {
                bridge.performRedo()
            }
        }
    }

    override fun getAffectedDocuments(): Array<DocumentReference> {
        return arrayOf(
            DocumentReferenceManager.getInstance().create(file)
        )
    }

    override fun isGlobal(): Boolean = false
}
