package com.hyve.ui.components.properties

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.hyve.ui.settings.TextInputFocusState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveTypography
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.services.assets.AssetLoader
import com.hyve.ui.services.items.ItemRegistry
import com.hyve.ui.canvas.CanvasState
import com.hyve.ui.canvas.ScreenshotReferencePanel
import com.hyve.ui.state.command.ReplaceElementCommand
import com.hyve.ui.schema.*
import com.hyve.ui.schema.discovery.TupleFieldInfo
import com.hyve.common.compose.HyveSpacing
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.Orientation

/**
 * Property inspector for editing element properties.
 *
 * This panel shows commonly-used properties with simple values:
 * - Layout: Anchor, Width, Height, positioning
 * - Appearance: Background, Color, Opacity
 * - Text: Text content, FontSize
 * - State: Visible toggle
 *
 * Features:
 * - Filtered property display for quick edits
 * - Add/Remove property functionality
 * - Asset Browser integration for ImagePath properties
 */
@Composable
fun SchemaPropertyInspector(
    canvasState: CanvasState,
    runtimeRegistry: RuntimeSchemaRegistry?,
    document: com.hyve.ui.core.domain.UIDocument? = null,
    assetLoader: AssetLoader? = null,
    itemRegistry: ItemRegistry? = null,
    projectRoot: java.nio.file.Path? = null,
    currentFilePath: java.nio.file.Path? = null,
    onOpenComposer: ((UIElement) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedElements = canvasState.selectedElements.value
    val scrollState = rememberScrollState()
    val currentElement = selectedElements.firstOrNull()

    // Collect all element IDs for validation
    val allElementIds = remember(canvasState.rootElement.value) {
        val ids = mutableSetOf<String>()
        canvasState.rootElement.value?.visitDescendants { element ->
            element.id?.let { ids.add(it.value) }
        }
        ids
    }

    // Build tuple field lookup from the runtime registry
    val tupleFieldLookup = remember(runtimeRegistry) {
        val map = mutableMapOf<String, List<TupleFieldInfo>>()
        runtimeRegistry?.getAllElementTypes()?.forEach { type ->
            runtimeRegistry.getPropertiesForElement(type).forEach { prop ->
                if (prop.tupleFields.isNotEmpty() && prop.name !in map) {
                    map[prop.name] = prop.tupleFields
                }
            }
        }
        map
    }

    // Provide AssetLoader and ItemRegistry to property editors
    CompositionLocalProvider(
        LocalAssetLoader provides assetLoader,
        LocalItemRegistry provides itemRegistry
    ) {
        Box(
            modifier = modifier
                .background(JewelTheme.globalColors.panelBackground)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(HyveSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Properties",
                        color = JewelTheme.globalColors.text.normal,
                        fontWeight = FontWeight.Medium
                    )

                    // Element type badge inline in the header
                    // Track last non-null type so it persists through exit animation
                    var lastElementType by remember { mutableStateOf("") }
                    if (currentElement != null) {
                        lastElementType = currentElement.type.value
                    }

                    // Use AnimatedVisibility to prevent flash during selection transitions
                    AnimatedVisibility(
                        visible = currentElement != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    HyveThemeColors.colors.honeySubtle,
                                    HyveShapes.card
                                )
                                .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xxs)
                        ) {
                            Text(
                                text = lastElementType,
                                color = HyveThemeColors.colors.honey,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Divider(Orientation.Horizontal)

                // Scrollable content area with padding
                Column(modifier = Modifier.padding(horizontal = HyveSpacing.md)) {
                when {
                    selectedElements.isEmpty() -> {
                        EmptySelectionMessage()
                    }
                    selectedElements.size == 1 && currentElement != null -> {
                        SingleElementInspector(
                            element = currentElement,
                            canvasState = canvasState,
                            runtimeRegistry = runtimeRegistry,
                            scrollState = scrollState,
                            allElementIds = allElementIds,
                            tupleFieldLookup = tupleFieldLookup,
                            onOpenComposer = onOpenComposer
                        )
                    }
                    else -> {
                        MultipleSelectionMessage(selectedElements.size)
                    }
                }
                }
            }
        }
    }

}

