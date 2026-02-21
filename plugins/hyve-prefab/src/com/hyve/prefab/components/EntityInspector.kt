@file:OptIn(ExperimentalFoundationApi::class)

package com.hyve.prefab.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.components.SectionHeader
import com.hyve.prefab.domain.ComponentTypeKey
import com.hyve.prefab.domain.EntityId
import com.hyve.prefab.domain.PrefabEntity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.serialization.json.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip

/**
 * Field name label with tooltip showing the full name on hover.
 */
@Composable
private fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    val colors = HyveThemeColors.colors

    Tooltip(tooltip = { Text(text) }, modifier = modifier) {
        Text(
            text = text,
            style = TextStyle(
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = fontWeight,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Right panel showing the selected entity's components and editable fields.
 */
@Composable
fun EntityInspector(
    entity: PrefabEntity,
    onFieldChanged: (EntityId, ComponentTypeKey, String, JsonElement, JsonElement) -> Unit,
    onJumpToSource: ((EntityId) -> Unit)? = null,
    scrollState: ScrollState,
    componentCollapsedStates: MutableMap<String, Boolean>,
    emptyComponentsExpanded: Boolean,
    onEmptyComponentsExpandedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val focusManager = LocalFocusManager.current
    val bgInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = bgInteraction,
                indication = null,
            ) { focusManager.clearFocus() }
            .verticalScroll(scrollState)
            .padding(8.dp),
    ) {
        // Entity type header — double-click to jump to JSON source
        Text(
            text = entity.displayName,
            style = TextStyle(
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.combinedClickable(
                onClick = {},
                onDoubleClick = { onJumpToSource?.invoke(entity.id) },
            ),
        )

        // Position summary
        val position = entity.position
        if (position != null) {
            Text(
                text = "Position: $position",
                style = TextStyle(
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                ),
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }

        // Partition components into non-empty and empty
        val nonEmpty = entity.components.filter { it.value.isNotEmpty() }
        val empty = entity.components.filter { it.value.isEmpty() }

        // Non-empty component sections
        for ((key, value) in nonEmpty) {
            val isExpanded = !(componentCollapsedStates[key.value] ?: false)
            ComponentSection(
                entity = entity,
                componentKey = key,
                componentData = value,
                onFieldChanged = onFieldChanged,
                isExpanded = isExpanded,
                onToggleExpanded = {
                    componentCollapsedStates[key.value] = isExpanded
                },
            )
            Spacer(Modifier.height(4.dp))
        }

        // Collapse empty components into a single expandable summary
        if (empty.isNotEmpty()) {
            SectionHeader(
                title = "${empty.size} empty component${if (empty.size != 1) "s" else ""}",
                accentColor = colors.textDisabled,
                isExpanded = emptyComponentsExpanded,
                onToggle = { onEmptyComponentsExpandedChanged(!emptyComponentsExpanded) },
                count = empty.size,
            )
            if (emptyComponentsExpanded) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                    for ((key, _) in empty) {
                        Text(
                            text = key.value,
                            style = TextStyle(
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                            ),
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ComponentSection(
    entity: PrefabEntity,
    componentKey: ComponentTypeKey,
    componentData: JsonObject,
    onFieldChanged: (EntityId, ComponentTypeKey, String, JsonElement, JsonElement) -> Unit,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val colors = HyveThemeColors.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, colors.slateLight, RoundedCornerShape(4.dp))
            .background(JewelTheme.globalColors.panelBackground),
    ) {
        // Section header — uniform neutral accent for all components
        SectionHeader(
            title = componentKey.value,
            accentColor = colors.slateLight,
            isExpanded = isExpanded,
            onToggle = onToggleExpanded,
            count = componentData.size,
        )

        // Fields
        if (isExpanded) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                for ((fieldName, fieldValue) in componentData) {
                    JsonFieldEditor(
                        entity = entity,
                        componentKey = componentKey,
                        fieldPath = fieldName,
                        fieldName = fieldName,
                        value = fieldValue,
                        onFieldChanged = onFieldChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonFieldEditor(
    entity: PrefabEntity,
    componentKey: ComponentTypeKey,
    fieldPath: String,
    fieldName: String,
    value: JsonElement,
    onFieldChanged: (EntityId, ComponentTypeKey, String, JsonElement, JsonElement) -> Unit,
) {
    val colors = HyveThemeColors.colors

    when (value) {
        is JsonObject -> {
            // Nested object: render sub-fields with indentation
            if (value.isEmpty()) {
                // Empty object: show label with "{empty}" indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldLabel(text = fieldName, modifier = Modifier.width(100.dp))
                    Text(
                        text = "{empty}",
                        style = TextStyle(
                            color = colors.textDisabled,
                            fontSize = 11.sp,
                        ),
                    )
                }
            } else {
                FieldLabel(
                    text = fieldName,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    for ((subKey, subVal) in value) {
                        JsonFieldEditor(
                            entity = entity,
                            componentKey = componentKey,
                            fieldPath = "$fieldPath.$subKey",
                            fieldName = subKey,
                            value = subVal,
                            onFieldChanged = onFieldChanged,
                        )
                    }
                }
            }
        }
        is JsonArray -> {
            // Arrays shown as read-only text for MVP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FieldLabel(text = fieldName, modifier = Modifier.width(100.dp))
                Text(
                    text = "[${value.size} items]",
                    style = TextStyle(
                        color = colors.textDisabled,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
        is JsonPrimitive -> {
            PrimitiveFieldEditor(
                entity = entity,
                componentKey = componentKey,
                fieldPath = fieldPath,
                fieldName = fieldName,
                primitive = value,
                onFieldChanged = onFieldChanged,
            )
        }
        is JsonNull -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FieldLabel(text = fieldName, modifier = Modifier.width(100.dp))
                Text(
                    text = "null",
                    style = TextStyle(
                        color = colors.textDisabled,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun PrimitiveFieldEditor(
    entity: PrefabEntity,
    componentKey: ComponentTypeKey,
    fieldPath: String,
    fieldName: String,
    primitive: JsonPrimitive,
    onFieldChanged: (EntityId, ComponentTypeKey, String, JsonElement, JsonElement) -> Unit,
) {
    val colors = HyveThemeColors.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldLabel(text = fieldName, modifier = Modifier.width(100.dp))

        when {
            primitive.booleanOrNull != null -> {
                val checked = primitive.boolean
                Text(
                    text = if (checked) "\u2611" else "\u2610",
                    style = TextStyle(
                        color = if (checked) colors.honey else colors.textSecondary,
                        fontSize = 14.sp,
                    ),
                    modifier = Modifier.clickable {
                        onFieldChanged(
                            entity.id,
                            componentKey,
                            fieldPath,
                            primitive,
                            JsonPrimitive(!checked),
                        )
                    },
                )
            }
            else -> {
                // String or number: text field using Jewel TextFieldState
                val textState = rememberTextFieldState(primitive.content)

                // Sync text and collect changes in a single effect keyed on entity/field identity.
                // This ensures the snapshotFlow always captures a fresh `primitive` reference,
                // preventing phantom onFieldChanged calls when entity selection changes.
                LaunchedEffect(entity.id, fieldPath, primitive) {
                    val currentText = textState.text.toString()
                    if (currentText != primitive.content) {
                        textState.setTextAndPlaceCursorAtEnd(primitive.content)
                    }

                    snapshotFlow { textState.text.toString() }
                        .debounce(300)
                        .collect { newText ->
                            if (newText != primitive.content) {
                                val newElement = parseJsonPrimitive(newText, primitive)
                                onFieldChanged(
                                    entity.id,
                                    componentKey,
                                    fieldPath,
                                    primitive,
                                    newElement,
                                )
                            }
                        }
                }

                TextField(
                    state = textState,
                    modifier = Modifier.weight(1f).height(28.dp),
                )
            }
        }
    }
}

/**
 * Parse a text value back to a JsonPrimitive, preserving the original type.
 */
private fun parseJsonPrimitive(text: String, original: JsonPrimitive): JsonPrimitive {
    // Try to preserve original type
    if (original.intOrNull != null) {
        text.toIntOrNull()?.let { return JsonPrimitive(it) }
    }
    if (original.longOrNull != null) {
        text.toLongOrNull()?.let { return JsonPrimitive(it) }
    }
    if (original.doubleOrNull != null) {
        text.toDoubleOrNull()?.let { return JsonPrimitive(it) }
    }
    if (original.floatOrNull != null) {
        text.toFloatOrNull()?.let { return JsonPrimitive(it) }
    }
    return JsonPrimitive(text)
}
