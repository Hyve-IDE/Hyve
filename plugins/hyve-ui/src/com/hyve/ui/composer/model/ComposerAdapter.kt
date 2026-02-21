// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.result.Result
import com.hyve.ui.parser.UIParser
import com.hyve.ui.schema.ElementSchema
import com.hyve.ui.schema.PropertySchema
import com.hyve.ui.schema.PropertyType
import com.hyve.ui.schema.PropertyCategory
import com.hyve.ui.schema.RuntimeElementSchema
import com.hyve.ui.schema.RuntimePropertySchema

/**
 * Converts a [UIElement] into a Composer [ElementDefinition] using a schema
 * to discover all available property slots.
 *
 * Properties that exist on the element are filled with their current values.
 * Properties defined in the schema but absent on the element are included as
 * empty slots.
 */
fun UIElement.toElementDefinition(schema: ElementSchema): ElementDefinition {
    val slots = schema.properties.map { propSchema ->
        val value = getProperty(propSchema.name)
        val (fillMode, stringValue) = value?.toSlotValue() ?: (FillMode.EMPTY to "")
        PropertySlot(
            name = propSchema.name.value,
            type = resolvePropertyType(propSchema.name.value, propSchema.type.toComposerPropertyType(), value),
            category = inferSlotCategory(propSchema.name.value),
            fillMode = fillMode,
            value = stringValue,
            required = propSchema.required,
            description = propSchema.description,
            anchorValues = extractAnchorValues(value),
            tupleValues = extractTupleValues(value)
        )
    }
    return ElementDefinition(
        type = type,
        id = id?.value ?: "",
        slots = slots
    )
}

/**
 * Converts a [UIElement] into a Composer [ElementDefinition] using a
 * [RuntimeElementSchema] from schema discovery.
 */
fun UIElement.toElementDefinition(schema: RuntimeElementSchema): ElementDefinition {
    val slots = schema.properties.map { propSchema ->
        val value = getProperty(propSchema.name)
        val (fillMode, stringValue) = value?.toSlotValue() ?: (FillMode.EMPTY to "")
        PropertySlot(
            name = propSchema.name,
            type = resolvePropertyType(propSchema.name, propSchema.type.toComposerPropertyType(), value),
            category = propSchema.category.toSlotCategory(),
            fillMode = fillMode,
            value = stringValue,
            required = propSchema.required,
            description = "",
            anchorValues = extractAnchorValues(value),
            tupleValues = extractTupleValues(value)
        )
    }
    return ElementDefinition(
        type = type,
        id = id?.value ?: "",
        slots = slots
    )
}

/**
 * Applies the Composer's [ElementDefinition] back to a [UIElement],
 * producing a new UIElement with updated properties.
 *
 * Empty slots are removed from the property map (they represent unset
 * properties). Filled slots are converted back to [PropertyValue] instances.
 *
 * To avoid lossy round-trip conversions (e.g., when the schema-inferred type
 * doesn't match the actual property type), unchanged slots preserve the
 * original [PropertyValue] from the source element.
 */
fun ElementDefinition.applyTo(element: UIElement): UIElement {
    var properties = PropertyMap.empty()
    for (slot in slots) {
        if (slot.fillMode == FillMode.EMPTY) continue

        // Check if the slot value is unchanged from the original element.
        // If so, preserve the original PropertyValue to avoid lossy conversion
        // (e.g., Anchor typed as TUPLE in schema would otherwise degrade to Unknown).
        val originalValue = element.getProperty(slot.name)
        if (originalValue != null && isSlotUnchanged(slot, originalValue)) {
            properties = properties.set(PropertyName(slot.name), originalValue)
        } else {
            val propertyValue = slotToPropertyValue(slot)
            if (propertyValue != null) {
                properties = properties.set(PropertyName(slot.name), propertyValue)
            }
        }
    }
    // Preserve any properties from the original element that aren't in the slots
    val slotNames = slots.map { it.name }.toSet()
    for ((name, value) in element.properties.entries()) {
        if (name.value !in slotNames) {
            properties = properties.set(name, value)
        }
    }
    return element.copy(
        id = if (id.isNotBlank()) ElementId(id) else null,
        properties = properties
    )
}

