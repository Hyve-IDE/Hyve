// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui

import com.intellij.lang.Language

/**
 * Language definition for Hytale .ui files.
 *
 * This is a simple language that doesn't extend XML since .ui files
 * use a custom format, not XML.
 */
class HyveUILanguage private constructor() : Language("HytaleUI") {
    companion object {
        @JvmField
        val INSTANCE = HyveUILanguage()
    }

    override fun getDisplayName(): String = "Hytale UI"

    override fun isCaseSensitive(): Boolean = true
}
