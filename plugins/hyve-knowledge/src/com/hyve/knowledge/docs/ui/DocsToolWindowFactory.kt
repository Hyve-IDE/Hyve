// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.docs.ui

import androidx.compose.runtime.Composable
import com.hyve.common.compose.SimpleHyveToolWindowFactory
import com.hyve.knowledge.docs.SyncDocsAction
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

/**
 * Tool window factory for the Hytale Documentation offline browser.
 *
 * Provides a navigation tree + search panel in a right-anchored tool window.
 * Registered in hyve-knowledge.xml.
 */
class DocsToolWindowFactory : SimpleHyveToolWindowFactory() {

    override val tabDisplayName: String? = null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setAdditionalGearActions(createGearActions(project))
        super.createToolWindowContent(project, toolWindow)
    }

    @Composable
    override fun Content(project: Project) {
        DocsNavigationPanel(project)
    }

    private fun createGearActions(project: Project) = DefaultActionGroup().apply {
        add(SyncDocsAction())
        add(OpenOnGitHubAction(project))
        addSeparator()
        add(LanguageActionGroup())
    }
}

/**
 * Opens the currently active docs file on GitHub, or the repo root if
 * no docs file is open.
 */
private class OpenOnGitHubAction(private val project: Project) : DumbAwareAction("Open on GitHub") {

    override fun actionPerformed(e: AnActionEvent) {
        val settings = KnowledgeSettings.getInstance()
        val state = settings.state
        val docsCachePath = settings.resolvedOfflineDocsPath().absolutePath.replace('\\', '/')

        // Check if the active editor has a docs file open
        val activeFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val filePath = activeFile?.path?.replace('\\', '/')

        if (filePath != null && filePath.startsWith(docsCachePath)) {
            val relativeToDocs = filePath.removePrefix(docsCachePath).trimStart('/')
            val pathParts = relativeToDocs.split("/", limit = 2)
            if (pathParts.size == 2) {
                val locale = pathParts[0]
                val docPath = pathParts[1].removeSuffix(".md") + ".mdx"
                val url = "https://github.com/${state.docsGithubRepo}/blob/${state.docsGithubBranch}/content/docs/$locale/$docPath"
                BrowserUtil.browse(url)
                return
            }
        }

        // Fallback: open the repo root
        BrowserUtil.browse("https://github.com/${state.docsGithubRepo}")
    }
}

/**
 * Submenu in the gear menu that lists all available documentation languages.
 * The current locale shows a checkmark; selecting another triggers recomposition
 * of the navigation panel and auto-syncs if the locale isn't cached.
 */
private class LanguageActionGroup : DefaultActionGroup("Language", true), DumbAware {
    init {
        for ((code, displayName) in LOCALE_DISPLAY_NAMES) {
            add(object : ToggleAction(displayName), DumbAware {
                override fun isSelected(e: AnActionEvent): Boolean =
                    DocsLocaleHolder.locale == code

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    if (state && DocsLocaleHolder.locale != code) {
                        DocsLocaleHolder.locale = code
                        KnowledgeSettings.getInstance().state.docsLanguage = code
                    }
                }
            })
        }
    }
}