/**
 * Checks whether a slot's value matches the original [PropertyValue], indicating
 * the user did not modify it in the Composer. Compares the slot's string
 * representation against what [toSlotValue] would produce from the original.
 */
private fun isSlotUnchanged(slot: PropertySlot, originalValue: PropertyValue): Boolean {
    val (originalFillMode, originalStringValue) = originalValue.toSlotValue()
    if (slot.fillMode != originalFillMode) return false
    if (slot.value != originalStringValue) return false
    // For anchor-type values, also check that anchorValues haven't changed
    if (originalValue is PropertyValue.Anchor) {
        val originalAnchorValues = extractAnchorValues(originalValue)
        if (slot.anchorValues != originalAnchorValues) return false
    }
    // For tuple-type values, also check that tupleValues haven't changed
    if (originalValue is PropertyValue.Tuple) {
        val originalTupleValues = extractTupleValues(originalValue)
        if (slot.tupleValues != originalTupleValues) return false
    }
    return true
}

/**
 * Converts a [PropertyValue] to the Composer's (FillMode, String) pair.
 */
fun PropertyValue.toSlotValue(): Pair<FillMode, String> {
    return when (this) {
        is PropertyValue.Text -> FillMode.LITERAL to value
        is PropertyValue.Number -> FillMode.LITERAL to toString()
        is PropertyValue.Percent -> FillMode.LITERAL to toString()
        is PropertyValue.Boolean -> FillMode.LITERAL to value.toString()
        is PropertyValue.Color -> FillMode.LITERAL to toString()
        is PropertyValue.ImagePath -> FillMode.LITERAL to path
        is PropertyValue.FontPath -> FillMode.LITERAL to path
        is PropertyValue.Anchor -> FillMode.LITERAL to toString()
        is PropertyValue.Style -> FillMode.VARIABLE to reference.toString()
        is PropertyValue.Tuple -> FillMode.LITERAL to toString()
        is PropertyValue.List -> FillMode.LITERAL to toString()
        is PropertyValue.LocalizedText -> FillMode.LOCALIZATION to key
        is PropertyValue.VariableRef -> FillMode.IMPORT to toString()
        is PropertyValue.Spread -> FillMode.LITERAL to toString()
        is PropertyValue.Expression -> FillMode.EXPRESSION to toString()
        is PropertyValue.Unknown -> FillMode.LITERAL to raw
        is PropertyValue.Null -> FillMode.EMPTY to ""
    }
}

/**
 * Converts a Composer [PropertySlot] back to a [PropertyValue].
 *
 * Returns null if the value cannot be parsed (the caller should skip it).
 */
