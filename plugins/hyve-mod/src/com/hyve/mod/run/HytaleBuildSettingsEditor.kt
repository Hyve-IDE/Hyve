package com.hyve.mod.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.*
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JTextField

class HytaleBuildSettingsEditor(
    @Suppress("unused") private val project: Project,
) : SettingsEditor<HytaleBuildRunConfiguration>() {

    private val taskCombo = ComboBox(DefaultComboBoxModel(TASK_OPTIONS)).apply { isEditable = true }
    private val extraArgsField = JTextField()

    override fun createEditor(): JComponent = panel {
        row("Gradle task:") {
            cell(taskCombo)
                .align(AlignX.FILL)
                .comment("The Gradle task to execute")
        }

        row("Extra arguments:") {
            cell(extraArgsField)
                .align(AlignX.FILL)
                .comment("Additional Gradle arguments (e.g. --info --stacktrace)")
        }
    }

    override fun applyEditorTo(config: HytaleBuildRunConfiguration) {
        config.gradleTask = (taskCombo.selectedItem as? String) ?: "shadowJar"
        config.extraArgs = extraArgsField.text
    }

    override fun resetEditorFrom(config: HytaleBuildRunConfiguration) {
        taskCombo.selectedItem = config.gradleTask
        extraArgsField.text = config.extraArgs
    }

    companion object {
        private val TASK_OPTIONS = arrayOf("shadowJar", "deployMod", "cleanDeploy", "build", "clean")
    }
}
