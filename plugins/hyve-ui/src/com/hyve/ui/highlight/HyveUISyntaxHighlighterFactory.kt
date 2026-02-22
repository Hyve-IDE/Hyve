// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.highlight

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Factory that provides [HyveUISyntaxHighlighter] for .ui files.
 */
class HyveUISyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        return HyveUISyntaxHighlighter()
    }
}
