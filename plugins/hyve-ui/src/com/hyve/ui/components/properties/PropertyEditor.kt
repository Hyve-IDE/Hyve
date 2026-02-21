package com.hyve.ui.components.properties

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveTypography
import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.discovery.TupleFieldInfo
import com.hyve.ui.settings.TextInputFocusState
import com.hyve.ui.services.assets.AssetLoader
import com.hyve.ui.services.items.ItemRegistry
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import com.hyve.ui.components.colorpicker.ColorPicker
import com.hyve.ui.components.colorpicker.formatColorWithOpacity
import com.hyve.ui.components.colorpicker.isValidHexColor
import com.hyve.ui.components.colorpicker.parseHexColor
import com.hyve.ui.components.colorpicker.splitColorOpacity
import kotlin.math.*

/**
 * CompositionLocal for providing AssetLoader to property editors.
 * This allows the Asset Browser to be opened from within PathPropertyEditor
 * without threading the AssetLoader through all intermediate composables.
 */
val LocalAssetLoader = compositionLocalOf<AssetLoader?> { null }

/**
 * CompositionLocal for providing ItemRegistry to property editors.
 * This allows the Item Picker to be opened from within property editors
 * for ItemPreviewComponent elements.
 */
val LocalItemRegistry = compositionLocalOf<ItemRegistry?> { null }

/**
 * Property names whose Text values represent image/texture paths.
 * These get routed to PathPropertyEditor with a browse button.
 */
private val IMAGE_PATH_PROPERTIES = setOf(
    "TexturePath", "MaskTexturePath", "Source", "Image", "Background"
)

/**
 * Main property editor composable that dispatches to type-specific editors.
 * Supports editing all common PropertyValue types with real-time updates.
 *
 * @param propertyName The name of the property being edited
 * @param value The current property value
 * @param onValueChange Callback when the value changes
 */
@Composable
fun PropertyEditor(
    propertyName: PropertyName,
    value: PropertyValue,
    onValueChange: (PropertyValue) -> Unit,
    observedValues: List<String> = emptyList(),
    knownTupleFields: List<TupleFieldInfo> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = HyveSpacing.xs)) {
        // Property name label
        Text(
            text = propertyName.value,
            color = JewelTheme.globalColors.text.info
        )

        Spacer(modifier = Modifier.height(HyveSpacing.xs))

        // Type-specific editor
        when (value) {
            is PropertyValue.Text -> {
                if (propertyName.value in IMAGE_PATH_PROPERTIES) {
                    PathPropertyEditor(
                        value = value.value,
                        label = "Image",
                        onValueChange = {
                            onValueChange(PropertyValue.Text(it, quoted = value.quoted))
                        }
                    )
                } else if (observedValues.isNotEmpty() && observedValues.size <= 10) {
                    EnumPropertyEditor(
                        value = value,
                        options = observedValues,
                        onValueChange = onValueChange
                    )
                } else {
                    TextPropertyEditor(
                        value = value,
                        onValueChange = onValueChange
                    )
                }
            }
            is PropertyValue.Number -> NumberPropertyEditor(
                value = value,
                onValueChange = onValueChange
            )
            is PropertyValue.Percent -> PercentPropertyEditor(
                value = value,
                onValueChange = onValueChange
            )
            is PropertyValue.Boolean -> BooleanPropertyEditor(
                value = value,
                onValueChange = onValueChange
            )
            is PropertyValue.Color -> ColorPropertyEditor(
                value = value,
                onValueChange = onValueChange
            )
            is PropertyValue.Anchor -> AnchorPropertyEditor(
                value = value,
                onValueChange = onValueChange
            )
            is PropertyValue.ImagePath -> PathPropertyEditor(
                value = value.path,
                label = "Image",
                onValueChange = {
                    onValueChange(PropertyValue.ImagePath(it))
                }
            )
            is PropertyValue.FontPath -> PathPropertyEditor(
                value = value.path,
                label = "Font",
                onValueChange = { onValueChange(PropertyValue.FontPath(it)) }
            )
            is PropertyValue.Tuple -> TuplePropertyEditor(
                value = value,
                onValueChange = onValueChange,
                knownFields = knownTupleFields
            )
            is PropertyValue.List -> ListPropertyEditor(
                value = value,
                onValueChange = onValueChange
            )
            is PropertyValue.Style -> ReadOnlyPropertyDisplay(
                value = value.toString(),
                typeLabel = "Style"
            )
            is PropertyValue.LocalizedText -> ReadOnlyPropertyDisplay(
                value = "%${value.key}",
                typeLabel = "Localized"
            )
            is PropertyValue.VariableRef -> ReadOnlyPropertyDisplay(
                value = value.toString(),
                typeLabel = "Variable"
            )
            is PropertyValue.Spread -> ReadOnlyPropertyDisplay(
                value = value.toString(),
                typeLabel = "Spread"
            )
            is PropertyValue.Expression -> ReadOnlyPropertyDisplay(
                value = value.toString(),
                typeLabel = "Expression"
            )
            is PropertyValue.Unknown -> ReadOnlyPropertyDisplay(
                value = value.raw,
                typeLabel = "Unknown"
            )
            PropertyValue.Null -> ReadOnlyPropertyDisplay(
                value = "null",
                typeLabel = "Null"
            )
        }
    }
}

