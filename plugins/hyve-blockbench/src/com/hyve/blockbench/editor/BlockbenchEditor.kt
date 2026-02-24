package com.hyve.blockbench.editor

import com.hyve.blockbench.bridge.BlockbenchBridge
import com.hyve.blockbench.download.BlockbenchBundle
import com.hyve.blockbench.download.BlockbenchDownloadTask
import com.hyve.blockbench.download.BlockbenchServer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.util.messages.MessageBusConnection
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.*

class BlockbenchEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private var browser: JBCefBrowser? = null
    private var bridge: BlockbenchBridge? = null
    private var modified = false
    private val pcs = PropertyChangeSupport(this)
    private var busConnection: MessageBusConnection? = null

    // Wrapper panel that hosts either the JCEF browser or the fallback UI.
    // JCEF browser creation is deferred via invokeLater to avoid triggering
    // JBCefApp$Holder.<clinit> during constructor execution, which would
    // violate the platform rule that class init must not depend on services.
    private val wrapper = JPanel(BorderLayout())

    init {
        // Defer everything to invokeLater — even JBCefApp.isSupported() triggers
        // JBCefApp$Holder.<clinit> which performs service lookups that the platform
        // forbids during class/constructor initialization.
        ApplicationManager.getApplication().invokeLater {
            if (!file.isValid) return@invokeLater
            if (BlockbenchBundle.isAvailable() && JBCefApp.isSupported()) {
                try {
                    initBrowser()
                } catch (e: Exception) {
                    wrapper.add(createFallbackPanel(), BorderLayout.CENTER)
                    wrapper.revalidate()
                }
            } else {
                wrapper.add(createFallbackPanel(), BorderLayout.CENTER)
                wrapper.revalidate()
            }
        }
    }

    private fun initBrowser() {
        val jcefBrowser = JBCefBrowserBuilder()
            .setOffScreenRendering(false)
            .build()
        browser = jcefBrowser
        // Set initial 60fps default; the bridge will sync to Blockbench's FPS Limit setting once loaded.
        // CefBrowserWr (remote mode) requires a post-creation call to push the rate to the remote CEF process.
        jcefBrowser.cefBrowser.setWindowlessFrameRate(60)
        // Serve via local HTTP to avoid CORS issues with file:// and ES modules
        val port = BlockbenchServer.ensureRunning()
        val baseUrl = "http://127.0.0.1:$port"
        bridge = BlockbenchBridge(jcefBrowser, file, project, baseUrl) { mod ->
            val old = modified
            modified = mod
            pcs.firePropertyChange("modified", old, mod)
        }
        jcefBrowser.loadURL("$baseUrl/index.html")
        wrapper.add(jcefBrowser.component, BorderLayout.CENTER)
        wrapper.revalidate()
        wrapper.repaint()

        // IntelliJ intercepts Ctrl+S before it reaches JCEF, so listen for IDE-level
        // save events and forward them to the Blockbench bridge.
        val conn = ApplicationManager.getApplication().messageBus.connect()
        busConnection = conn
        conn.subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
            override fun beforeAllDocumentsSaving() {
                if (modified) {
                    bridge?.save()
                }
            }
        })
    }

    private fun createFallbackPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        if (!JBCefApp.isSupported()) {
            val label = JLabel("JCEF (embedded browser) is not available in this IDE build.")
            label.alignmentX = JLabel.CENTER_ALIGNMENT
            panel.add(Box.createVerticalGlue())
            panel.add(label)
            panel.add(Box.createVerticalGlue())
        } else {
            val label = JLabel("Blockbench web editor is not installed yet.")
            label.alignmentX = JLabel.CENTER_ALIGNMENT

            val button = JButton("Download Blockbench Web Editor")
            button.alignmentX = JButton.CENTER_ALIGNMENT
            button.addActionListener {
                button.isEnabled = false
                button.text = "Downloading..."
                BlockbenchDownloadTask(project) {
                    SwingUtilities.invokeLater {
                        button.text = "Downloaded! Reopen this file."
                    }
                }.queue()
            }

            panel.add(Box.createVerticalGlue())
            panel.add(label)
            panel.add(Box.createRigidArea(java.awt.Dimension(0, 12)))
            panel.add(button)
            panel.add(Box.createVerticalGlue())
        }

        return panel
    }

    override fun getComponent(): JComponent = wrapper

    override fun getPreferredFocusedComponent(): JComponent? = browser?.component ?: wrapper

    override fun getName(): String = "Blockbench"

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = modified

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        pcs.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        pcs.removePropertyChangeListener(listener)
    }

    override fun dispose() {
        busConnection?.disconnect()
        bridge?.dispose()
        browser?.dispose()
    }
}
