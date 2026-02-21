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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.ui.composer.model.*
import org.jetbrains.jewel.ui.component.Text

/**
 * Add Style popover dialog (spec 06 FR-3).
 *
 * Two fields: Style Type (dropdown), Name (@ prefix, required).
 * On confirm, creates both a [WordBankItem] and a [StyleTab] populated
 * with empty slots for the 4 standard states.
 *
 * @param onConfirm Called with the new word bank item and style tab
 * @param onDismiss Called when the popover should close without submitting
 */
@Composable
fun AddStylePopover(
    onConfirm: (WordBankItem, StyleTab) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HyveThemeColors.colors

    // Local form state
    var selectedStyleType by remember { mutableStateOf(StyleType.TEXT_BUTTON_STYLE) }
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // FR-3: Strip leading @, trim whitespace
    val cleanName = name.trim().removePrefix("@").trim()
    val canSubmit = cleanName.isNotEmpty()

    fun submit() {
        if (!canSubmit) return
        val fullName = "@$cleanName"
        val item = WordBankItem(
            id = "s_${System.currentTimeMillis()}",
            name = fullName,
            type = ComposerPropertyType.STYLE,
            kind = WordBankKind.STYLE,
            source = "local",
        )
        val tab = StyleTypeRegistry.createStyleTab(fullName, selectedStyleType)
        onConfirm(item, tab)
    }

    // Auto-focus name field
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    PopoverShell(title = "Add Style", onDismiss = onDismiss) {
        // Style Type dropdown
        PopoverDropdown(
            selected = selectedStyleType,
            options = StyleType.entries.toList(),
            onSelect = { selectedStyleType = it },
            displayName = { it.displayName },
        )

        // Hint line
        Text(
            text = "Determines which properties are available per state",
            color = colors.textDisabled,
            style = HyveTypography.badge.copy(fontStyle = FontStyle.Italic),
        )

        Spacer(modifier = Modifier.height(HyveSpacing.sm))

        // Name field with @ prefix
        PopoverTextField(
            value = name,
            onValueChange = { name = it },
            prefix = "@",
            placeholder = "styleName",
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

        Spacer(modifier = Modifier.height(HyveSpacing.lg))

        // Actions (blue/info button)
        PopoverActions(
            confirmText = "Create Style",
            confirmColor = colors.info,
            confirmTextColor = colors.textPrimary,
            confirmEnabled = canSubmit,
            onCancel = onDismiss,
            onConfirm = ::submit,
        )
    }
}
