// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.popover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.ui.composer.model.ImportableFile
import com.hyve.ui.composer.model.WordBankItem
import com.hyve.ui.composer.model.WordBankKind
import org.jetbrains.jewel.ui.component.Text

/**
 * Add Import popover dialog — two-step picker (spec 06 FR-4).
 *
 * **Step 1**: Shows a list of discovered `.ui` files. Clicking a file
 * advances to step 2.
 *
 * **Step 2**: Shows the selected file's exports as a checklist. The user
 * toggles individual exports. On confirm, one [WordBankItem] is created
 * per selected export.
 *
 * @param importableFiles Pre-discovered list of importable `.ui` files
 * @param onConfirm Called with the list of new [WordBankItem]s on submit
 * @param onDismiss Called when the popover should close without submitting
 */
@Composable
fun AddImportPopover(
    importableFiles: List<ImportableFile>,
    onConfirm: (List<WordBankItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HyveThemeColors.colors

    // Two-step navigation state
    var selectedFile by remember { mutableStateOf<ImportableFile?>(null) }
    var selectedExports by remember { mutableStateOf<Set<String>>(emptySet()) }

    val selectionCount = selectedExports.size
    val canSubmit = selectionCount > 0

    fun submit() {
        if (!canSubmit) return
        val file = selectedFile ?: return
        val timestamp = System.currentTimeMillis()
        val items = file.exports
            .filter { it.name in selectedExports }
            .mapIndexed { index, export ->
                WordBankItem(
                    id = "i_${timestamp}_${index}_${export.name}",
                    name = "\$${file.name}.@${export.name}",
                    type = export.type,
                    kind = WordBankKind.IMPORT,
                    source = file.fileName,
                )
            }
        onConfirm(items)
    }

    val title = if (selectedFile == null) "Import from File" else "Select Exports"

    PopoverShell(title = title, onDismiss = onDismiss) {
        if (selectedFile == null) {
            // Step 1: File list
            if (importableFiles.isEmpty()) {
                Text(
                    text = "No .ui files found",
                    color = colors.textDisabled,
                    style = HyveTypography.caption.copy(fontStyle = FontStyle.Italic),
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
                ) {
                    for (file in importableFiles) {
                        ImportFileItem(
                            file = file,
                            onClick = {
                                selectedFile = file
                                selectedExports = emptySet()
                            },
                        )
                    }
                }
            }
        } else {
            // Step 2: Export checklist
            PopoverBackButton(
                onClick = {
                    selectedFile = null
                    selectedExports = emptySet()
                },
            )

            Spacer(modifier = Modifier.height(HyveSpacing.xs))

            // Selected file name
            Text(
                text = selectedFile!!.fileName,
                color = colors.textPrimary,
                style = HyveTypography.itemTitle.copy(fontFamily = FontFamily.Monospace),
            )

            Spacer(modifier = Modifier.height(HyveSpacing.sm))

            // Export checklist
            Column(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                for (export in selectedFile!!.exports) {
                    ExportCheckboxRow(
                        export = export,
                        isChecked = export.name in selectedExports,
                        onToggle = {
                            selectedExports = if (export.name in selectedExports) {
                                selectedExports - export.name
                            } else {
                                selectedExports + export.name
                            }
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(HyveSpacing.lg))

        // Actions
        PopoverActions(
            confirmText = if (selectionCount > 0) "Import ($selectionCount)" else "Import",
            confirmColor = colors.honey,
            confirmTextColor = colors.deepNight,
            confirmEnabled = canSubmit,
            onCancel = onDismiss,
            onConfirm = ::submit,
        )
    }
}