@Composable
private fun EmptySelectionMessage() {
    Column {
        Text(
            text = "No element selected",
            color = JewelTheme.globalColors.text.info
        )
        Text(
            text = "Click an element on the canvas to select it and edit its properties.",
            color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = HyveSpacing.sm)
        )
    }
}

@Composable
private fun MultipleSelectionMessage(count: Int) {
    Column {
        Text(
            text = "$count elements selected",
            color = JewelTheme.globalColors.text.normal,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(HyveSpacing.sm))
        Text(
            text = "Select a single element to edit properties",
            color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ColumnScope.SingleElementInspector(
    element: UIElement,
    canvasState: CanvasState,
    runtimeRegistry: RuntimeSchemaRegistry?,
    scrollState: androidx.compose.foundation.ScrollState,
    allElementIds: Set<String>,
    tupleFieldLookup: Map<String, List<TupleFieldInfo>> = emptyMap(),
    onOpenComposer: ((UIElement) -> Unit)? = null
) {
    val elementSchema = runtimeRegistry?.getElementSchema(element.type)
    val existingPropertyNames = element.properties.entries().map { it.key.value }.toSet()

    // Element header with editable ID
    ElementHeader(
        element = element,
        canvasState = canvasState,
        allElementIds = allElementIds
    )

    Spacer(modifier = Modifier.height(HyveSpacing.md))

    // Scrollable property list
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(scrollState)
    ) {
        // Constraint editor (shown for non-root elements that have an Anchor)
        val isRoot = element === canvasState.rootElement.value
        if (!isRoot) {
            ConstraintEditorPanel(
                element = element,
                canvasState = canvasState
            )
            Spacer(modifier = Modifier.height(HyveSpacing.sm))
        }

        // ItemPreviewComponent: show item picker from metadata (not in property map)
        if (element.type.value == "ItemPreviewComponent") {
            ItemIdPropertyEditor(
                value = element.metadata.previewItemId ?: "",
                onValueChange = { newValue ->
                    canvasState.updateElementMetadata(element) { meta ->
                        meta.copy(previewItemId = newValue.ifBlank { null })
                    }
                }
            )
            Spacer(modifier = Modifier.height(HyveSpacing.sm))
        }

        if (elementSchema != null) {
            // Schema-driven display: group properties by category
            SchemaPropertyDisplay(
                element = element,
                elementSchema = elementSchema,
                existingPropertyNames = existingPropertyNames,
                canvasState = canvasState,
                tupleFieldLookup = tupleFieldLookup
            )
        } else {
            // Fallback: just show existing properties
            FallbackPropertyDisplay(
                element = element,
                canvasState = canvasState
            )
        }
    }

    // Screenshot reference controls (shown when Root is selected)
    val isRootElement = element === canvasState.rootElement.value
    if (isRootElement) {
        Spacer(modifier = Modifier.height(HyveSpacing.sm))
        ScreenshotReferencePanel(canvasState = canvasState)
    }

    // Open in Composer button (spec 10 FR-1)
    if (onOpenComposer != null && !isRootElement) {
        Spacer(modifier = Modifier.height(HyveSpacing.sm))
        OpenInComposerButton(
            enabled = elementSchema != null,
            onClick = { onOpenComposer(element) }
        )
        Spacer(modifier = Modifier.height(HyveSpacing.md))
    }
}

/**
 * Element header with editable ID field.
 *
 * Displays the element ID as an editable field.
 * When clicked, the ID becomes editable with validation for format and uniqueness.
 */
@Composable
private fun ElementHeader(
    element: UIElement,
    canvasState: CanvasState,
    allElementIds: Set<String>
) {
    var isEditingId by remember { mutableStateOf(false) }
    var idValidationError by remember { mutableStateOf<String?>(null) }
    var editText by remember(element.id) { mutableStateOf(element.id?.value ?: "") }
    val focusRequester = remember { FocusRequester() }

    // Clear global focus state when editing ends or composable is disposed
    DisposableEffect(isEditingId) {
        onDispose {
            if (isEditingId) {
                TextInputFocusState.setFocused(false)
            }
        }
    }

    val headerInteraction = remember { MutableInteractionSource() }
    val isHeaderHovered by headerInteraction.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = HyveSpacing.sm)
    ) {
            // Editable Element ID
            val hasError = idValidationError != null
            val borderColor = when {
                hasError -> JewelTheme.globalColors.text.error
                isEditingId -> JewelTheme.globalColors.outlines.focused
                isHeaderHovered -> HyveThemeColors.colors.slateLight
                else -> JewelTheme.globalColors.borders.normal
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(HyveShapes.card)
                    .border(1.dp, borderColor, HyveShapes.card)
                    .background(
                        when {
                            isEditingId -> JewelTheme.globalColors.panelBackground
                            isHeaderHovered -> HyveThemeColors.colors.textPrimary.copy(alpha = HyveOpacity.faint)
                            else -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f)
                        }
                    )
                    .then(
                        if (!isEditingId) {
                            Modifier
                                .hoverable(headerInteraction)
                                .clickable {
                                    editText = element.id?.value ?: ""
                                    idValidationError = null
                                    isEditingId = true
                                }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.smd)
            ) {
                if (isEditingId) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "#",
                                color = JewelTheme.globalColors.text.info
                            )
                            androidx.compose.foundation.text.BasicTextField(
                                value = editText,
                                onValueChange = { newValue ->
                                    editText = newValue
                                    // Clear error when typing
                                    idValidationError = null
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = JewelTheme.globalColors.text.normal
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                                    JewelTheme.globalColors.outlines.focused
                                ),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onDone = {
                                        applyIdChange(
                                            newId = editText,
                                            element = element,
                                            allElementIds = allElementIds,
                                            canvasState = canvasState,
                                            onSuccess = {
                                                isEditingId = false
                                                idValidationError = null
                                            },
                                            onError = { error ->
                                                idValidationError = error
                                            }
                                        )
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        TextInputFocusState.setFocused(focusState.isFocused)
                                    }
                                    .onKeyEvent { keyEvent ->
                                        when {
                                            keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyUp -> {
                                                editText = element.id?.value ?: ""
                                                isEditingId = false
                                                idValidationError = null
                                                true
                                            }
                                            keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp -> {
                                                applyIdChange(
                                                    newId = editText,
                                                    element = element,
                                                    allElementIds = allElementIds,
                                                    canvasState = canvasState,
                                                    onSuccess = {
                                                        isEditingId = false
                                                        idValidationError = null
                                                    },
                                                    onError = { error ->
                                                        idValidationError = error
                                                    }
                                                )
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                            )
                            // Confirm and cancel buttons
                            IconButton(
                                onClick = {
                                    applyIdChange(
                                        newId = editText,
                                        element = element,
                                        allElementIds = allElementIds,
                                        canvasState = canvasState,
                                        onSuccess = {
                                            isEditingId = false
                                            idValidationError = null
                                        },
                                        onError = { error ->
                                            idValidationError = error
                                        }
                                    )
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    key = AllIconsKeys.Actions.Checked,
                                    contentDescription = "Apply",
                                    modifier = Modifier.size(14.dp),
                                    tint = JewelTheme.globalColors.outlines.focused
                                )
                            }
                            IconButton(
                                onClick = {
                                    editText = element.id?.value ?: ""
                                    isEditingId = false
                                    idValidationError = null
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    key = AllIconsKeys.Actions.Close,
                                    contentDescription = "Cancel",
                                    modifier = Modifier.size(14.dp),
                                    tint = JewelTheme.globalColors.text.info
                                )
                            }
                        }

                        // Validation error
                        if (idValidationError != null) {
                            Spacer(modifier = Modifier.height(HyveSpacing.xs))
                            Text(
                                text = idValidationError!!,
                                color = JewelTheme.globalColors.text.error
                            )
                        }
                    }

                    // Request focus when entering edit mode
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = element.id?.let { "#${it.value}" } ?: "(no id)",
                            color = if (element.id != null)
                                JewelTheme.globalColors.text.normal
                            else
                                JewelTheme.globalColors.text.info.copy(alpha = 0.6f),
                            fontStyle = if (element.id == null) FontStyle.Italic else FontStyle.Normal
                        )
                        Icon(
                            key = AllIconsKeys.Actions.Edit,
                            contentDescription = "Edit ID",
                            modifier = Modifier.size(14.dp),
                            tint = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)
                        )
                    }
                }
        }
    }
}

