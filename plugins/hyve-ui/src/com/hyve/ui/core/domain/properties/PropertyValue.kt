package com.hyve.ui.core.domain.properties

import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.styles.StyleReference

/**
 * Sealed hierarchy representing all possible property value types in .ui files.
 * Extensible as schema discovery progresses - add new types here.
 *
 * Unknown property values are preserved to ensure round-trip safety.
 */
sealed class PropertyValue {
    /**
     * Text/string property value
     * Example: Text: "Hello World", LayoutMode: Top
     *
     * @param quoted true if the value was originally a quoted string literal,
     *               false if it was an unquoted identifier/enum (e.g. Top, Center, true).
     *               Defaults to true for backwards compatibility.
     */
    data class Text(val value: String, val quoted: kotlin.Boolean = true) : PropertyValue() {
        override fun toString(): String = if (quoted) "\"$value\"" else value
    }

    /**
     * Numeric property value (int or decimal)
     * Example: FontSize: 14, Opacity: 0.5
     */
    data class Number(val value: Double) : PropertyValue() {
        override fun toString(): String = if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }

    /**
     * Percentage property value (stored as ratio 0.0-1.0)
     * Example: Width: 100%, Height: 50%
     */
    data class Percent(val ratio: Double) : PropertyValue() {
        init {
            require(ratio in 0.0..1.0) { "Percent ratio must be between 0.0 and 1.0 (got $ratio)" }
        }

        /** Get the percentage as a whole number (0-100) */
        val percentage: Double get() = ratio * 100.0

        override fun toString(): String {
            val pct = ratio * 100.0
            return if (pct % 1.0 == 0.0) {
                "${pct.toInt()}%"
            } else {
                "$pct%"
            }
        }
    }

    /**
     * Boolean property value
     * Example: RenderBold: true
     */
    data class Boolean(val value: kotlin.Boolean) : PropertyValue() {
        override fun toString(): String = value.toString()
    }

    /**
     * Color property value with optional alpha
     * Example: Background: #ffffff(0.5), Color: #ff0000
     */
    data class Color(
        val hex: String,
        val alpha: Float? = null
    ) : PropertyValue() {
        init {
            require(hex.matches(Regex("^#[0-9a-fA-F]{6}$"))) {
                "Invalid hex color format: $hex (expected #RRGGBB)"
            }
            alpha?.let {
                require(it in 0.0f..1.0f) { "Alpha must be between 0.0 and 1.0" }
            }
        }

        override fun toString(): String = if (alpha != null) {
            "$hex($alpha)"
        } else {
            hex
        }
    }

    /**
     * Anchor property value (layout positioning)
     * Example: Anchor: (Left: 10, Top: 5, Width: 100, Height: 50)
     */
    data class Anchor(val anchor: AnchorValue) : PropertyValue() {
        override fun toString(): String = anchor.toString()
    }

    /**
     * Style reference property value
     * Example: Style: @MyStyle, Style: $Common.@HeaderStyle, Style: ...@BaseStyle
     */
    data class Style(val reference: StyleReference) : PropertyValue() {
        override fun toString(): String = reference.toString()
    }

    /**
     * Tuple property value (key-value pairs)
     * Example: Position: (X: 10, Y: 20), Size: (Width: 100, Height: 50)
     */
    data class Tuple(val values: Map<String, PropertyValue>) : PropertyValue() {
        override fun toString(): String {
            val entries = values.entries.joinToString(", ") { (k, v) -> "$k: $v" }
            return "($entries)"
        }
    }

    /**
     * List property value
     * Example: Items: ["One", "Two", "Three"]
     */
    data class List(val values: kotlin.collections.List<PropertyValue>) : PropertyValue() {
        override fun toString(): String {
            val items = values.joinToString(", ") { it.toString() }
            return "[$items]"
        }
    }

    /**
     * Image path property value
     * Example: Source: "textures/icons/sword.png"
     */
    data class ImagePath(val path: String) : PropertyValue() {
        override fun toString(): String = "\"$path\""
    }

    /**
     * Font path property value
     * Example: Font: "fonts/arial.ttf"
     */
    data class FontPath(val path: String) : PropertyValue() {
        override fun toString(): String = "\"$path\""
    }

    /**
     * Localized text property value (references a localization key)
     * Example: Text: %client.assetEditor.mode.editor
     */
    data class LocalizedText(val key: String) : PropertyValue() {
        override fun toString(): String = "%$key"
    }

    /**
     * Variable reference with optional path chain
     * Example: $AssetEditor.@DropdownBoxStyle, $AssetEditor.@ContextPaneWidth
     *
     * @param alias The import alias (e.g., "AssetEditor" from "$AssetEditor")
     * @param path Optional chain of accessors (e.g., ["@DropdownBoxStyle"])
     */
    data class VariableRef(
        val alias: String,
        val path: kotlin.collections.List<String> = emptyList()
    ) : PropertyValue() {
        override fun toString(): String {
            val base = "$$alias"
            return if (path.isEmpty()) base else base + path.joinToString("") { ".$it" }
        }
    }

    /**
     * Spread value - spread operator applied to another value
     * Example: ...@StyleState inside a tuple
     */
    data class Spread(val value: PropertyValue) : PropertyValue() {
        override fun toString(): String = "...$value"
    }

