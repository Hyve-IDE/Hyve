// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.FormBuilder
import java.io.File
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.FlowLayout

/**
 * Parent configurable for all Hyve settings under Tools → Hyve.
 *
 * Hosts the shared Hytale install path configuration.
 */
class HyveSettingsConfigurable : SearchableConfigurable {

    companion object {
        const val ID = "hyve.settings"
        const val DISPLAY_NAME = "Hyve"
    }

    private var mainPanel: JPanel? = null
    private var installPathField: TextFieldWithBrowseButton? = null
    private var statusLabel: JBLabel? = null
    private var assetsStatusLabel: JBLabel? = null
    private var clientStatusLabel: JBLabel? = null
    private var serverStatusLabel: JBLabel? = null

    // Path override fields
    private var assetsZipOverrideField: TextFieldWithBrowseButton? = null
    private var serverJarOverrideField: TextFieldWithBrowseButton? = null
    private var modsOverrideField: TextFieldWithBrowseButton? = null

    override fun getId(): String = ID

    override fun getDisplayName(): String = DISPLAY_NAME

    override fun createComponent(): JComponent {
        installPathField = TextFieldWithBrowseButton().apply {
            text = HytaleInstallPath.get()?.toString() ?: ""
            addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    .withTitle("Select Hytale Installation")
                    .withDescription("Choose the Hytale game root folder")
            )
        }

        statusLabel = JBLabel()
        assetsStatusLabel = JBLabel()
        clientStatusLabel = JBLabel()
        serverStatusLabel = JBLabel()