/**
 * Validate and build a ReplaceElementCommand for an ID change.
 * Returns the command on success, or null if validation fails (error reported via onError).
 */
internal fun buildIdChangeCommand(
    newId: String,
    element: UIElement,
    allElementIds: Set<String>,
    onError: (String) -> Unit
): ReplaceElementCommand? {
    val normalizedId = newId.trim()

    // Empty ID is allowed (removes the ID)
    if (normalizedId.isEmpty()) {
        val updatedElement = element.copy(id = null)
        return ReplaceElementCommand.forElement(element, updatedElement)
    }

    // Validate ID format
    if (!normalizedId.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))) {
        onError("ID must start with letter/underscore, contain only letters, numbers, underscores")
        return null
    }

    // Check for duplicates (exclude current element's ID)
    val currentId = element.id?.value
    val otherIds = if (currentId != null) allElementIds - currentId else allElementIds
    if (normalizedId in otherIds) {
        onError("An element with this ID already exists")
        return null
    }

    // Build the command
    val updatedElement = element.copy(
        id = com.hyve.ui.core.id.ElementId(normalizedId)
    )
    return ReplaceElementCommand.forElement(element, updatedElement)
}

/**
 * Validate and apply a new element ID.
 */
private fun applyIdChange(
    newId: String,
    element: UIElement,
    allElementIds: Set<String>,
    canvasState: CanvasState,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val command = buildIdChangeCommand(newId, element, allElementIds, onError)
    if (command != null) {
        canvasState.executeCommand(command)
        onSuccess()
    }
}

