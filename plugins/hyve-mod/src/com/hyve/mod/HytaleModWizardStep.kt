package com.hyve.mod

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.hyve.common.settings.HytalePathDetector
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

class HytaleModWizardStep(
    private val modMetadata: ModMetadata,
) : ModuleWizardStep() {

    private val propertyGraph = PropertyGraph()
    private val authorNameProp = propertyGraph.lazyProperty { modMetadata.authorName }
    private val authorEmailProp = propertyGraph.lazyProperty { modMetadata.authorEmail }
    private val authorUrlProp = propertyGraph.lazyProperty { modMetadata.authorUrl }
    private val descriptionProp = propertyGraph.lazyProperty { modMetadata.description }
    private val versionProp = propertyGraph.lazyProperty { modMetadata.modVersion }
    private val licenseProp = propertyGraph.lazyProperty { modMetadata.license }
    private val installPathProp = propertyGraph.lazyProperty { modMetadata.hytaleInstallPath }

    private val contentPanel: DialogPanel by lazy { createPanel() }

    private val wrapper: JComponent by lazy {
        JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(contentPanel, GridBagConstraints().apply {
                gridx = 0; gridy = 0
                weightx = 1.0; weighty = 1.0
                fill = GridBagConstraints.BOTH
                anchor = GridBagConstraints.NORTHWEST
                insets = Insets(20, 20, 20, 20)
            })
        }
    }

    override fun getComponent(): JComponent = wrapper

    private fun createPanel(): DialogPanel = panel {
        row("Author Name:") {
            textField()
                .bindText(authorNameProp)
                .align(AlignX.FILL)
        }.bottomGap(BottomGap.SMALL)

        row("Author Email:") {
            textField()
                .bindText(authorEmailProp)
                .align(AlignX.FILL)
                .comment("Optional")
        }.bottomGap(BottomGap.SMALL)

        row("Author URL:") {
            textField()
                .bindText(authorUrlProp)
                .align(AlignX.FILL)
                .comment("Optional")
        }.bottomGap(BottomGap.SMALL)

        row("Description:") {
            textArea()
                .bindText(descriptionProp)
                .align(AlignX.FILL)
                .rows(3)
                .comment("Optional")
        }.resizableRow().bottomGap(BottomGap.SMALL)

        row("Version:") {
            textField()
                .bindText(versionProp)
                .align(AlignX.FILL)
                .validationOnInput {
                    val text = it.text
                    if (text.isNotBlank() && !VERSION_REGEX.matches(text)) {
                        ValidationInfo("Semver format required (e.g. 1.0.0 or 1.0.0-beta)", it)
                    } else null
                }
        }.bottomGap(BottomGap.SMALL)

        row("License:") {
            comboBox(DefaultComboBoxModel(LICENSE_OPTIONS))
                .bindItem(licenseProp)
                .align(AlignX.FILL)
        }.bottomGap(BottomGap.SMALL)

        separator()

        row("Hytale Install Path:") {
            textFieldWithBrowseButton(
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    .withTitle("Select Hytale Installation")
            ).bindText(installPathProp)
                .align(AlignX.FILL)
                .comment("Must contain Server/HytaleServer.jar")
                .validationOnInput {
                    val text = it.text
                    if (text.isNotBlank() && !HytalePathDetector.isValidInstallPath(java.nio.file.Paths.get(text))) {
                        ValidationInfo("Server/HytaleServer.jar not found at this path", it)
                    } else null
                }
        }

        row {
            @Suppress("DialogTitleCapitalization")
            comment("Leave blank to configure later in Tools > Hyve")
        }
    }

    override fun updateDataModel() {
        contentPanel.apply()
        modMetadata.authorName = authorNameProp.get()
        modMetadata.authorEmail = authorEmailProp.get()
        modMetadata.authorUrl = authorUrlProp.get()
        modMetadata.description = descriptionProp.get()
        modMetadata.modVersion = versionProp.get()
        modMetadata.license = licenseProp.get()
        modMetadata.hytaleInstallPath = installPathProp.get()
    }

    override fun validate(): Boolean {
        contentPanel.apply()
        return contentPanel.validateAll().isEmpty()
    }

    companion object {
        private val VERSION_REGEX = Regex("""^\d+\.\d+\.\d+(-[a-zA-Z0-9]+)?$""")
        private val LICENSE_OPTIONS = arrayOf("MIT", "Apache-2.0", "GPL-3.0", "None")
    }
}

class ModMetadata {
    var authorName: String = System.getProperty("user.name") ?: ""
    var authorEmail: String = ""
    var authorUrl: String = ""
    var description: String = ""
    var modVersion: String = "1.0.0"
    var license: String = "MIT"
    var hytaleInstallPath: String = HytaleInstallSettings.getInstallPath()?.toString() ?: ""
}
