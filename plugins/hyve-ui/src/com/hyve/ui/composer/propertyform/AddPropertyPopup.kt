// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.propertyform

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.ui.composer.model.PropertySlot
import org.jetbrains.jewel.ui.component.Text

/**
 * Popup listing unset (hidden) properties for a category.
 *
 * Each item shows a type-colored dot and the property name.
 * Clicking an item calls [onSelect] with the slot name and dismisses the popup.
 * A visible scrollbar appears when the list exceeds the max height.
 *
 * @param unsetSlots The unset slots to display
 * @param onSelect Called with the slot name when a property is picked
 * @param onDismissRequest Called when the popup should close
 */
@Composable
fun AddPropertyPopup(
    unsetSlots: List<PropertySlot>,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val colors = HyveThemeColors.colors

    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .width(180.dp)
                .heightIn(max = 200.dp)
                .clip(HyveShapes.dialog)
                .background(colors.deepNight)
                .border(1.dp, colors.slate, HyveShapes.dialog)
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = HyveSpacing.xs),
            ) {
                for (slot in unsetSlots) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hoverable(interactionSource)
                            .clickable { onSelect(slot.name) }
                            .background(
                                if (isHovered) colors.textPrimary.copy(alpha = HyveOpacity.faint)
                                else Color.Transparent
                            )
                            .padding(horizontal = HyveSpacing.sm, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.smd),
                    ) {
                        // Type dot
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(typeColor(slot.type, colors))
                        )

                        // Property name
                        Text(
                            text = slot.name,
                            color = colors.textPrimary,
                            style = HyveTypography.caption,
                        )
                    }
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = HyveSpacing.xxs, top = HyveSpacing.xs, bottom = HyveSpacing.xs),
            )
        }
    }
}