/**
 * Properties eligible for Quick Edit display.
 * These are the most commonly adjusted properties that have simple value types.
 * All other properties require a dedicated editor.
 */
private val QUICK_EDIT_PROPERTIES = setOf(
    // Layout - basic positioning and sizing
    "Anchor",
    "Width", "Height",
    "Left", "Top", "Right", "Bottom",

    // Appearance - basic visual properties
    "Background", "Color", "BackgroundColor", "ForegroundColor",
    "Opacity", "Alpha",

    // Image / texture paths and stretch mode
    "Source", "Image", "TexturePath", "MaskTexturePath",
    "Stretch",

    // Text - basic text properties
    "Text", "FontSize",

    // State - visibility toggle
    "Visible"
)

/**
 * Check if a property value is simple enough for Quick Edit.
 * Complex values (expressions, variable refs, styles, etc.) are excluded.
 */
private fun isSimpleValue(value: PropertyValue): Boolean {
    return when (value) {
        // Simple editable types
        is PropertyValue.Text -> true
        is PropertyValue.Number -> true
        is PropertyValue.Percent -> true
        is PropertyValue.Boolean -> true
        is PropertyValue.Color -> true
        is PropertyValue.ImagePath -> true
        is PropertyValue.FontPath -> true

        // Anchor is simple only if all its dimensions are simple (not expressions)
        is PropertyValue.Anchor -> {
            val anchor = value.anchor
            // An anchor is simple if it only contains Absolute or Relative dimensions
            // (not expressions or variable refs, which would be stored differently)
            true // AnchorDimension only supports Absolute/Relative, so always simple
        }

        // Tuple is simple only if it represents a simple anchor-like structure
        // with only Number or Percent values (no expressions or variable refs)
        is PropertyValue.Tuple -> {
            value.values.values.all { innerValue ->
                innerValue is PropertyValue.Number ||
                innerValue is PropertyValue.Percent
            }
        }

        // Complex types - not inline editable
        is PropertyValue.Style -> false
        is PropertyValue.LocalizedText -> false
        is PropertyValue.VariableRef -> false
        is PropertyValue.Spread -> false
        is PropertyValue.Expression -> false
        is PropertyValue.List -> false
        is PropertyValue.Unknown -> false
        PropertyValue.Null -> false
    }
}