fun slotToPropertyValue(slot: PropertySlot): PropertyValue? {
    if (slot.fillMode == FillMode.EMPTY || slot.value.isBlank()) return null

    return when (slot.fillMode) {
        FillMode.EMPTY -> null
        FillMode.LOCALIZATION -> PropertyValue.LocalizedText(slot.value)
        FillMode.EXPRESSION -> PropertyValue.Unknown(slot.value) // Preserve raw expression
        FillMode.IMPORT -> PropertyValue.Unknown(slot.value) // Preserve raw import reference
        FillMode.VARIABLE -> PropertyValue.Unknown("@${slot.value.removePrefix("@")}")
        FillMode.LITERAL -> when (slot.type) {
            ComposerPropertyType.TEXT -> PropertyValue.Text(slot.value)
            ComposerPropertyType.NUMBER -> {
                slot.value.toDoubleOrNull()?.let { PropertyValue.Number(it) }
                    ?: PropertyValue.Unknown(slot.value)
            }
            ComposerPropertyType.PERCENT -> {
                val raw = slot.value.removeSuffix("%")
                raw.toDoubleOrNull()?.let { PropertyValue.Percent(it / 100.0) }
                    ?: PropertyValue.Unknown(slot.value)
            }
            ComposerPropertyType.BOOLEAN -> {
                PropertyValue.Boolean(slot.value.equals("true", ignoreCase = true))
            }
            ComposerPropertyType.COLOR -> {
                if (slot.value.startsWith("#")) {
                    try {
                        PropertyValue.Color(slot.value.take(7))
                    } catch (_: Exception) {
                        PropertyValue.Unknown(slot.value)
                    }
                } else {
                    PropertyValue.Unknown(slot.value)
                }
            }
            ComposerPropertyType.IMAGE -> PropertyValue.ImagePath(slot.value)
            ComposerPropertyType.FONT -> PropertyValue.FontPath(slot.value)
            ComposerPropertyType.STYLE -> PropertyValue.Unknown(slot.value)
            ComposerPropertyType.ANCHOR -> reconstructAnchor(slot)
            ComposerPropertyType.TUPLE -> reconstructTuple(slot)
        }
    }
}

/**
 * Parses a tuple string like "(Color: #000000(0.6))" back into the proper
 * [PropertyValue] by wrapping it in a minimal element and running it through
 * [UIParser]. Falls back to [PropertyValue.Unknown] if parsing fails.
 */
private fun parseTupleString(value: String): PropertyValue {
    return try {
        val wrapper = "Group { _: $value; }"
        val parser = UIParser(wrapper)
        val result = parser.parse()
        when (result) {
            is Result.Success -> result.value.root.getProperty("_") ?: PropertyValue.Unknown(value)
            is Result.Failure -> PropertyValue.Unknown(value)
        }
    } catch (_: Exception) {
        PropertyValue.Unknown(value)
    }
}

/**
 * Resolves the composer property type, overriding the schema-inferred type when
 * the actual property value provides stronger type information.
 *
 * The runtime schema discovery sometimes infers incorrect types (e.g., Anchor
 * properties typed as TUPLE or ANY) because not all instances have the same
 * parsed type. This function corrects those mismatches.
 */
private fun resolvePropertyType(
    name: String,
    schemaType: ComposerPropertyType,
    value: PropertyValue?,
): ComposerPropertyType {
    // If the actual value is an Anchor, always use ANCHOR type
    if (value is PropertyValue.Anchor) return ComposerPropertyType.ANCHOR
    // If the property is named "Anchor" and the schema didn't detect it correctly, override
    if (name == "Anchor" && schemaType != ComposerPropertyType.ANCHOR) return ComposerPropertyType.ANCHOR
    // If the actual value is a Tuple, always use TUPLE type (schema may say ANY→TEXT)
    if (value is PropertyValue.Tuple) return ComposerPropertyType.TUPLE
    return schemaType
}

/**
 * Maps the existing [PropertyType] enum to [ComposerPropertyType].
 */
internal fun PropertyType.toComposerPropertyType(): ComposerPropertyType {
    return when (this) {
        PropertyType.TEXT -> ComposerPropertyType.TEXT
        PropertyType.NUMBER -> ComposerPropertyType.NUMBER
        PropertyType.PERCENT -> ComposerPropertyType.PERCENT
        PropertyType.BOOLEAN -> ComposerPropertyType.BOOLEAN
        PropertyType.COLOR -> ComposerPropertyType.COLOR
        PropertyType.ANCHOR -> ComposerPropertyType.ANCHOR
        PropertyType.STYLE -> ComposerPropertyType.STYLE
        PropertyType.TUPLE -> ComposerPropertyType.TUPLE
        PropertyType.LIST -> ComposerPropertyType.TUPLE // Lists display as tuples in the form
        PropertyType.IMAGE_PATH -> ComposerPropertyType.IMAGE
        PropertyType.FONT_PATH -> ComposerPropertyType.FONT
        PropertyType.ANY -> ComposerPropertyType.TEXT // Fallback
    }
}

