// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.highlight

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/**
 * Brace matching for Hytale .ui files.
 * Highlights matching pairs of braces, brackets, and parentheses.
 */
class HyveUIBraceMatcher : PairedBraceMatcher {

    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
            BracePair(HyveUIElementTypes.LEFT_BRACE, HyveUIElementTypes.RIGHT_BRACE, true),
            BracePair(HyveUIElementTypes.LEFT_BRACKET, HyveUIElementTypes.RIGHT_BRACKET, false),
            BracePair(HyveUIElementTypes.LEFT_PAREN, HyveUIElementTypes.RIGHT_PAREN, false),
        )
    }
}