/**
 * Check if a property is eligible for Quick Edit.
 * Must be in the whitelist AND have a simple value (if set).
 */
private fun isQuickEditEligible(propertyName: String, value: PropertyValue?): Boolean {
    // Must be in the Quick Edit whitelist
    if (propertyName !in QUICK_EDIT_PROPERTIES) return false

    // If no value set, it's eligible (can be added with a simple default)
    if (value == null) return true

    // Must have a simple value
    return isSimpleValue(value)
}

@Composable
private fun SchemaPropertyDisplay(
    element: UIElement,
    elementSchema: RuntimeElementSchema,
    existingPropertyNames: Set<String>,
    canvasState: CanvasState,
    tupleFieldLookup: Map<String, List<TupleFieldInfo>> = emptyMap()
) {
    // Get properties grouped by category
    val propertiesByCategory = elementSchema.getPropertiesByCategory()

    // Track expanded state for each category
    val expandedCategories = remember { mutableStateMapOf<PropertyCategory, Boolean>().apply {
        PropertyCategory.entries.forEach { this[it] = true }
    }}

    // Sort categories by order
    val sortedCategories = propertiesByCategory.keys.sortedBy { it.order }

    // Filter to only Quick Edit eligible properties
    val quickEditPropertiesByCategory = propertiesByCategory.mapValues { (_, properties) ->
        properties.filter { propSchema ->
            val value = element.getProperty(propSchema.name)
            isQuickEditEligible(propSchema.name, value)
        }
    }

    // Check if there are any Quick Edit properties at all
    val hasAnyQuickEditProperties = quickEditPropertiesByCategory.values.any { it.isNotEmpty() }

    if (!hasAnyQuickEditProperties) {
        Text(
            text = "No editable properties available.",
            color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
        )
        return
    }

    // Display properties grouped by category (only Quick Edit eligible ones)
    for (category in sortedCategories) {
        val properties = quickEditPropertiesByCategory[category] ?: continue
        if (properties.isEmpty()) continue

        val isExpanded = expandedCategories[category] ?: true

        QuickEditCategorySection(
            category = category,
            properties = properties,
            element = element,
            existingPropertyNames = existingPropertyNames,
            isExpanded = isExpanded,
            onToggleExpanded = { expandedCategories[category] = !isExpanded },
            canvasState = canvasState,
            tupleFieldLookup = tupleFieldLookup
        )

        Spacer(modifier = Modifier.height(HyveSpacing.sm))
    }
}

/**
 * Simplified category section for Quick Edit panel.
 * Only shows properties that are set and have simple values.
 * Unset properties can be added via the "+" button in the header.
 */
