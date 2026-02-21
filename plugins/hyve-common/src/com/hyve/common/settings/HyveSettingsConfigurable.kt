// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
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

        // Assets.zip status
        val assetsZip = File(root, "Assets.zip")
        assetsStatusLabel?.text = if (assetsZip.exists()) {
            "<html><font color='#6A8759'>  Assets.zip found</font></html>"
        } else {
            "<html><font color='#CC7832'>  Assets.zip not found</font></html>"
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

        // Server jar status
        serverStatusLabel?.text = if (serverJar.exists()) {
            "<html><font color='#6A8759'>  Server/HytaleServer.jar found</font></html>"
        } else {
            "<html><font color='#CC7832'>  Server/HytaleServer.jar not found</font></html>"
        }
    }

    override fun isModified(): Boolean {
        val current = installPathField?.text ?: ""
        val saved = HytaleInstallPath.get()?.toString() ?: ""
        return current != saved
    }

    override fun apply() {
        val pathText = installPathField?.text
        if (!pathText.isNullOrBlank()) {
            HytaleInstallPath.set(File(pathText).toPath())
        } else {
            HytaleInstallPath.clear()
        }
        updateAllStatusLabels()
    }

    override fun reset() {
        installPathField?.text = HytaleInstallPath.get()?.toString() ?: ""
        updateAllStatusLabels()
    }
}
