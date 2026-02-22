// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.highlight

import com.intellij.lang.Commenter

/**
 * Commenter for Hytale .ui files.
 * Enables Ctrl+/ (line comment) and Ctrl+Shift+/ (block comment).
 */
class HyveUICommenter : Commenter {
    override fun getLineCommentPrefix(): String = "//"
    override fun getBlockCommentPrefix(): String = "/*"
    override fun getBlockCommentSuffix(): String = "*/"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