@Composable
private fun QuickEditCategorySection(
    category: PropertyCategory,
    properties: List<RuntimePropertySchema>,
    element: UIElement,
    existingPropertyNames: Set<String>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    canvasState: CanvasState,
    tupleFieldLookup: Map<String, List<TupleFieldInfo>> = emptyMap()
) {
    // Only show properties that are set (Quick Edit focuses on editing existing values)
    val setProperties = properties.filter { it.name in existingPropertyNames }

    // Also track unset properties for the "add" functionality
    val unsetProperties = properties.filter { it.name !in existingPropertyNames }

    // Only show section if there are set properties
    if (setProperties.isEmpty() && unsetProperties.isEmpty()) return

    Column {
        // Category header (clickable to expand/collapse)
        val catInteraction = remember { MutableInteractionSource() }
        val isCatHovered by catInteraction.collectIsHoveredAsState()
        val catTextColor = if (isCatHovered)
            HyveThemeColors.colors.textSecondary
        else
            JewelTheme.globalColors.text.info.copy(alpha = 0.6f)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HyveShapes.card)
                .hoverable(catInteraction)
                .clickable { onToggleExpanded() }
                .padding(vertical = HyveSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs)
        ) {
            Icon(
                key = if (isExpanded) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(14.dp),
                tint = catTextColor
            )
            Text(
                text = category.displayName.uppercase(),
                fontWeight = FontWeight.Medium,
                style = HyveTypography.caption,
                color = catTextColor
            )
            if (setProperties.isNotEmpty()) {
                Text(
                    text = "(${setProperties.size})",
                    style = HyveTypography.caption,
                    color = JewelTheme.globalColors.text.info.copy(alpha = 0.4f)
                )
            }
        }

        if (isExpanded) {
            // Show properties with values
            for (propSchema in setProperties) {
                val value = element.getProperty(propSchema.name)
                if (value != null) {
                    QuickEditPropertyItem(
                        propSchema = propSchema,
                        value = value,
                        element = element,
                        canvasState = canvasState,
                        anchorOverride = if (propSchema.name == "Anchor") {
                            canvasState.dragPreviewAnchor.value
                        } else null,
                        knownTupleFields = tupleFieldLookup[propSchema.name] ?: emptyList()
                    )
                }
            }

            // Show unset properties that can be added
            for (propSchema in unsetProperties) {
                QuickEditUnsetPropertyItem(
                    propSchema = propSchema,
                    element = element,
                    canvasState = canvasState
                )
            }
        }
    }
}

/**
 * A single property item in the Quick Edit panel.
 * A single property item in the property inspector.
 */
@Composable
private fun QuickEditPropertyItem(
    propSchema: RuntimePropertySchema,
    value: PropertyValue,
    element: UIElement,
    canvasState: CanvasState,
    anchorOverride: com.hyve.ui.core.domain.anchor.AnchorValue? = null,
    knownTupleFields: List<TupleFieldInfo> = emptyList()
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Property editor
        Column(modifier = Modifier.weight(1f)) {
            if (value is PropertyValue.Anchor && anchorOverride != null) {
                // Use AnchorPropertyEditor directly with live drag preview,
                // but keep the property name label consistent with PropertyEditor
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = propSchema.name,
                        color = JewelTheme.globalColors.text.info
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AnchorPropertyEditor(
                        value = value,
                        onValueChange = { newValue ->
                            canvasState.updateElementProperty(
                                element = element,
                                propertyName = propSchema.name,
                                value = newValue
                            )
                        },
                        anchorOverride = anchorOverride
                    )
                }
            } else {
                // Use standard property editor
                PropertyEditor(
                    propertyName = PropertyName(propSchema.name),
                    value = value,
                    onValueChange = { newValue ->
                        canvasState.updateElementProperty(
                            element = element,
                            propertyName = propSchema.name,
                            value = newValue
                        )
                    },
                    observedValues = propSchema.observedValues,
                    knownTupleFields = knownTupleFields
                )
            }
        }

        // Remove button (for optional properties)
        if (!propSchema.required) {
            IconButton(
                onClick = {
                    canvasState.removeElementProperty(element, propSchema.name)
                },
                modifier = Modifier.size(24.dp).padding(top = 4.dp)
            ) {
                Icon(
                    key = AllIconsKeys.General.Remove,
                    contentDescription = "Remove property",
                    modifier = Modifier.size(16.dp),
                    tint = JewelTheme.globalColors.text.error.copy(alpha = 0.6f)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(HyveSpacing.xs))
    Divider(orientation = Orientation.Horizontal, color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f))
    Spacer(modifier = Modifier.height(HyveSpacing.xs))
}

/**
 * Unset property item that can be clicked to add.
 */
