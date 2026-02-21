// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.wordbank

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.common.compose.components.HyveChevronDownIcon
import com.hyve.common.compose.components.HyveCloseIcon
import com.hyve.common.compose.components.HyveDiamondFilledIcon
import com.hyve.common.compose.components.HyveSquareOutlineIcon
import com.hyve.ui.composer.model.ComposerPropertyType
import com.hyve.ui.composer.model.WordBankItem
import com.hyve.ui.composer.model.WordBankKind
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text

/**
 * The Word Bank sidebar panel.
 *
 * Displays reusable values grouped by kind (variables, styles, imports,
 * localization, assets) with search filtering, collapsible sections,
 * context menus, and inline "+" add buttons on section headers.
 *
 * ## Spec Reference
 * - FR-1: Panel Layout (240dp fixed width, header/search/sections)
 * - FR-2: Section Organization
 * - FR-3: Item Display
 * - FR-4: Search / Filter
 * - FR-5: Drag Initiation (gesture detection via detectDragGestures)
 * - FR-6: Context Menu
 * - FR-7: Inline Add Buttons (on section headers)
 * - FR-8: Item Management
 */
@Composable
fun WordBankPanel(
    state: WordBankState,
    onDragStart: (WordBankItem) -> Unit,
    onDragEnd: () -> Unit,
    dragItem: WordBankItem?,
    onStyleEdit: (String) -> Unit,
    onAddVariable: () -> Unit,
    onAddStyle: () -> Unit,
    onAddImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val searchQuery by state.searchQuery
    val contextMenu by state.contextMenu

    // Map each WordBankKind to its add callback (null = no add support)
    val addCallbacks: Map<WordBankKind, () -> Unit> = mapOf(
        WordBankKind.VARIABLE to onAddVariable,
        WordBankKind.STYLE to onAddStyle,
        WordBankKind.IMPORT to onAddImport,
    )

    // Track the panel's root position so we can convert item-local pointer
    // coordinates into panel-local coordinates for context menu placement.
    var panelRootOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .onGloballyPositioned { panelRootOffset = it.positionInRoot() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.deepNight)
        ) {
            // Header — FR-1
            WordBankHeader()

            Divider(orientation = Orientation.Horizontal, color = colors.slate)

            // Search bar — FR-4
            WordBankSearchBar(
                query = searchQuery,
                onQueryChange = { state.setSearchQuery(it) },
                onClear = { state.clearSearch() },
            )

            // Scrollable sections — FR-2
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = HyveSpacing.smd)
            ) {
                for (kind in WordBankKind.entries) {
                    if (!state.isSectionVisible(kind)) continue

                    val sectionItems = state.itemsForKind(kind)
                    val isCollapsed = state.isSectionCollapsed(kind) || sectionItems.isEmpty()

                    WordBankSection(
                        kind = kind,
                        items = sectionItems,
                        isCollapsed = isCollapsed,
                        onToggleCollapse = { state.toggleSection(kind) },
                        onAdd = addCallbacks[kind],
                        dragItem = dragItem,
                        onDragStart = onDragStart,
                        onDragEnd = onDragEnd,
                        onContextMenu = { item, positionInRoot ->
                            // Convert root-space position to panel-local position
                            val panelLocal = positionInRoot - panelRootOffset
                            state.showContextMenu(item, panelLocal)
                        },
                        onStyleEdit = onStyleEdit,
                    )
                }
            }
        }

        // Context menu overlay — FR-6
        contextMenu?.let { menu ->
            ContextMenuOverlay(
                menu = menu,
                onEditStyle = {
                    onStyleEdit(menu.item.name)
                    state.closeContextMenu()
                },
                onRemove = {
                    state.removeItem(menu.item.id)
                    state.closeContextMenu()
                },
                onDismiss = { state.closeContextMenu() },
            )
        }
    }
}

// -- Header --

@Composable
private fun WordBankHeader() {
    val colors = HyveThemeColors.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "WORD BANK",
            color = colors.textPrimary,
            style = HyveTypography.caption.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp,
            ),
        )
        Text(
            text = "drag \u2192 blank",
            color = colors.textDisabled,
            style = HyveTypography.badge.copy(fontStyle = FontStyle.Italic),
        )
    }
}

// -- Search bar --