        val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            add(JButton("Validate").apply {
                addActionListener { updateAllStatusLabels() }
            })
            add(JButton("Auto-detect").apply {
                addActionListener {
                    val detected = HytalePathDetector.detect()
                    if (detected != null) {
                        installPathField?.text = detected.toString()
                    }
                    updateAllStatusLabels()
                }
            })
            add(JButton("Clear").apply {
                addActionListener {
                    installPathField?.text = ""
                    updateAllStatusLabels()
                }
            })
        }

        // --- Path override fields ---
        val derivedAssets = HytaleInstallPath.assetsZipPath()?.toString() ?: ""
        val derivedJar = HytaleInstallPath.serverJarPath()?.toString() ?: ""
        val derivedMods = HytaleInstallPath.serverModsPath()?.toString() ?: ""

        assetsZipOverrideField = TextFieldWithBrowseButton().apply {
            text = HytaleInstallPath.getOverride(HytaleInstallPath.KEY_ASSETS_ZIP) ?: ""
            addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor("zip")
                    .withTitle("Select Assets.zip")
                    .withDescription("Choose the Hytale Assets.zip file")
            )
            (textField as? JBTextField)?.emptyText?.setText(derivedAssets.ifBlank { "Derived from install path" })
        }

        serverJarOverrideField = TextFieldWithBrowseButton().apply {
            text = HytaleInstallPath.getOverride(HytaleInstallPath.KEY_SERVER_JAR) ?: ""
            addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor("jar")
                    .withTitle("Select HytaleServer.jar")
                    .withDescription("Choose the Hytale server JAR file")
            )
            (textField as? JBTextField)?.emptyText?.setText(derivedJar.ifBlank { "Derived from install path" })
        }

        modsOverrideField = TextFieldWithBrowseButton().apply {
            text = HytaleInstallPath.getOverride(HytaleInstallPath.KEY_SERVER_MODS) ?: ""
            addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    .withTitle("Select Mods Folder")
                    .withDescription("Choose the Hytale server mods directory")
            )
            (textField as? JBTextField)?.emptyText?.setText(derivedMods.ifBlank { "Derived from install path" })
        }

        val browseFolderButton = JButton("Browse Folder\u2026").apply {
            addActionListener {
                val chooser = javax.swing.JFileChooser().apply {
                    fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Select folder containing Hytale files"
                }
                if (chooser.showOpenDialog(mainPanel) == javax.swing.JFileChooser.APPROVE_OPTION) {
                    val dir = chooser.selectedFile
                    val zip = File(dir, "Assets.zip")
                    if (zip.isFile) assetsZipOverrideField?.text = zip.absolutePath
                    val jar = File(dir, "Server/HytaleServer.jar")
                    if (jar.isFile) serverJarOverrideField?.text = jar.absolutePath
                    val mods = File(dir, "Server/Mods")
                    if (mods.isDirectory) modsOverrideField?.text = mods.absolutePath
                }
            }
        }

        val overrideButtonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            add(browseFolderButton)
            add(JButton("Clear Overrides").apply {
                addActionListener {
                    assetsZipOverrideField?.text = ""
                    serverJarOverrideField?.text = ""
                    modsOverrideField?.text = ""
                }
            })
        }

        updateAllStatusLabels()

        mainPanel = FormBuilder.createFormBuilder()
            .addSeparator()
            .addComponent(JBLabel("<html><b>Hytale Installation</b></html>"))
            .addLabeledComponent("Install Path:", installPathField!!)
            .addComponent(JBLabel("<html><font size='-2' color='gray'>Root of the Hytale game installation (contains Assets.zip, Client/, Server/)</font></html>"))
            .addComponent(statusLabel!!)
            .addComponent(buttonsPanel)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Detected Resources</b></html>"))
            .addComponent(assetsStatusLabel!!)
            .addComponent(clientStatusLabel!!)
            .addComponent(serverStatusLabel!!)
            .addSeparator()
            .addComponent(TitledSeparator("Path Overrides"))
            .addComponent(JBLabel("<html><font size='-2' color='gray'>Override individual paths when they differ from the install path. Leave blank to use derived defaults.</font></html>"))
            .addLabeledComponent("Assets zip:", assetsZipOverrideField!!)
            .addLabeledComponent("Server jar:", serverJarOverrideField!!)
            .addLabeledComponent("Mods folder:", modsOverrideField!!)
            .addComponent(overrideButtonsPanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return mainPanel!!
    }

    private fun updateAllStatusLabels() {
        val pathText = installPathField?.text ?: ""

        if (pathText.isBlank()) {
            statusLabel?.text = "<html><font color='#808080'>Not configured (auto-detect will be used)</font></html>"
            assetsStatusLabel?.text = ""
            clientStatusLabel?.text = ""
            serverStatusLabel?.text = ""
            return
        }

        val root = File(pathText)
        if (!root.exists() || !root.isDirectory) {
            statusLabel?.text = "<html><font color='#CC7832'>Directory not found: $pathText</font></html>"
            assetsStatusLabel?.text = ""
            clientStatusLabel?.text = ""
            serverStatusLabel?.text = ""
            return
        }

        val serverJar = File(root, "Server/HytaleServer.jar")
        if (serverJar.exists()) {
            statusLabel?.text = "<html><font color='#6A8759'>Valid Hytale installation</font></html>"
        } else {
            statusLabel?.text = "<html><font color='#CC7832'>Server/HytaleServer.jar not found</font></html>"
        }

        // Assets.zip — check override first, then derived
        val assetsOverride = assetsZipOverrideField?.text?.takeIf { it.isNotBlank() }
        if (assetsOverride != null) {
            val overrideFile = File(assetsOverride)
            assetsStatusLabel?.text = if (overrideFile.isFile) {
                "<html><font color='#6A8759'>  Assets.zip (override): $assetsOverride</font></html>"
            } else {
                "<html><font color='#CC7832'>  Assets.zip override not found: $assetsOverride</font></html>"
            }
        } else {
            val assetsZip = File(root, "Assets.zip")
            assetsStatusLabel?.text = if (assetsZip.exists()) {
                "<html><font color='#6A8759'>  Assets.zip found</font></html>"
            } else {
                "<html><font color='#CC7832'>  Assets.zip not found</font></html>"
            }
        }

        // Client folder + .ui file count
        val clientDir = File(root, "Client")
        val interfaceDir = File(clientDir, "Data/Game/Interface")
        clientStatusLabel?.text = if (interfaceDir.exists() && interfaceDir.isDirectory) {
            val uiCount = interfaceDir.walkTopDown()
                .filter { it.extension == "ui" }
                .count()
            "<html><font color='#6A8759'>  Client folder found ($uiCount .ui files)</font></html>"
        } else if (clientDir.exists()) {
            "<html><font color='#CC7832'>  Client folder found (Interface subfolder missing)</font></html>"
        } else {
            "<html><font color='#CC7832'>  Client folder not found</font></html>"
        }

        // Server jar — check override first, then derived
        val jarOverride = serverJarOverrideField?.text?.takeIf { it.isNotBlank() }
        if (jarOverride != null) {
            val overrideFile = File(jarOverride)
            serverStatusLabel?.text = if (overrideFile.isFile) {
                "<html><font color='#6A8759'>  Server jar (override): $jarOverride</font></html>"
            } else {
                "<html><font color='#CC7832'>  Server jar override not found: $jarOverride</font></html>"
            }
        } else {
            serverStatusLabel?.text = if (serverJar.exists()) {
                "<html><font color='#6A8759'>  Server/HytaleServer.jar found</font></html>"
            } else {
                "<html><font color='#CC7832'>  Server/HytaleServer.jar not found</font></html>"
            }
        }
    }

    override fun isModified(): Boolean {
        val current = installPathField?.text ?: ""
        val saved = HytaleInstallPath.get()?.toString() ?: ""
        if (current != saved) return true

        val assetsOverride = assetsZipOverrideField?.text ?: ""
        val savedAssets = HytaleInstallPath.getOverride(HytaleInstallPath.KEY_ASSETS_ZIP) ?: ""
        if (assetsOverride != savedAssets) return true

        val jarOverride = serverJarOverrideField?.text ?: ""
        val savedJar = HytaleInstallPath.getOverride(HytaleInstallPath.KEY_SERVER_JAR) ?: ""
        if (jarOverride != savedJar) return true

        val modsOverride = modsOverrideField?.text ?: ""
        val savedMods = HytaleInstallPath.getOverride(HytaleInstallPath.KEY_SERVER_MODS) ?: ""
        if (modsOverride != savedMods) return true

        return false
    }

    override fun apply() {
        val pathText = installPathField?.text
        if (!pathText.isNullOrBlank()) {
            HytaleInstallPath.set(File(pathText).toPath())
        } else {
            HytaleInstallPath.clear()
        }

        applyOverride(HytaleInstallPath.KEY_ASSETS_ZIP, assetsZipOverrideField?.text)
        applyOverride(HytaleInstallPath.KEY_SERVER_JAR, serverJarOverrideField?.text)
        applyOverride(HytaleInstallPath.KEY_SERVER_MODS, modsOverrideField?.text)

        updateAllStatusLabels()
    }

    private fun applyOverride(key: String, value: String?) {
        if (!value.isNullOrBlank()) {
            HytaleInstallPath.setOverride(key, value)
        } else {
            HytaleInstallPath.clearOverride(key)
        }
    }

    override fun reset() {
        installPathField?.text = HytaleInstallPath.get()?.toString() ?: ""
        assetsZipOverrideField?.text = HytaleInstallPath.getOverride(HytaleInstallPath.KEY_ASSETS_ZIP) ?: ""
        serverJarOverrideField?.text = HytaleInstallPath.getOverride(HytaleInstallPath.KEY_SERVER_JAR) ?: ""
        modsOverrideField?.text = HytaleInstallPath.getOverride(HytaleInstallPath.KEY_SERVER_MODS) ?: ""
        updateAllStatusLabels()
    }
}
