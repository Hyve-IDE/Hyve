// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.actions

import com.hyve.knowledge.bridge.toConfig
import com.hyve.knowledge.core.config.KnowledgeConfig
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Paths
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * IDE action that configures the standalone MCP server for external LLM clients.
 * Writes current IDE settings to ~/.hyve/knowledge/mcp-config.json and
 * registers the MCP server in selected client config files.
 */
class ConfigureMcpAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val jarPath = findMcpServerJar()

        // Write current IDE settings to mcp-config.json
        val config = KnowledgeSettings.getInstance().toConfig()
        KnowledgeConfig.writeToFile(config)

        // Show dialog to select LLM clients
        val dialog = ClientSelectionDialog()
        if (!dialog.showAndGet()) return

        val selectedClients = dialog.selectedClients
        if (selectedClients.isEmpty()) {
            Messages.showInfoMessage(
                "No clients selected. MCP config file was written to:\n${KnowledgeConfig.configFilePath().absolutePath}",
                "Hyve Knowledge MCP",
            )
            return
        }

        val results = mutableListOf<String>()
        for (client in selectedClients) {
            try {
                writeClientConfig(client, jarPath)
                results.add("${client.displayName}: configured")
            } catch (ex: Exception) {
                results.add("${client.displayName}: FAILED — ${ex.message}")
            }
        }

        Messages.showInfoMessage(
            "MCP server configured:\n\n${results.joinToString("\n")}\n\n" +
                "Config: ${KnowledgeConfig.configFilePath().absolutePath}\n" +
                "JAR: $jarPath",
            "Hyve Knowledge MCP",
        )
    }

    private fun findMcpServerJar(): String {
        val jarName = "hyve-knowledge-mcp.jar"
        val pluginsDir = com.intellij.openapi.application.PathManager.getPluginsPath()
        val ideHome = com.intellij.openapi.application.PathManager.getHomePath()

        // 1. Installed plugin: look in the plugins directory
        for (pluginDirName in listOf("hyve-plugin", "Hyve Toolkit", "com.hyve")) {
            val candidate = File(pluginsDir, "$pluginDirName/lib/$jarName")
            if (candidate.exists()) return candidate.absolutePath
        }

        // 2. Dev mode: check hyve-plugin build output relative to IDE home
        val devCandidates = listOf(
            "hyve-plugin/mcp-server/build/libs/$jarName",
            "hyve-plugin/build/idea-sandbox/plugins/hyve-plugin/lib/$jarName",
        )
        for (candidate in devCandidates) {
            val file = File(ideHome, candidate)
            if (file.exists()) return file.absolutePath
        }

        // 3. Fallback: best-guess path
        return File(pluginsDir, "hyve-plugin/lib/$jarName").absolutePath
    }

    private fun writeClientConfig(client: LlmClient, jarPath: String) {
        val configFile = client.configFile()
        configFile.parentFile?.mkdirs()

        val serverEntry = buildJsonObject {
            put("type", "stdio")
            put("command", "java")
            put("args", buildJsonArray {
                add("-jar")
                add(jarPath)
            })
        }

        val existing = if (configFile.exists()) {
            try {
                Json.parseToJsonElement(configFile.readText()).jsonObject
            } catch (_: Exception) {
                buildJsonObject { }
            }
        } else {
            buildJsonObject { }
        }

        val updated = when (client) {
            LlmClient.VS_CODE -> {
                // VS Code uses "servers" key
                val servers = existing["servers"]?.jsonObject ?: buildJsonObject { }
                val updatedServers = buildJsonObject {
                    for ((k, v) in servers) put(k, v)
                    put("hyve-knowledge", serverEntry)
                }
                buildJsonObject {
                    for ((k, v) in existing) {
                        if (k != "servers") put(k, v)
                    }
                    put("servers", updatedServers)
                }
            }
            else -> {
                // Claude Code, Cursor, Windsurf use "mcpServers" key
                val mcpServers = existing["mcpServers"]?.jsonObject ?: buildJsonObject { }
                val updatedMcpServers = buildJsonObject {
                    for ((k, v) in mcpServers) put(k, v)
                    put("hyve-knowledge", serverEntry)
                }
                buildJsonObject {
                    for ((k, v) in existing) {
                        if (k != "mcpServers") put(k, v)
                    }
                    put("mcpServers", updatedMcpServers)
                }
            }
        }

        val json = Json { prettyPrint = true }
        configFile.writeText(json.encodeToString(JsonObject.serializer(), updated))
    }

    enum class LlmClient(val displayName: String) {
        CLAUDE_CODE("Claude Code"),
        CURSOR("Cursor"),
        VS_CODE("VS Code"),
        WINDSURF("Windsurf");

        fun configFile(): File {
            val home = System.getProperty("user.home")
            return when (this) {
                CLAUDE_CODE -> Paths.get(home, ".claude.json").toFile()
                CURSOR -> Paths.get(home, ".cursor", "mcp.json").toFile()
                VS_CODE -> Paths.get(home, ".vscode", "mcp.json").toFile()
                WINDSURF -> Paths.get(home, ".codeium", "windsurf", "mcp_config.json").toFile()
            }
        }
    }

    private class ClientSelectionDialog : DialogWrapper(true) {
        private val checkBoxes = LlmClient.entries.map { client ->
            client to JBCheckBox(client.displayName, true)
        }

        val selectedClients: List<LlmClient>
            get() = checkBoxes.filter { it.second.isSelected }.map { it.first }

        init {
            title = "Configure Hytale Knowledge MCP"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.add(JLabel("Select LLM clients to configure:"))
            panel.add(javax.swing.Box.createVerticalStrut(8))
            for ((_, checkBox) in checkBoxes) {
                panel.add(checkBox)
            }
            return panel
        }
    }
}
