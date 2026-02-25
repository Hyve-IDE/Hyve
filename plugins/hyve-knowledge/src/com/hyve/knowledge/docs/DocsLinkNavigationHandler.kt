// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.docs

import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.ide.browsers.UrlOpener
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.net.URI

/**
 * Intercepts link clicks from the markdown JCEF preview.
 *
 * When the user clicks a link in a rendered markdown doc, IntelliJ's
 * [UrlOpener] EP is invoked before the browser is launched. We check if
 * the URL points into the offline docs cache directory, and if so, open
 * it as an editor tab instead of launching an external browser.
 */
class DocsLinkNavigationHandler : UrlOpener() {

    private val log = Logger.getInstance(DocsLinkNavigationHandler::class.java)

    override fun openUrl(browser: WebBrowser, url: String, project: Project?): Boolean {
        try {
            val uri = URI(url)

            // Only handle file:// URLs
            if (uri.scheme != "file") return false

            val filePath = File(uri).canonicalFile
            val cacheDir = KnowledgeSettings.getInstance().resolvedOfflineDocsPath().canonicalFile

            // Only intercept links that point into our offline docs cache
            if (!filePath.path.startsWith(cacheDir.path)) return false

            // Resolve the target file, handling common link patterns
            val resolved = resolveDocFile(filePath) ?: return false

            log.info("Intercepted docs link: $url → ${resolved.path}")

            // Open in editor — use provided project or fall back to any open project
            val targetProject = project
                ?: com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
                ?: return false

            ApplicationManager.getApplication().invokeLater {
                val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(resolved) ?: return@invokeLater
                val editors = FileEditorManager.getInstance(targetProject).openFile(vf, true)
                for (editor in editors) {
                    if (editor is TextEditorWithPreview) {
                        editor.setLayout(TextEditorWithPreview.Layout.SHOW_PREVIEW)
                        break
                    }
                }
            }
            return true
        } catch (e: Exception) {
            log.debug("Failed to handle docs link: $url", e)
            return false
        }
    }

    /**
     * Resolves a file path to an actual doc file on disk.
     *
     * Handles:
     * - Direct `.md` files
     * - `.mdx` → `.md` conversion (offline cache stores as `.md`)
     * - Links without extension (some inter-doc links omit `.md`)
     * - Directory links → `index.md` inside that directory
     */
    private fun resolveDocFile(file: File): File? {
        // Direct match
        if (file.isFile) return file

        // .mdx → .md
        if (file.path.endsWith(".mdx")) {
            val mdFile = File(file.path.removeSuffix(".mdx") + ".md")
            if (mdFile.isFile) return mdFile
        }

        // No extension — try appending .md
        if (!file.name.contains('.')) {
            val withMd = File(file.path + ".md")
            if (withMd.isFile) return withMd

            // Directory link → index.md
            if (file.isDirectory) {
                val indexMd = File(file, "index.md")
                if (indexMd.isFile) return indexMd
            }
        }

        return null
    }
}