@Composable
private fun QuickEditUnsetPropertyItem(
    propSchema: RuntimePropertySchema,
    element: UIElement,
    canvasState: CanvasState
) {
    val unsetInteraction = remember { MutableInteractionSource() }
    val isUnsetHovered by unsetInteraction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HyveShapes.card)
            .background(
                if (isUnsetHovered) HyveThemeColors.colors.textPrimary.copy(alpha = HyveOpacity.faint)
                else Color.Transparent
            )
            .hoverable(unsetInteraction)
            .clickable {
                // Add property with default value
                canvasState.updateElementProperty(
                    element = element,
                    propertyName = propSchema.name,
                    value = propSchema.getDefaultValue(),
                    recordUndo = true,
                    allowMerge = false
                )
            }
            .padding(vertical = HyveSpacing.smd, horizontal = HyveSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            key = AllIconsKeys.General.Add,
            contentDescription = "Add property",
            modifier = Modifier.size(14.dp),
            tint = if (isUnsetHovered)
                JewelTheme.globalColors.outlines.focused
            else
                JewelTheme.globalColors.outlines.focused.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(HyveSpacing.sm))
        Text(
            text = propSchema.name,
            fontStyle = FontStyle.Italic,
            color = if (isUnsetHovered)
                JewelTheme.globalColors.text.info.copy(alpha = 0.8f)
            else
                JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun FallbackPropertyDisplay(
    element: UIElement,
    canvasState: CanvasState
) {
    // Filter to only Quick Edit eligible properties (even without schema)
    val quickEditProperties = element.properties.entries()
        .filter { (name, value) -> isQuickEditEligible(name.value, value) }

    if (quickEditProperties.isEmpty()) {
        Text(
            text = "No editable properties available.",
            color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
        )
        return
    }

    quickEditProperties.forEach { (name, value) ->
        PropertyEditor(
            propertyName = name,
            value = value,
            onValueChange = { newValue ->
                canvasState.updateElementProperty(
                    element = element,
                    propertyName = name.value,
                    value = newValue
                )
            }
        )
        Spacer(modifier = Modifier.height(HyveSpacing.sm))
        Divider(
            orientation = Orientation.Horizontal,
            color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(HyveSpacing.sm))
    }
}

/**
 * Styled "Open in Composer" button with honey accent, icon, and disabled tooltip.
 *
 * Follows the custom Box button pattern from ComposerHeader.kt (CodeToggleButton).
 * Shows a tooltip when disabled explaining that no schema is available.
 */
@Composable
private fun OpenInComposerButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor = when {
        !enabled -> colors.slate
        isHovered -> colors.honey
        else -> colors.honey.copy(alpha = 0.7f)
    }
    val backgroundColor = when {
        !enabled -> Color.Transparent
        isHovered -> colors.honeySubtle
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> colors.textDisabled
        isHovered -> colors.textPrimary
        else -> colors.textSecondary
    }
    val contentAlpha = if (enabled) 1f else 0.4f

    val button = @Composable {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HyveShapes.dialog)
                .border(1.dp, borderColor, HyveShapes.dialog)
                .background(backgroundColor, HyveShapes.dialog)
                .then(
                    if (enabled) Modifier.hoverable(interactionSource).clickable(onClick = onClick)
                    else Modifier
                )
                .padding(vertical = HyveSpacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.smd),
                modifier = Modifier.alpha(contentAlpha)
            ) {
                Text(
                    text = "\u2197",
                    color = if (enabled) colors.honey else colors.textDisabled,
                    style = HyveTypography.title
                )
                Text(
                    text = "Open in Composer",
                    color = textColor,
                    style = HyveTypography.sectionHeader
                )
            }
        }
    }

    if (!enabled) {
        TooltipArea(
            tooltip = {
                Box(
                    modifier = Modifier
                        .background(JewelTheme.globalColors.panelBackground, HyveShapes.card)
                        .border(1.dp, JewelTheme.globalColors.borders.normal, HyveShapes.card)
                        .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
                ) {
                    Text(
                        text = "No schema available for this element type",
                        color = JewelTheme.globalColors.text.normal,
                        style = HyveTypography.itemTitle
                    )
                }
            },
            delayMillis = 500,
            tooltipPlacement = TooltipPlacement.CursorPoint(
                offset = DpOffset(0.dp, 16.dp)
            )
        ) {
            button()
        }
    } else {
        button()
    }
}
