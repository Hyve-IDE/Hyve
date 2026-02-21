package com.hyve.ui.core.domain.styles

import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ImportAlias
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.id.StyleName

/**
 * Sealed hierarchy representing style references in .ui files.
 * Styles can be local, imported, spread, or inline.
 */
sealed class StyleReference {
    /**
     * Local style reference
     * Example: Style: @MyStyle
     */
    data class Local(val name: StyleName) : StyleReference() {
        override fun toString(): String = "@${name.value}"
    }

    /**
     * Imported style reference
     * Example: Style: $Common.@HeaderStyle
     */
    data class Imported(
        val alias: ImportAlias,
        val name: StyleName
    ) : StyleReference() {
        override fun toString(): String = "${alias.value}.@${name.value}"
    }

    /**
     * Spread style reference (applies all properties from referenced style)
     * Example: Style: ...@BaseStyle
     */
    data class Spread(val reference: StyleReference) : StyleReference() {
        override fun toString(): String = "...$reference"
    }

    /**
     * Inline style definition (properties defined directly)
     * Example: Style: (FontSize: 14, RenderBold: true)
     */
    data class Inline(val properties: Map<PropertyName, PropertyValue>) : StyleReference() {
        override fun toString(): String {
            val props = properties.entries.joinToString(", ") { (k, v) -> "${k.value}: $v" }
            return "($props)"
        }
    }
}

/**
 * Represents a style definition in a .ui file
 *
 * Examples:
 * - Tuple style: @MyStyle = (FontSize: 14, RenderBold: true);
 * - Type constructor: @PopupMenuLayerStyle = PopupMenuLayerStyle(Background: #2e2e2e, ...);
 * - Element-based: @FooterButton = TextButton { Style: (...); };
 *
 * @param name The style name (without @ prefix)
 * @param properties The style properties
 * @param typeName Optional type constructor name (e.g., "PopupMenuLayerStyle")
 * @param elementType Optional element type for element-based styles (e.g., "TextButton")
 * @param sourceFile If imported from another file
 */
data class StyleDefinition(
    val name: StyleName,
    val properties: Map<PropertyName, PropertyValue>,
    val typeName: String? = null,
    val elementType: String? = null,
    val sourceFile: ImportAlias? = null
) {
    override fun toString(): String {
        val prefix = if (sourceFile != null) "${sourceFile.value}." else ""
        val props = properties.entries.joinToString(", ") { (k, v) -> "${k.value}: $v" }

        return when {
            elementType != null -> "$prefix@${name.value} = $elementType { $props }"
            typeName != null -> "$prefix@${name.value} = $typeName($props)"
            else -> "$prefix@${name.value} = ($props)"
        }
    }
}
