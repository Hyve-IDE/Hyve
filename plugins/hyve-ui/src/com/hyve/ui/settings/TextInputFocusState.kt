// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.settings

import androidx.compose.runtime.mutableStateOf

/**
 * Global state tracking whether a text input field currently has focus.
 * When true, single-key hotkeys (like G, B) should be suppressed to allow typing.
 * Modifier hotkeys (Ctrl+S, etc.) still work during text input.
 */
object TextInputFocusState {
    var isTextFieldFocused = mutableStateOf(false)

    fun setFocused(focused: Boolean) {
        isTextFieldFocused.value = focused
    }
}