/**
 * Text property editor - updates in real-time as you type
 */
@Composable
fun TextPropertyEditor(
    value: PropertyValue.Text,
    onValueChange: (PropertyValue) -> Unit,
    modifier: Modifier = Modifier
) {
    var textState by remember { mutableStateOf(value.value) }
    val focusManager = LocalFocusManager.current

    // Sync external value changes (e.g., from inline editing on canvas)
    // Only update if not focused (to avoid fighting with user input)
    LaunchedEffect(value.value) {
        textState = value.value
    }

    StyledTextField(
        value = textState,
        onValueChange = { newText ->
            textState = newText
            // Update in real-time
            onValueChange(PropertyValue.Text(newText, quoted = value.quoted))
        },
        onSubmit = {
            focusManager.clearFocus()
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true
    )
}

/**
 * Enum-like property editor for TEXT properties with a small set of observed values.
 * Displays a dropdown selector with the available options.
 */
@Composable
fun EnumPropertyEditor(
    value: PropertyValue.Text,
    options: List<String>,
    onValueChange: (PropertyValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Current value display (click to open dropdown)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HyveShapes.card)
                .background(JewelTheme.globalColors.panelBackground, HyveShapes.card)
                .border(1.dp, JewelTheme.globalColors.borders.normal, HyveShapes.card)
                .clickable { expanded = !expanded }
                .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.smd)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value.value.ifEmpty { options.firstOrNull() ?: "" },
                    color = JewelTheme.globalColors.text.normal
                )
                Icon(
                    key = AllIconsKeys.General.ChevronDown,
                    contentDescription = "Open dropdown",
                    modifier = Modifier.size(14.dp),
                    tint = JewelTheme.globalColors.text.info
                )
            }
        }

        // Dropdown popup
        if (expanded) {
            Popup(
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .background(colors.midnight, HyveShapes.card)
                        .border(1.dp, colors.slate, HyveShapes.card)
                ) {
                    Column {
                        options.forEach { option ->
                            val isSelected = option == value.value
                            val itemInteraction = remember { MutableInteractionSource() }
                            val itemHovered by itemInteraction.collectIsHoveredAsState()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .hoverable(itemInteraction)
                                    .clickable {
                                        onValueChange(PropertyValue.Text(option, quoted = value.quoted))
                                        expanded = false
                                    }
                                    .background(
                                        when {
                                            isSelected -> colors.honey.copy(alpha = 0.12f)
                                            itemHovered -> colors.slate.copy(alpha = 0.5f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .padding(horizontal = HyveSpacing.mld, vertical = HyveSpacing.smd)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = option,
                                        color = if (isSelected) colors.honey else colors.textPrimary,
                                        style = HyveTypography.itemTitle.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Number property editor - updates in real-time when input is valid
 */
@Composable
fun NumberPropertyEditor(
    value: PropertyValue.Number,
    onValueChange: (PropertyValue) -> Unit,
    modifier: Modifier = Modifier
) {
    var textState by remember(value) {
        mutableStateOf(
            if (value.value % 1.0 == 0.0) value.value.toInt().toString()
            else value.value.toString()
        )
    }
    var isError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    StyledTextField(
        value = textState,
        onValueChange = { newText ->
            textState = newText
            val parsed = newText.toDoubleOrNull()
            if (parsed != null) {
                isError = false
                onValueChange(PropertyValue.Number(parsed))
            } else {
                // Allow typing minus sign or decimal point without error
                isError = newText.isNotEmpty() && newText != "-" && newText != "." && newText != "-."
            }
        },
        onSubmit = {
            focusManager.clearFocus()
        },
        modifier = modifier.fillMaxWidth(),
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        singleLine = true
    )
}

/**
 * Percent property editor - edits percentage values (e.g., 50%)
 * Displays as percentage (0-100), stores as ratio (0.0-1.0)
 *
 * Features a slider control with numeric input for precise values.
 */
@Composable
fun PercentPropertyEditor(
    value: PropertyValue.Percent,
    onValueChange: (PropertyValue) -> Unit,
    modifier: Modifier = Modifier
) {
    // Display as percentage (0-100), rounded to avoid floating point issues
    val displayPercent = kotlin.math.round(value.ratio * 10000.0) / 100.0
    var textState by remember(value) {
        mutableStateOf(
            if (displayPercent % 1.0 == 0.0) "${displayPercent.toInt()}"
            else "$displayPercent"
        )
    }
    var isError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier) {
        // Slider row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Slider
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
            ) {
                PercentSlider(
                    value = value.ratio.toFloat(),
                    onValueChange = { newRatio ->
                        val percent = kotlin.math.round(newRatio * 100.0f)
                        textState = if (percent % 1.0f == 0.0f) "${percent.toInt()}" else "$percent"
                        onValueChange(PropertyValue.Percent(newRatio.toDouble()))
                    }
                )
            }

            Spacer(modifier = Modifier.width(HyveSpacing.sm))

            // Numeric input
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(70.dp)
            ) {
                StyledTextField(
                    value = textState,
                    onValueChange = { newText ->
                        textState = newText
                        // Parse percentage - strip % if present
                        val cleanText = newText.trimEnd('%', ' ')
                        val parsed = cleanText.toDoubleOrNull()
                        if (parsed != null && parsed >= 0.0 && parsed <= 100.0) {
                            isError = false
                            onValueChange(PropertyValue.Percent(parsed / 100.0))
                        } else {
                            isError = newText.isNotEmpty() && cleanText != "-" && cleanText != "."
                        }
                    },
                    onSubmit = {
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.weight(1f),
                    isError = isError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true
                )
                Text(
                    text = "%",
                    color = JewelTheme.globalColors.text.info,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

/**
 * Custom slider for percentage values.
 */
@Composable
private fun PercentSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor = JewelTheme.globalColors.borders.normal
    val activeColor = JewelTheme.globalColors.outlines.focused
    val thumbColor = JewelTheme.globalColors.outlines.focused

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clip(HyveShapes.card)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newValue = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val newValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
    ) {
        val trackHeight = 6.dp.toPx()
        val trackY = (size.height - trackHeight) / 2

        // Background track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackY),
            size = Size(size.width, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        )

        // Active track
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, trackY),
            size = Size(size.width * value, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        )

        // Thumb
        val thumbRadius = 8.dp.toPx()
        val thumbX = size.width * value
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = Offset(thumbX.coerceIn(thumbRadius, size.width - thumbRadius), size.height / 2)
        )
    }
}

/**
 * Boolean property editor - checkbox (already real-time)
 */
@Composable
fun BooleanPropertyEditor(
    value: PropertyValue.Boolean,
    onValueChange: (PropertyValue) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = value.value,
            onCheckedChange = { newValue ->
                onValueChange(PropertyValue.Boolean(newValue))
            }
        )
        Spacer(modifier = Modifier.width(HyveSpacing.sm))
        Text(
            text = if (value.value) "true" else "false",
            color = JewelTheme.globalColors.text.info
        )
    }
}

/**
 * Color property editor with hex input and color preview - updates in real-time
 */
@Composable
fun ColorPropertyEditor(
    value: PropertyValue.Color,
    onValueChange: (PropertyValue) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track when we're the source of changes vs external changes (like undo)
    // Use value as key so these reset when the external value changes
    var lastEmittedHex by remember(value.hex) { mutableStateOf(value.hex) }
    var lastEmittedAlpha by remember(value.alpha) { mutableStateOf(value.alpha) }

    // Local state for editing - also keyed on value to reset on external changes
    var hexState by remember(value.hex) { mutableStateOf(value.hex) }
    var alphaState by remember(value.alpha) { mutableStateOf(value.alpha?.toString() ?: "1.0") }
    var isHexError by remember { mutableStateOf(false) }
    var isAlphaError by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Helper to emit color change if valid
    // Only emit if the value is actually different from what we last emitted
    fun tryUpdateColor(hex: String, alphaStr: String) {
        if (isValidHexColor(hex)) {
            val alpha = alphaStr.toFloatOrNull()?.takeIf { it in 0f..1f }
            // Only emit if actually changed from current value
            if (hex != lastEmittedHex || alpha != lastEmittedAlpha) {
                lastEmittedHex = hex
                lastEmittedAlpha = alpha
                onValueChange(PropertyValue.Color(hex, alpha))
            }
        }
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Color preview swatch
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(HyveShapes.card)
                    .background(parseHexColor(hexState).copy(alpha = alphaState.toFloatOrNull() ?: 1.0f))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, HyveShapes.card)
                    .clickable { showColorPicker = !showColorPicker }
            )

            Spacer(modifier = Modifier.width(HyveSpacing.sm))

            // Hex input
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hex",
                    color = JewelTheme.globalColors.text.info
                )
                StyledTextField(
                    value = hexState,
                    onValueChange = { newHex ->
                        hexState = newHex
                        isHexError = !isValidHexColor(newHex) && newHex.isNotEmpty() && newHex != "#"
                        tryUpdateColor(newHex, alphaState)
                    },
                    onSubmit = { focusManager.clearFocus() },
                    isError = isHexError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(HyveSpacing.sm))

            // Alpha input
            Column(modifier = Modifier.width(60.dp)) {
                Text(
                    text = "Alpha",
                    color = JewelTheme.globalColors.text.info
                )
                StyledTextField(
                    value = alphaState,
                    onValueChange = { newAlpha ->
                        alphaState = newAlpha
                        val parsed = newAlpha.toFloatOrNull()
                        isAlphaError = newAlpha.isNotEmpty() && newAlpha != "." && (parsed == null || parsed !in 0f..1f)
                        tryUpdateColor(hexState, newAlpha)
                    },
                    onSubmit = { focusManager.clearFocus() },
                    isError = isAlphaError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Color picker dropdown
        if (showColorPicker) {
            Spacer(modifier = Modifier.height(HyveSpacing.sm))
            ColorPicker(
                currentColor = formatColorWithOpacity(hexState, alphaState.toFloatOrNull() ?: 1f),
                onColorChanged = { colorStr ->
                    val (hex, opacity) = splitColorOpacity(colorStr)
                    hexState = hex
                    isHexError = false
                    if (opacity != null) {
                        alphaState = opacity.toString()
                        isAlphaError = false
                    }
                    tryUpdateColor(hex, alphaState)
                },
                onDismiss = { showColorPicker = false }
            )
        }
    }
}


/**
 * Anchor property editor - edits position and size dimensions in real-time.
 *
 * Uses a compact 2-row layout per spec:
 * Row 1: L [  ] T [  ] R [  ]
 * Row 2: W [  ] H [  ] B [  ]
 */
@Composable
fun AnchorPropertyEditor(
    value: PropertyValue.Anchor,
    onValueChange: (PropertyValue) -> Unit,
    anchorOverride: AnchorValue? = null,
    modifier: Modifier = Modifier
) {
    val anchor = anchorOverride ?: value.anchor
    val isPreview = anchorOverride != null

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.panelBackground, HyveShapes.dialog)
        ) {
            Column(modifier = Modifier.padding(HyveSpacing.sm)) {
                // Row 1: Left, Top, Right
                Row(modifier = Modifier.fillMaxWidth()) {
                    CompactAnchorDimensionEditor(
                        label = "L",
                        dimension = anchor.left,
                        onDimensionChange = { newDim ->
                            if (isPreview) return@CompactAnchorDimensionEditor
                            onValueChange(PropertyValue.Anchor(anchor.copy(left = newDim)))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(HyveSpacing.xs))
                    CompactAnchorDimensionEditor(
                        label = "T",
                        dimension = anchor.top,
                        onDimensionChange = { newDim ->
                            if (isPreview) return@CompactAnchorDimensionEditor
                            onValueChange(PropertyValue.Anchor(anchor.copy(top = newDim)))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(HyveSpacing.xs))
                    CompactAnchorDimensionEditor(
                        label = "R",
                        dimension = anchor.right,
                        onDimensionChange = { newDim ->
                            if (isPreview) return@CompactAnchorDimensionEditor
                            onValueChange(PropertyValue.Anchor(anchor.copy(right = newDim)))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(HyveSpacing.xs))

                // Row 2: Width, Height, Bottom
                Row(modifier = Modifier.fillMaxWidth()) {
                    CompactAnchorDimensionEditor(
                        label = "W",
                        dimension = anchor.width,
                        onDimensionChange = { newDim ->
                            if (isPreview) return@CompactAnchorDimensionEditor
                            onValueChange(PropertyValue.Anchor(anchor.copy(width = newDim)))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(HyveSpacing.xs))
                    CompactAnchorDimensionEditor(
                        label = "H",
                        dimension = anchor.height,
                        onDimensionChange = { newDim ->
                            if (isPreview) return@CompactAnchorDimensionEditor
                            onValueChange(PropertyValue.Anchor(anchor.copy(height = newDim)))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(HyveSpacing.xs))
                    CompactAnchorDimensionEditor(
                        label = "B",
                        dimension = anchor.bottom,
                        onDimensionChange = { newDim ->
                            if (isPreview) return@CompactAnchorDimensionEditor
                            onValueChange(PropertyValue.Anchor(anchor.copy(bottom = newDim)))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Compact anchor dimension editor with inline label.
 * Format: "L: [value]" where label is a single letter.
 */
@Composable
fun CompactAnchorDimensionEditor(
    label: String,
    dimension: AnchorDimension?,
    onDimensionChange: (AnchorDimension?) -> Unit,
    modifier: Modifier = Modifier
) {
    var textState by remember(dimension) {
        mutableStateOf(
            when (dimension) {
                is AnchorDimension.Absolute -> dimension.pixels.let {
                    if (it % 1.0f == 0.0f) it.toInt().toString() else it.toString()
                }
                is AnchorDimension.Relative -> "${(dimension.ratio * 100).toInt()}%"
                null -> ""
            }
        )
    }
    var isError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Compact label with spacing
        Text(
            text = "$label:",
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.width(20.dp)
        )

        Spacer(modifier = Modifier.width(HyveSpacing.xs))

        // Input field - sized for decimal values like "471.0"
        StyledTextField(
            value = textState,
            onValueChange = { newText ->
                textState = newText
                if (newText.isEmpty()) {
                    isError = false
                    // Don't update to null while typing - wait for submit
                } else {
                    val parsed = parseAnchorDimension(newText)
                    if (parsed != null) {
                        isError = false
                        onDimensionChange(parsed)
                    } else {
                        // Allow partial input without error
                        isError = newText != "-" && newText != "." && newText != "-." && !newText.endsWith("%")
                    }
                }
            },
            onSubmit = {
                // On Enter, if empty, set to null
                if (textState.isEmpty()) {
                    onDimensionChange(null)
                }
                focusManager.clearFocus()
            },
            isError = isError,
            singleLine = true,
            placeholder = "-",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Editor for a single anchor dimension (supports Absolute, Relative, or null)
 * Updates in real-time when input is valid
 */
@Composable
fun AnchorDimensionEditor(
    label: String,
    dimension: AnchorDimension?,
    onDimensionChange: (AnchorDimension?) -> Unit,
    modifier: Modifier = Modifier
) {
    var textState by remember(dimension) {
        mutableStateOf(
            when (dimension) {
                is AnchorDimension.Absolute -> dimension.pixels.let {
                    if (it % 1.0f == 0.0f) it.toInt().toString() else it.toString()
                }
                is AnchorDimension.Relative -> "${(dimension.ratio * 100).toInt()}%"
                null -> ""
            }
        )
    }
    var isError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier) {
        Text(
            text = label,
            color = JewelTheme.globalColors.text.info
        )
        StyledTextField(
            value = textState,
            onValueChange = { newText ->
                textState = newText
                if (newText.isEmpty()) {
                    isError = false
                    // Don't update to null while typing - wait for submit
                } else {
                    val parsed = parseAnchorDimension(newText)
                    if (parsed != null) {
                        isError = false
                        onDimensionChange(parsed)
                    } else {
                        // Allow partial input without error (minus, dot, percent sign)
                        isError = newText != "-" && newText != "." && newText != "-." && !newText.endsWith("%")
                    }
                }
            },
            onSubmit = {
                // On Enter, if empty, set to null; otherwise validate
                if (textState.isEmpty()) {
                    onDimensionChange(null)
                }
                focusManager.clearFocus()
            },
            isError = isError,
            singleLine = true,
            placeholder = "-",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Path property editor for ImagePath and FontPath - updates in real-time.
 * Includes a Browse button for ImagePath that opens the Asset Browser dialog.
 */
@Composable
fun PathPropertyEditor(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showBrowseButton: Boolean = true
) {
    var textState by remember(value) { mutableStateOf(value) }
    val focusManager = LocalFocusManager.current

    // Asset Browser dialog state
    var showAssetBrowser by remember { mutableStateOf(false) }
    val assetLoader = LocalAssetLoader.current

    // Only show browse button for images and if asset loader can load textures
    val canBrowse = showBrowseButton && label == "Image" && assetLoader != null && assetLoader.canLoadTextures

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StyledTextField(
                value = textState,
                onValueChange = { newText ->
                    textState = newText
                    onValueChange(newText)
                },
                onSubmit = { focusManager.clearFocus() },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            if (canBrowse) {
                Spacer(modifier = Modifier.width(HyveSpacing.xs))
                IconButton(
                    onClick = { showAssetBrowser = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        key = AllIconsKeys.Actions.MenuOpen,
                        contentDescription = "Browse assets",
                        modifier = Modifier.size(18.dp),
                        tint = JewelTheme.globalColors.outlines.focused
                    )
                }
            }
        }
    }

    // Texture Browser dialog
    if (showAssetBrowser && assetLoader != null) {
        com.hyve.ui.components.dialogs.TextureBrowserDialog(
            assetLoader = assetLoader,
            projectResourcesPath = assetLoader.projectPath,
            onDismiss = { showAssetBrowser = false },
            onTextureSelected = { selectedPath ->
                textState = selectedPath
                onValueChange(selectedPath)
                showAssetBrowser = false
            },
            initialPath = textState
        )
    }
}

/**
 * Item ID property editor for ItemPreviewComponent's PreviewItemId.
 * Shows a text field with a Browse button that opens the Item Picker dialog.
 */
@Composable
fun ItemIdPropertyEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textState by remember(value) { mutableStateOf(value) }
    val focusManager = LocalFocusManager.current

    // Item Picker dialog state
    var showItemPicker by remember { mutableStateOf(false) }
    val assetLoader = LocalAssetLoader.current
    val itemRegistry = LocalItemRegistry.current

    // Only show browse button if asset loader and item registry are available
    val canBrowse = assetLoader != null && assetLoader.isAvailable && itemRegistry != null

    Column(modifier = modifier) {
        Text(
            text = "Preview Item",
            color = JewelTheme.globalColors.text.info
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StyledTextField(
                value = textState,
                onValueChange = { newText ->
                    textState = newText
                    onValueChange(newText)
                },
                onSubmit = { focusManager.clearFocus() },
                singleLine = true,
                placeholder = "Enter item ID or browse...",
                modifier = Modifier.weight(1f)
            )

            if (canBrowse) {
                Spacer(modifier = Modifier.width(HyveSpacing.xs))
                IconButton(
                    onClick = { showItemPicker = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        key = AllIconsKeys.Actions.MenuOpen,
                        contentDescription = "Browse items",
                        modifier = Modifier.size(18.dp),
                        tint = JewelTheme.globalColors.outlines.focused
                    )
                }
            }
        }

        // Show hint if no item is set
        if (textState.isBlank()) {
            Text(
                text = "Select an item to preview in the editor",
                color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    // Item Picker dialog
    if (showItemPicker && assetLoader != null) {
        com.hyve.ui.components.dialogs.ItemPickerDialog(
            assetLoader = assetLoader,
            onDismiss = { showItemPicker = false },
            onItemSelected = { selectedItemId ->
                textState = selectedItemId
                onValueChange(selectedItemId)
                showItemPicker = false
            },
            initialItemId = textState
        )
    }
}

/**
 * Tuple property editor - displays nested key-value pairs with ability to add/remove entries
 */
@Composable
fun TuplePropertyEditor(
    value: PropertyValue.Tuple,
    onValueChange: (PropertyValue) -> Unit,
    knownFields: List<TupleFieldInfo> = emptyList(),
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground, HyveShapes.dialog)
    ) {
        Column(modifier = Modifier.padding(HyveSpacing.sm)) {
            if (value.values.isEmpty()) {
                // Empty tuple - show placeholder with add button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = HyveSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "(empty)",
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)
                    )
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            key = AllIconsKeys.General.Add,
                            contentDescription = "Add entry",
                            modifier = Modifier.size(16.dp),
                            tint = JewelTheme.globalColors.outlines.focused
                        )
                    }
                }
            } else {
                // Display existing entries
                value.values.forEach { (key, propValue) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            PropertyEditor(
                                propertyName = PropertyName(key),
                                value = propValue,
                                onValueChange = { newValue ->
                                    val newValues = value.values.toMutableMap()
                                    newValues[key] = newValue
                                    onValueChange(PropertyValue.Tuple(newValues))
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                val newValues = value.values.toMutableMap()
                                newValues.remove(key)
                                onValueChange(PropertyValue.Tuple(newValues))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                key = AllIconsKeys.Actions.Close,
                                contentDescription = "Remove $key",
                                modifier = Modifier.size(14.dp),
                                tint = JewelTheme.globalColors.text.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Add more button
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.padding(top = HyveSpacing.xs)
                ) {
                    Icon(
                        key = AllIconsKeys.General.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(HyveSpacing.xs))
                    Text("Add entry")
                }
            }
        }
    }

    // Add entry dialog
    if (showAddDialog) {
        TupleAddEntryDialog(
            existingKeys = value.values.keys,
            knownFields = knownFields,
            onDismiss = { showAddDialog = false },
            onAdd = { key, entryValue ->
                val newValues = value.values.toMutableMap()
                newValues[key] = entryValue
                onValueChange(PropertyValue.Tuple(newValues))
                showAddDialog = false
            }
        )
    }
}

/**
 * Dialog for adding a new entry to a Tuple.
 * When [knownFields] is non-empty, shows a list of known field suggestions
 * above the key name input. Clicking a suggestion pre-fills the key and type.
 */
@Composable
private fun TupleAddEntryDialog(
    existingKeys: Set<String>,
    knownFields: List<TupleFieldInfo> = emptyList(),
    onDismiss: () -> Unit,
    onAdd: (String, PropertyValue) -> Unit
) {
    var keyName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TupleEntryType.NUMBER) }
    var textValue by remember { mutableStateOf("") }
    var numberValue by remember { mutableStateOf("0") }

    val trimmedKey = keyName.trim()
    val keyExists = trimmedKey in existingKeys
    val canAdd = trimmedKey.isNotEmpty() && !keyExists

    // Filter known fields: exclude already-present keys, sort by occurrences, cap at 15
    val suggestions = remember(knownFields, existingKeys) {
        knownFields
            .filter { it.name !in existingKeys }
            .sortedByDescending { it.occurrences }
            .take(15)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(JewelTheme.globalColors.panelBackground, HyveShapes.dialog)
                .padding(HyveSpacing.lg)
                .widthIn(max = 360.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HyveSpacing.md)) {
                Text(text = "Add Tuple Entry", color = JewelTheme.globalColors.text.normal)

                // Known field suggestions
                if (suggestions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(HyveSpacing.xxs)) {
                        Text(
                            text = "Known fields",
                            color = JewelTheme.globalColors.text.info,
                            style = HyveTypography.caption
                        )
                        // Filter by typed text
                        val filtered = if (keyName.isBlank()) suggestions
                            else suggestions.filter { it.name.contains(keyName.trim(), ignoreCase = true) }
                        for (field in filtered) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(HyveShapes.card)
                                    .clickable {
                                        // Pre-fill key and infer type
                                        keyName = field.name
                                        selectedType = when (field.inferredType.uppercase()) {
                                            "NUMBER", "PERCENT" -> TupleEntryType.NUMBER
                                            else -> TupleEntryType.TEXT
                                        }
                                        // Auto-add immediately (tuple fields are typically unquoted identifiers)
                                        val entryValue = when (field.inferredType.uppercase()) {
                                            "NUMBER", "PERCENT" -> PropertyValue.Number(
                                                field.observedValues.firstOrNull()?.toDoubleOrNull() ?: 0.0
                                            )
                                            "BOOLEAN" -> PropertyValue.Text(
                                                field.observedValues.firstOrNull() ?: "false",
                                                quoted = false
                                            )
                                            "COLOR" -> PropertyValue.Text(
                                                field.observedValues.firstOrNull() ?: "#ffffff",
                                                quoted = false
                                            )
                                            else -> PropertyValue.Text(
                                                field.observedValues.firstOrNull() ?: "",
                                                quoted = false
                                            )
                                        }
                                        onAdd(field.name, entryValue)
                                    }
                                    .background(
                                        JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f),
                                        HyveShapes.card
                                    )
                                    .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm)
                            ) {
                                // Type badge
                                Box(
                                    modifier = Modifier
                                        .background(
                                            JewelTheme.globalColors.text.info.copy(alpha = 0.15f),
                                            HyveShapes.input
                                        )
                                        .padding(horizontal = HyveSpacing.xs, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = field.inferredType.take(4),
                                        color = JewelTheme.globalColors.text.info,
                                        style = HyveTypography.micro
                                    )
                                }
                                Text(
                                    text = field.name,
                                    color = JewelTheme.globalColors.text.normal,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${field.occurrences}x",
                                    color = JewelTheme.globalColors.text.info.copy(alpha = 0.5f),
                                    style = HyveTypography.badge
                                )
                            }
                        }
                    }
                }

                Column {
                    Text(text = "Key", color = JewelTheme.globalColors.text.info)
                    StyledTextField(
                        value = keyName,
                        onValueChange = { keyName = it },
                        placeholder = if (suggestions.isNotEmpty()) "Or type custom key..." else "e.g., Left, Top, Width",
                        singleLine = true,
                        isError = keyExists,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (keyExists) {
                        Text(text = "Key already exists", color = JewelTheme.globalColors.text.error)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm)
                ) {
                    TupleEntryType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        OutlinedButton(
                            onClick = { selectedType = type },
                            modifier = Modifier.background(
                                if (isSelected) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.2f)
                                else Color.Transparent,
                                HyveShapes.card
                            )
                        ) {
                            Text(type.displayName)
                        }
                    }
                }

                when (selectedType) {
                    TupleEntryType.NUMBER -> {
                        Column {
                            Text(text = "Value", color = JewelTheme.globalColors.text.info)
                            StyledTextField(
                                value = numberValue,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue == "-" || newValue.toDoubleOrNull() != null) {
                                        numberValue = newValue
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    TupleEntryType.TEXT -> {
                        Column {
                            Text(text = "Value", color = JewelTheme.globalColors.text.info)
                            StyledTextField(
                                value = textValue,
                                onValueChange = { textValue = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(HyveSpacing.sm))
                    OutlinedButton(
                        onClick = {
                            val entryValue = when (selectedType) {
                                TupleEntryType.NUMBER -> PropertyValue.Number(numberValue.toDoubleOrNull() ?: 0.0)
                                TupleEntryType.TEXT -> PropertyValue.Text(textValue, quoted = false)
                            }
                            onAdd(trimmedKey, entryValue)
                        },
                        enabled = canAdd
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

private enum class TupleEntryType(val displayName: String) {
    NUMBER("Number"),
    TEXT("Text")
}

/**
 * List property editor - displays list items with ability to add/remove entries
 */
@Composable
fun ListPropertyEditor(
    value: PropertyValue.List,
    onValueChange: (PropertyValue) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground, HyveShapes.dialog)
    ) {
        Column(modifier = Modifier.padding(HyveSpacing.sm)) {
            if (value.values.isEmpty()) {
                // Empty list - show placeholder with add button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = HyveSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "(empty list)",
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)
                    )
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            key = AllIconsKeys.General.Add,
                            contentDescription = "Add item",
                            modifier = Modifier.size(16.dp),
                            tint = JewelTheme.globalColors.outlines.focused
                        )
                    }
                }
            } else {
                // Display existing items
                value.values.forEachIndexed { index, propValue ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            PropertyEditor(
                                propertyName = PropertyName("[$index]"),
                                value = propValue,
                                onValueChange = { newValue ->
                                    val newValues = value.values.toMutableList()
                                    newValues[index] = newValue
                                    onValueChange(PropertyValue.List(newValues))
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                val newValues = value.values.toMutableList()
                                newValues.removeAt(index)
                                onValueChange(PropertyValue.List(newValues))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                key = AllIconsKeys.Actions.Close,
                                contentDescription = "Remove item $index",
                                modifier = Modifier.size(14.dp),
                                tint = JewelTheme.globalColors.text.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Add more button
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.padding(top = HyveSpacing.xs)
                ) {
                    Icon(
                        key = AllIconsKeys.General.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(HyveSpacing.xs))
                    Text("Add item")
                }
            }
        }
    }

    // Add item dialog
    if (showAddDialog) {
        ListAddItemDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { itemValue ->
                val newValues = value.values.toMutableList()
                newValues.add(itemValue)
                onValueChange(PropertyValue.List(newValues))
                showAddDialog = false
            }
        )
    }
}

/**
 * Dialog for adding a new item to a List
 */
@Composable
private fun ListAddItemDialog(
    onDismiss: () -> Unit,
    onAdd: (PropertyValue) -> Unit
) {
    var selectedType by remember { mutableStateOf(TupleEntryType.TEXT) }
    var textValue by remember { mutableStateOf("") }
    var numberValue by remember { mutableStateOf("0") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(JewelTheme.globalColors.panelBackground, HyveShapes.dialog)
                .padding(HyveSpacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HyveSpacing.md)) {
                Text(text = "Add List Item", color = JewelTheme.globalColors.text.normal)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm)
                ) {
                    TupleEntryType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        OutlinedButton(
                            onClick = { selectedType = type },
                            modifier = Modifier.background(
                                if (isSelected) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.2f)
                                else Color.Transparent,
                                HyveShapes.card
                            )
                        ) {
                            Text(type.displayName)
                        }
                    }
                }

                when (selectedType) {
                    TupleEntryType.NUMBER -> {
                        Column {
                            Text(text = "Value", color = JewelTheme.globalColors.text.info)
                            StyledTextField(
                                value = numberValue,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue == "-" || newValue.toDoubleOrNull() != null) {
                                        numberValue = newValue
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    TupleEntryType.TEXT -> {
                        Column {
                            Text(text = "Value", color = JewelTheme.globalColors.text.info)
                            StyledTextField(
                                value = textValue,
                                onValueChange = { textValue = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(HyveSpacing.sm))
                    OutlinedButton(
                        onClick = {
                            val itemValue = when (selectedType) {
                                TupleEntryType.NUMBER -> PropertyValue.Number(numberValue.toDoubleOrNull() ?: 0.0)
                                TupleEntryType.TEXT -> PropertyValue.Text(textValue, quoted = false)
                            }
                            onAdd(itemValue)
                        }
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

/**
 * Read-only display for property types that aren't editable inline.
 * Shows the value with a type label badge.
 *
 * @param value The property value to display
 * @param typeLabel The type label (e.g., "Style", "Variable")
 */
@Composable
fun ReadOnlyPropertyDisplay(
    value: String,
    typeLabel: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f), HyveShapes.card)
    ) {
        Column(modifier = Modifier.padding(HyveSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Value display
                Text(
                    text = value,
                    color = JewelTheme.globalColors.text.info,
                    modifier = Modifier.weight(1f)
                )

                // Type badge
                Box(
                    modifier = Modifier
                        .background(
                            JewelTheme.globalColors.text.info.copy(alpha = 0.15f),
                            HyveShapes.card
                        )
                        .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xxs)
                ) {
                    Text(
                        text = typeLabel,
                        color = JewelTheme.globalColors.text.info
                    )
                }
            }
        }
    }
}

/**
 * Styled text field component with consistent appearance.
 * Supports Enter key to submit and clear focus.
 */
@Composable
fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit = {},
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    singleLine: Boolean = true,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
) {
    var hasFocus by remember { mutableStateOf(false) }

    // Track global focus state for hotkey suppression
    DisposableEffect(hasFocus) {
        if (hasFocus) {
            TextInputFocusState.setFocused(true)
        }
        onDispose {
            if (hasFocus) {
                TextInputFocusState.setFocused(false)
            }
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(28.dp)
            .background(
                color = JewelTheme.globalColors.panelBackground,
                shape = HyveShapes.card
            )
            .border(
                width = 1.dp,
                color = when {
                    isError -> JewelTheme.globalColors.text.error
                    hasFocus -> JewelTheme.globalColors.outlines.focused
                    else -> JewelTheme.globalColors.borders.normal
                },
                shape = HyveShapes.card
            )
            .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xs)
            .onFocusChanged { focusState ->
                hasFocus = focusState.isFocused
                // Update global focus state immediately on change
                TextInputFocusState.setFocused(focusState.isFocused)
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp) {
                    onSubmit()
                    true
                } else {
                    false
                }
            },
        textStyle = TextStyle(
            color = JewelTheme.globalColors.text.normal
        ),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(
            onDone = { onSubmit() }
        ),
        cursorBrush = SolidColor(JewelTheme.globalColors.outlines.focused),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        }
    )
}

// --- Utility Functions ---

/**
 * Parse anchor dimension from text input.
 * Supports:
 * - "100" -> Absolute(100f)
 * - "100.5" -> Absolute(100.5f)
 * - "50%" -> Relative(0.5f)
 */
private fun parseAnchorDimension(text: String): AnchorDimension? {
    val trimmed = text.trim()

    // Check for percentage
    if (trimmed.endsWith("%")) {
        val percentValue = trimmed.dropLast(1).toFloatOrNull()
        if (percentValue != null && percentValue in 0f..100f) {
            return AnchorDimension.Relative(percentValue / 100f)
        }
        return null
    }

    // Check for absolute value
    val absoluteValue = trimmed.toFloatOrNull()
    if (absoluteValue != null) {
        return AnchorDimension.Absolute(absoluteValue)
    }

    return null
}