/**
 * Maps the existing [PropertyCategory] to [SlotCategory].
 */
internal fun PropertyCategory.toSlotCategory(): SlotCategory {
    return when (this) {
        PropertyCategory.LAYOUT -> SlotCategory.LAYOUT
        PropertyCategory.APPEARANCE -> SlotCategory.APPEARANCE
        PropertyCategory.TEXT -> SlotCategory.TEXT
        PropertyCategory.INTERACTION -> SlotCategory.INTERACTION
        PropertyCategory.STATE -> SlotCategory.STATE
        PropertyCategory.DATA -> SlotCategory.DATA
        PropertyCategory.SETTINGS -> SlotCategory.DATA
        PropertyCategory.OTHER -> SlotCategory.DATA
    }
}

/**
 * Infers a [SlotCategory] from a property name using the same heuristics
 * as [RuntimePropertySchema.inferCategory].
 */
internal fun inferSlotCategory(propertyName: String): SlotCategory {
    return when {
        propertyName in LAYOUT_PROPERTIES -> SlotCategory.LAYOUT
        propertyName in APPEARANCE_PROPERTIES -> SlotCategory.APPEARANCE
        propertyName in TEXT_PROPERTIES -> SlotCategory.TEXT
        propertyName in INTERACTION_PROPERTIES -> SlotCategory.INTERACTION
        propertyName in STATE_PROPERTIES -> SlotCategory.STATE
        propertyName in DATA_PROPERTIES -> SlotCategory.DATA
        else -> SlotCategory.DATA
    }
}

private val LAYOUT_PROPERTIES = setOf(
    "Anchor", "Position", "Size", "Width", "Height",
    "Left", "Top", "Right", "Bottom", "Margin", "Padding",
    "MinWidth", "MinHeight", "MaxWidth", "MaxHeight"
)

private val APPEARANCE_PROPERTIES = setOf(
    "Background", "Color", "ForegroundColor", "BackgroundColor",
    "BorderColor", "BorderWidth", "BorderRadius", "Opacity", "Alpha",
    "CornerRadius", "BackgroundHover", "BackgroundPressed", "BackgroundDisabled",
    "TrackColor", "FillColor", "HandleColor", "SelectionColor", "Icon"
)

private val TEXT_PROPERTIES = setOf(
    "Text", "FontSize", "FontFamily", "Font", "FontPath",
    "TextAlign", "TextColor", "LineHeight", "LetterSpacing",
    "RenderBold", "RenderItalic", "Truncate", "WordWrap"
)

private val INTERACTION_PROPERTIES = setOf(
    "OnClick", "OnChange", "OnHover", "OnFocus", "OnBlur",
    "OnSubmit", "OnSelect", "Enabled", "Focusable", "Clickable"
)

private val STATE_PROPERTIES = setOf(
    "Visible", "Hidden", "Style"
)

private val DATA_PROPERTIES = setOf(
    "Value", "MinValue", "MaxValue", "Step", "DefaultValue",
    "Checked", "Selected", "Items", "SelectedIndex", "Source",
    "Orientation", "LayoutMode", "ScrollDirection",
    "HandleSize", "Placeholder", "Mask"
)

/**
 * Extracts tuple field values from a Tuple property value.
 * Returns an empty map for non-tuple values.
 */
private fun extractTupleValues(value: PropertyValue?): Map<String, String> {
    if (value !is PropertyValue.Tuple) return emptyMap()
    return value.values.mapValues { (_, v) -> propertyValueToString(v) }
}

/**
 * Converts a [PropertyValue] to its string representation for tuple field display.
 * Unlike [PropertyValue.toString], this omits outer parens for nested tuples.
 */
private fun propertyValueToString(value: PropertyValue): String {
    return when (value) {
        is PropertyValue.Text -> value.value
        is PropertyValue.Boolean -> value.value.toString()
        else -> value.toString()
    }
}