@Composable
private fun WordBankSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = HyveThemeColors.colors
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) colors.honey else colors.slate

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.smd)
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.midnight, HyveShapes.card)
                .border(1.dp, borderColor, HyveShapes.card)
                .padding(horizontal = HyveSpacing.sm, vertical = 5.dp)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                        onClear()
                        focusManager.clearFocus()
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
            interactionSource = interactionSource,
            textStyle = HyveTypography.itemTitle.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.honey),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "Filter...",
                            color = colors.textDisabled,
                            style = HyveTypography.itemTitle,
                        )
                    }
                    innerTextField()
                }
            },
        )

        // Clear button
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = HyveSpacing.xs)
                    .clip(HyveShapes.small)
                    .clickable { onClear() }
                    .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.xxs),
                contentAlignment = Alignment.Center,
            ) {
                HyveCloseIcon(
                    color = colors.textDisabled,
                    modifier = Modifier.size(8.dp),
                )
            }
        }
    }
}

// -- Section --

@Composable
private fun WordBankSection(
    kind: WordBankKind,
    items: List<WordBankItem>,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onAdd: (() -> Unit)?,
    dragItem: WordBankItem?,
    onDragStart: (WordBankItem) -> Unit,
    onDragEnd: () -> Unit,
    onContextMenu: (WordBankItem, Offset) -> Unit,
    onStyleEdit: (String) -> Unit,
) {
    val colors = HyveThemeColors.colors

    Column(modifier = Modifier.padding(bottom = HyveSpacing.xs)) {
        // Section header — FR-2
        SectionHeader(
            kind = kind,
            isCollapsed = isCollapsed,
            onClick = onToggleCollapse,
            onAdd = onAdd,
        )

        // Section items (conditional on expanded)
        if (!isCollapsed && items.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = HyveSpacing.sm)) {
                for (item in items) {
                    WordBankItemRow(
                        item = item,
                        isDragging = dragItem?.id == item.id,
                        onDragStart = { onDragStart(item) },
                        onDragEnd = onDragEnd,
                        onContextMenu = { offset -> onContextMenu(item, offset) },
                        onStyleEdit = onStyleEdit,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    kind: WordBankKind,
    isCollapsed: Boolean,
    onClick: () -> Unit,
    onAdd: (() -> Unit)?,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val textColor = if (isHovered) colors.textPrimary else colors.textSecondary
    val sectionColor = kindColor(kind)

    val arrowRotation by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = tween(durationMillis = 150),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HyveShapes.card)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
    ) {
        // Collapse arrow
        HyveChevronDownIcon(
            color = textColor,
            modifier = Modifier.size(10.dp).rotate(arrowRotation),
        )

        // Section icon/badge
        KindIcon(kind = kind, color = sectionColor)

        // Label
        Text(
            text = kind.label.uppercase(),
            color = textColor,
            style = HyveTypography.badge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            ),
            modifier = Modifier.weight(1f),
        )

        // "+" button — visible when this section supports adding items
        if (onAdd != null) {
            val addInteraction = remember { MutableInteractionSource() }
            val addHovered by addInteraction.collectIsHoveredAsState()

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (addHovered) sectionColor.copy(alpha = HyveOpacity.medium)
                        else Color.Transparent
                    )
                    .hoverable(addInteraction)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = if (addHovered) sectionColor else colors.textDisabled,
                    style = HyveTypography.itemTitle.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

// -- Item row --

@Composable
private fun WordBankItemRow(
    item: WordBankItem,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onContextMenu: (Offset) -> Unit,
    onStyleEdit: (String) -> Unit,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val rowAlpha = if (isDragging) 0.4f else 1f
    val bgColor = if (isHovered && !isDragging) colors.textPrimary.copy(alpha = HyveOpacity.faint) else Color.Transparent

    // Track row position in root space for context menu positioning
    var rowRootOffset by remember { mutableStateOf(Offset.Zero) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .clip(HyveShapes.card)
            .background(bgColor)
            .hoverable(interactionSource)
            .onGloballyPositioned { rowRootOffset = it.positionInRoot() }
            .pointerInput(item.id) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, _ -> change.consume() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            }
            .pointerInput(item.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press &&
                            event.button == PointerButton.Secondary
                        ) {
                            val localPos = event.changes.first().position
                            // Convert to root-space by adding row's root offset
                            onContextMenu(localPos + rowRootOffset)
                        }
                    }
                }
            }
            .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.inputVPad),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.smd),
    ) {
        // Kind badge — FR-3
        Text(
            text = item.kind.badge,
            color = kindColor(item.kind),
            style = HyveTypography.itemTitle.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
        )

        // Name + preview + source — FR-3
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = colors.textPrimary,
                style = HyveTypography.caption.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Preview value
            if (item.value != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
                ) {
                    // Color swatch dot for color-type items
                    if (item.type == ComposerPropertyType.COLOR) {
                        ColorDot(hexValue = item.value)
                    }
                    Text(
                        text = item.value,
                        color = colors.textDisabled,
                        style = HyveTypography.badge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Source file
            if (item.source != null) {
                Text(
                    text = item.source,
                    color = colors.textDisabled,
                    style = HyveTypography.badge.copy(fontStyle = FontStyle.Italic),
                )
            }
        }

        // Edit button for local styles — FR-3
        if (item.kind == WordBankKind.STYLE && item.source == "local") {
            val editAlpha by animateFloatAsState(
                targetValue = if (isHovered) 1f else 0f,
                animationSpec = tween(durationMillis = 100),
            )
            Box(
                modifier = Modifier
                    .alpha(editAlpha)
                    .clip(HyveShapes.input)
                    .background(colors.info.copy(alpha = HyveOpacity.subtle))
                    .border(1.dp, colors.info.copy(alpha = HyveOpacity.medium), HyveShapes.input)
                    .clickable { onStyleEdit(item.name) }
                    .padding(horizontal = HyveSpacing.smd, vertical = 1.dp),
            ) {
                Text(
                    text = "EDIT",
                    color = colors.info,
                    style = HyveTypography.micro.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                    ),
                )
            }
        }
    }
}

