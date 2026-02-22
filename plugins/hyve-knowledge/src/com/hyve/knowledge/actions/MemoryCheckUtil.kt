// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.actions

import com.intellij.diagnostic.VMOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.Action
import javax.swing.JComponent
import java.awt.event.ActionEvent

/**
 * Pre-flight memory check for knowledge indexing operations.
 *
 * HNSW vector index building + embedding storage can easily exceed the default
 * heap of most IntelliJ installations. This utility warns the user and offers
 * to set a custom Xmx before indexing starts.
 */
object MemoryCheckUtil {

    private val log = Logger.getInstance(MemoryCheckUtil::class.java)
    private const val RECOMMENDED_HEAP_MB = 8192
    private const val MINIMUM_HEAP_MB = 2048

    enum class Result { PROCEED, RESTARTING, CANCELLED }

    /**
     * Checks IDE heap size and warns if it's below the recommended threshold.
     *
     * @return `true` if indexing should proceed, `false` if the user cancelled or IDE is restarting.
     */
    fun checkHeapAndWarn(project: Project): Boolean {
        val currentHeapMb = getEffectiveHeapMb()
        if (currentHeapMb < 0 || currentHeapMb >= RECOMMENDED_HEAP_MB) {
            return true
        }

        log.info("Current heap: ${currentHeapMb} MB, recommended: ${RECOMMENDED_HEAP_MB} MB")

        val dialog = HeapWarningDialog(project, currentHeapMb)
        return when (dialog.showAndGetResult()) {
            Result.PROCEED -> true
            Result.RESTARTING -> false
            Result.CANCELLED -> false
        }
    }

    internal fun getEffectiveHeapMb(): Int {
        val fromVmOptions = VMOptions.readOption(VMOptions.MemoryKind.HEAP, true)
        if (fromVmOptions > 0) return fromVmOptions

        val runtimeMaxMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
        return if (runtimeMaxMb > 0) runtimeMaxMb else -1
    }

    /**
     * Dialog that warns about low heap and lets the user type a custom Xmx value.
     */
    private class HeapWarningDialog(
        private val project: Project,
        private val currentHeapMb: Int,
    ) : DialogWrapper(project) {

        private val heapField = JBTextField(RECOMMENDED_HEAP_MB.toString(), 8)
        private var result: Result = Result.CANCELLED

        private lateinit var saveRestartAction: Action
        private lateinit var continueAction: Action

        init {
            title = "Low Memory Warning"
            init()
            initValidation()
        }

        override fun createCenterPanel(): JComponent {
            val message = JBLabel(
                "<html>" +
                    "Your IDE is currently allocated <b>$currentHeapMb MB</b> of heap memory.<br><br>" +
                    "Knowledge indexing (especially HNSW vector building) recommends at least<br>" +
                    "<b>$RECOMMENDED_HEAP_MB MB</b> to avoid out-of-memory crashes.<br><br>" +
                    "Set a new heap size (MB), or continue with the current allocation." +
                    "</html>",
            )

            return FormBuilder.createFormBuilder()
                .addComponent(message)
                .addVerticalGap(8)
                .addLabeledComponent("Heap size (MB):", heapField)
                .panel
                .apply { border = JBUI.Borders.empty(8) }
        }

        override fun createActions(): Array<Action> {
            val canRestart = ApplicationManager.getApplication().isRestartCapable
            val restartLabel = if (canRestart) "Save & Restart" else "Save & Exit"

            saveRestartAction = object : DialogWrapperAction(restartLabel) {
                override fun doAction(e: ActionEvent) {
                    if (doValidateAll().isNotEmpty()) return
                    val newValue = heapField.text.trim().toInt()
                    if (applyHeapAndRestart(newValue)) {
                        result = Result.RESTARTING
                        close(OK_EXIT_CODE)
                    }
                }
            }

            continueAction = object : DialogWrapperAction("Continue Anyway") {
                override fun doAction(e: ActionEvent) {
                    result = Result.PROCEED
                    close(OK_EXIT_CODE)
                }
            }

            return arrayOf(saveRestartAction, continueAction, cancelAction)
        }

        override fun doValidate(): ValidationInfo? {
            val text = heapField.text.trim()
            val value = text.toIntOrNull()
            if (value == null) {
                return ValidationInfo("Enter a number (e.g. $RECOMMENDED_HEAP_MB)", heapField)
            }
            if (value < MINIMUM_HEAP_MB) {
                return ValidationInfo("Minimum is $MINIMUM_HEAP_MB MB", heapField)
            }
            return null
        }

        fun showAndGetResult(): Result {
            if (!showAndGet() && result != Result.RESTARTING) {
                return Result.CANCELLED
            }
            return result
        }

        private fun applyHeapAndRestart(heapMb: Int): Boolean {
            try {
                VMOptions.setOption(VMOptions.MemoryKind.HEAP, heapMb)
                log.info("Set Xmx to ${heapMb}m, restarting IDE")
                ApplicationManager.getApplication().restart()
                return true
            } catch (e: Exception) {
                log.warn("Failed to update VM options", e)
                Messages.showErrorDialog(
                    project,
                    "Could not update memory settings automatically.\n\n" +
                        "Please increase -Xmx manually:\n" +
                        "  Help \u2192 Edit Custom VM Options \u2192 set -Xmx${heapMb}m\n\n" +
                        "Error: ${e.message}",
                    "Failed to Update Memory",
                )
                return false
            }
        }
    }
}
