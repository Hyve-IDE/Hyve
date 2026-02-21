package com.hyve.mod.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.*
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTextField

class HytaleServerSettingsEditor(
    private val project: Project,
) : SettingsEditor<HytaleServerRunConfiguration>() {

    private val installPathField = TextFieldWithBrowseButton()
    private val debugCheckbox = JCheckBox()
    private val debugPortField = JTextField()
    private val vmArgsField = JTextField()
    private val programArgsField = JTextField()

    init {
        installPathField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Hytale Installation")
        )
        debugCheckbox.addActionListener {
            debugPortField.isEnabled = debugCheckbox.isSelected
        }
    }

    override fun createEditor(): JComponent = panel {
        row("Install path:") {
            cell(installPathField)
                .align(AlignX.FILL)
                .resizableColumn()
                .comment("Root directory containing Server/HytaleServer.jar")
        }

        row("Enable debug:") {
            cell(debugCheckbox)
                .comment("Attach JDWP debug agent to the server process")
        }

        row("Debug port:") {
            cell(debugPortField)
                .align(AlignX.FILL)
                .comment("JDWP listen port (default 5005)")
        }

        row("VM arguments:") {
            cell(vmArgsField)
                .align(AlignX.FILL)
                .comment("Additional JVM flags (e.g. -Xmx4G)")
        }

        row("Program arguments:") {
            cell(programArgsField)
                .align(AlignX.FILL)
                .comment("Arguments passed to HytaleServer.jar")
        }
    }

    override fun applyEditorTo(config: HytaleServerRunConfiguration) {
        config.installPath = installPathField.text
        config.enableDebug = debugCheckbox.isSelected
        config.debugPort = debugPortField.text.toIntOrNull() ?: 5005
        config.vmArgs = vmArgsField.text
        config.programArgs = programArgsField.text
    }

    override fun resetEditorFrom(config: HytaleServerRunConfiguration) {
        installPathField.text = config.installPath
        debugCheckbox.isSelected = config.enableDebug
        debugPortField.isEnabled = config.enableDebug
        debugPortField.text = config.debugPort.toString()
        vmArgsField.text = config.vmArgs
        programArgsField.text = config.programArgs
    }
}