// -- Color dot swatch --

@Composable
private fun ColorDot(hexValue: String) {
    val colors = HyveThemeColors.colors
    val parsedColor = remember(hexValue) { parseHexColor(hexValue) }
    if (parsedColor != null) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(HyveShapes.small)
                .background(parsedColor)
                .border(1.dp, colors.textPrimary.copy(alpha = HyveOpacity.medium), HyveShapes.small)
        )
    }
}

private fun parseHexColor(hex: String): Color? =
    com.hyve.ui.components.colorpicker.parseHexColorOrNull(hex)

// -- Context menu overlay --

@Composable
private fun ContextMenuOverlay(
    menu: ContextMenuState,
    onEditStyle: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HyveThemeColors.colors
    val density = LocalDensity.current

    // Convert pixel position to dp for offset
    val offsetX = with(density) { menu.position.x.toDp() }
    val offsetY = with(density) { menu.position.y.toDp() }

    // Full-size scrim to catch dismiss clicks — FR-6
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
    ) {
        // Menu card positioned at pointer (panel-local coordinates)
        Column(
            modifier = Modifier
                .offset(x = offsetX, y = offsetY)
                .width(IntrinsicSize.Min)
                .widthIn(min = 120.dp)
                .clip(HyveShapes.card)
                .background(colors.deepNight)
                .border(1.dp, colors.slate, HyveShapes.card)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* prevent click-through */ }
                .padding(HyveSpacing.xs)
        ) {
            // "Edit Style" — only for local styles
            if (menu.item.kind == WordBankKind.STYLE && menu.item.source == "local") {
                ContextMenuItem(
                    text = "Edit Style",
                    textColor = colors.textSecondary,
                    hoverColor = colors.textPrimary.copy(alpha = HyveOpacity.subtle),
                    onClick = onEditStyle,
                )
            }

            // "Remove" — always shown
            ContextMenuItem(
                text = "Remove",
                textColor = colors.error,
                hoverColor = colors.error.copy(alpha = HyveOpacity.light),
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun ContextMenuItem(
    text: String,
    textColor: Color,
    hoverColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HyveShapes.input)
            .background(if (isHovered) hoverColor else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.smd)
    ) {
        Text(
            text = text,
            color = if (isHovered && textColor != HyveThemeColors.colors.error) {
                HyveThemeColors.colors.textPrimary
            } else {
                textColor
            },
            style = HyveTypography.caption,
        )
    }
}

// -- Kind color mapping --

@Composable
private fun kindColor(kind: WordBankKind): Color {
    val colors = HyveThemeColors.colors
    return when (kind) {
        WordBankKind.VARIABLE -> colors.kindVariable
        WordBankKind.STYLE -> colors.kindStyle
        WordBankKind.IMPORT -> colors.kindImport
        WordBankKind.LOCALIZATION -> colors.kindLocalization
        WordBankKind.ASSET -> colors.kindAsset
    }
}

@Composable
private fun KindIcon(kind: WordBankKind, color: Color) {
    when (kind) {
        WordBankKind.VARIABLE -> Text(
            text = "@",
            color = color,
            style = HyveTypography.caption.copy(fontWeight = FontWeight.Bold),
        )
        WordBankKind.STYLE -> HyveDiamondFilledIcon(color = color, modifier = Modifier.size(10.dp))
        WordBankKind.IMPORT -> Text(
            text = "$",
            color = color,
            style = HyveTypography.caption.copy(fontWeight = FontWeight.Bold),
        )
        WordBankKind.LOCALIZATION -> Text(
            text = "%",
            color = color,
            style = HyveTypography.caption.copy(fontWeight = FontWeight.Bold),
        )
        WordBankKind.ASSET -> HyveSquareOutlineIcon(color = color, modifier = Modifier.size(10.dp))
    }
}