    /**
     * Arithmetic expression property value
     * Example: @Value * 1000, 74 * 3, @Min + 10
     *
     * @param left The left-hand operand
     * @param operator The arithmetic operator (+, -, *, /)
     * @param right The right-hand operand
     */
    data class Expression(
        val left: PropertyValue,
        val operator: String,
        val right: PropertyValue
    ) : PropertyValue() {
        override fun toString(): String = "$left $operator $right"
    }

    /**
     * Unknown property value - preserves raw text for round-trip safety
     * Used when parser encounters a property value it doesn't recognize yet
     */
    data class Unknown(val raw: String) : PropertyValue() {
        override fun toString(): String = raw
    }

    /**
     * Null/absent property value
     * Used when a property is explicitly set to null or is absent
     */
    data object Null : PropertyValue() {
        override fun toString(): String = "null"
    }

    /**
     * Accept a [PropertyValueVisitor], dispatching to the appropriate visit method.
     * Use for exhaustive operations that handle all subtypes (export, evaluation).
     * For targeted checks on 1-3 types, prefer the extension functions below instead.
     */
    fun <T> accept(visitor: PropertyValueVisitor<T>): T = when (this) {
        is Text -> visitor.visitText(this)
        is Number -> visitor.visitNumber(this)
        is Percent -> visitor.visitPercent(this)
        is Boolean -> visitor.visitBoolean(this)
        is Color -> visitor.visitColor(this)
        is Anchor -> visitor.visitAnchor(this)
        is Style -> visitor.visitStyle(this)
        is Tuple -> visitor.visitTuple(this)
        is List -> visitor.visitList(this)
        is ImagePath -> visitor.visitImagePath(this)
        is FontPath -> visitor.visitFontPath(this)
        is LocalizedText -> visitor.visitLocalizedText(this)
        is VariableRef -> visitor.visitVariableRef(this)
        is Spread -> visitor.visitSpread(this)
        is Expression -> visitor.visitExpression(this)
        is Unknown -> visitor.visitUnknown(this)
        is Null -> visitor.visitNull(this)
    }
}

/**
 * Visitor interface for exhaustive dispatch over [PropertyValue] subtypes.
 *
 * Implement this when you need to handle ALL property value types in a single
 * operation (e.g., export formatting, expression evaluation, deep copying).
 * Adding a new [PropertyValue] subtype will cause a compile error in all visitors.
 *
 * For targeted checks on 1-3 types, use the extension functions instead:
 * [PropertyValue.textValue], [PropertyValue.numberValue], etc.
 */
interface PropertyValueVisitor<out T> {
    fun visitText(value: PropertyValue.Text): T
    fun visitNumber(value: PropertyValue.Number): T
    fun visitPercent(value: PropertyValue.Percent): T
    fun visitBoolean(value: PropertyValue.Boolean): T
    fun visitColor(value: PropertyValue.Color): T
    fun visitAnchor(value: PropertyValue.Anchor): T
    fun visitStyle(value: PropertyValue.Style): T
    fun visitTuple(value: PropertyValue.Tuple): T
    fun visitList(value: PropertyValue.List): T
    fun visitImagePath(value: PropertyValue.ImagePath): T
    fun visitFontPath(value: PropertyValue.FontPath): T
    fun visitLocalizedText(value: PropertyValue.LocalizedText): T
    fun visitVariableRef(value: PropertyValue.VariableRef): T
    fun visitSpread(value: PropertyValue.Spread): T
    fun visitExpression(value: PropertyValue.Expression): T
    fun visitUnknown(value: PropertyValue.Unknown): T
    fun visitNull(value: PropertyValue.Null): T
}

// ── Convenience accessor extensions ─────────────────────────────────
// Use these for targeted property reads instead of `is PropertyValue.X` checks.

/** Returns the string value if this is [PropertyValue.Text], null otherwise. */
fun PropertyValue.textValue(): String? = (this as? PropertyValue.Text)?.value

/** Returns the numeric value if this is [PropertyValue.Number], null otherwise. */
fun PropertyValue.numberValue(): Double? = (this as? PropertyValue.Number)?.value

/** Returns the boolean value if this is [PropertyValue.Boolean], null otherwise. */
fun PropertyValue.booleanValue(): kotlin.Boolean? = (this as? PropertyValue.Boolean)?.value

/** Returns the image path string if this is [PropertyValue.ImagePath], null otherwise. */
fun PropertyValue.imagePathValue(): String? = (this as? PropertyValue.ImagePath)?.path

/** Returns the font path string if this is [PropertyValue.FontPath], null otherwise. */
fun PropertyValue.fontPathValue(): String? = (this as? PropertyValue.FontPath)?.path

/** Returns the [AnchorValue] if this is [PropertyValue.Anchor], null otherwise. */
fun PropertyValue.anchorValue(): AnchorValue? = (this as? PropertyValue.Anchor)?.anchor

/** Returns the [StyleReference] if this is [PropertyValue.Style], null otherwise. */
fun PropertyValue.styleValue(): StyleReference? = (this as? PropertyValue.Style)?.reference

/** Returns the tuple map if this is [PropertyValue.Tuple], null otherwise. */
fun PropertyValue.tupleValues(): Map<String, PropertyValue>? = (this as? PropertyValue.Tuple)?.values

/** Returns the list items if this is [PropertyValue.List], null otherwise. */
fun PropertyValue.listValues(): kotlin.collections.List<PropertyValue>? = (this as? PropertyValue.List)?.values

/** Returns the color hex string if this is [PropertyValue.Color], null otherwise. */
fun PropertyValue.colorHex(): String? = (this as? PropertyValue.Color)?.hex