/**
 * Reconstructs a [PropertyValue.Tuple] from a slot's [tupleValues] map.
 * Falls back to parsing the raw slot value if tupleValues is empty.
 */
private fun reconstructTuple(slot: PropertySlot): PropertyValue {
    if (slot.tupleValues.isEmpty()) return parseTupleString(slot.value)
    val entries = slot.tupleValues.mapValues { (_, v) -> inferPropertyValue(v) }
    return PropertyValue.Tuple(entries)
}

/**
 * Infers the [PropertyValue] type from a raw string value.
 *
 * - `true`/`false` → [PropertyValue.Boolean]
 * - Valid number → [PropertyValue.Number]
 * - Starts with `(` → parsed as nested tuple
 * - Starts with `#` → [PropertyValue.Color]
 * - Otherwise → [PropertyValue.Text]
 */
internal fun inferPropertyValue(raw: String): PropertyValue {
    val trimmed = raw.trim()
    return when {
        trimmed.equals("true", ignoreCase = true) -> PropertyValue.Boolean(true)
        trimmed.equals("false", ignoreCase = true) -> PropertyValue.Boolean(false)
        trimmed.toDoubleOrNull() != null -> PropertyValue.Number(trimmed.toDouble())
        trimmed.startsWith("(") -> parseTupleString(trimmed)
        trimmed.startsWith("#") -> {
            // Handle #RRGGBB(alpha) format, e.g. "#393b8d(0.8)"
            val colorMatch = Regex("""^(#[0-9a-fA-F]{6})\(([0-9]*\.?[0-9]+)\)$""").find(trimmed)
            if (colorMatch != null) {
                PropertyValue.Color(colorMatch.groupValues[1], colorMatch.groupValues[2].toFloatOrNull())
            } else {
                PropertyValue.Color(trimmed)
            }
        }
        trimmed.startsWith("\"") && trimmed.endsWith("\"") ->
            PropertyValue.Text(trimmed.removeSurrounding("\""))
        else -> PropertyValue.Text(trimmed)
    }
}

/**
 * Extracts anchor axis values from an Anchor property value.
 * Returns an empty map for non-anchor values.
 */
private fun extractAnchorValues(value: PropertyValue?): Map<String, String> {
    if (value !is PropertyValue.Anchor) return emptyMap()
    val anchor = value.anchor
    val result = mutableMapOf<String, String>()
    anchor.left?.let { result["left"] = it.toString() }
    anchor.top?.let { result["top"] = it.toString() }
    anchor.right?.let { result["right"] = it.toString() }
    anchor.bottom?.let { result["bottom"] = it.toString() }
    anchor.width?.let { result["width"] = it.toString() }
    anchor.height?.let { result["height"] = it.toString() }
    return result
}

/**
 * Reconstructs a [PropertyValue.Anchor] from a slot's [anchorValues] map.
 * Falls back to [PropertyValue.Unknown] if the anchor cannot be reconstructed.
 */
private fun reconstructAnchor(slot: PropertySlot): PropertyValue {
    val values = slot.anchorValues
    if (values.isEmpty()) return PropertyValue.Unknown(slot.value)

    fun parseDimension(key: String): AnchorDimension? {
        val raw = values[key] ?: return null
        if (raw.endsWith("%")) {
            val pct = raw.removeSuffix("%").toFloatOrNull() ?: return null
            return AnchorDimension.Relative(pct / 100f)
        }
        val num = raw.toFloatOrNull() ?: return null
        return AnchorDimension.Absolute(num)
    }

    val anchor = AnchorValue(
        left = parseDimension("left"),
        top = parseDimension("top"),
        right = parseDimension("right"),
        bottom = parseDimension("bottom"),
        width = parseDimension("width"),
        height = parseDimension("height")
    )
    return PropertyValue.Anchor(anchor)
}
