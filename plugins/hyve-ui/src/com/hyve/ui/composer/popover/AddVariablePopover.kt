// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.popover

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.ui.composer.model.*

/**
 * Add Variable popover dialog (spec 06 FR-2).
 *
 * Three fields: Name (@ prefix, required), Type (dropdown), Default Value (optional).
 * On confirm, creates a [WordBankItem] with `kind = VARIABLE`.
 *
 * @param onConfirm Called with the new [WordBankItem] when the user submits
 * @param onDismiss Called when the popover should close without submitting
 */
@Composable
fun AddVariablePopover(
    onConfirm: (WordBankItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HyveThemeColors.colors

    // Local form state
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ComposerPropertyType.TEXT) }
    var defaultValue by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // FR-2: Strip leading @, trim whitespace
    val cleanName = name.trim().removePrefix("@").trim()
    val canSubmit = cleanName.isNotEmpty()

    fun submit() {
        if (!canSubmit) return
        val item = WordBankItem(
            id = "v_${System.currentTimeMillis()}",
            name = "@$cleanName",
            type = selectedType,
            kind = WordBankKind.VARIABLE,
            value = defaultValue.ifBlank { null },
        )
        onConfirm(item)
    }

    // Auto-focus name field
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    PopoverShell(title = "Add Variable", onDismiss = onDismiss) {
        // Name field with @ prefix
        PopoverTextField(
            value = name,
            onValueChange = { name = it },
            prefix = "@",
            placeholder = "variableName",
            focusRequester = focusRequester,
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    submit()
                    true
                } else {
                    false
                }
            },
        )

        Spacer(modifier = Modifier.height(HyveSpacing.sm))

        // Type dropdown
        PopoverDropdown(
            selected = selectedType,
            options = VARIABLE_TYPE_OPTIONS,
            onSelect = { selectedType = it },
            displayName = { it.displayName },
        )

        Spacer(modifier = Modifier.height(HyveSpacing.sm))

        // Default value field (placeholder adapts to selected type)
        PopoverTextField(
            value = defaultValue,
            onValueChange = { defaultValue = it },
            placeholder = defaultPlaceholder(selectedType),
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    submit()
                    true
                } else {
                    false
                }
            },
        )

        Spacer(modifier = Modifier.height(HyveSpacing.lg))

        // Actions
        PopoverActions(
            confirmText = "Add Variable",
            confirmColor = colors.honey,
            confirmTextColor = colors.deepNight,
            confirmEnabled = canSubmit,
            onCancel = onDismiss,
            onConfirm = ::submit,
        )
    }
}
