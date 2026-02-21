// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.schema

import com.hyve.ui.core.id.PropertyName

/**
 * Shared property schemas that appear across most element types.
 *
 * Instead of copy-pasting identical PropertySchema blocks in every
 * tier file, element schemas should reference these shared definitions.
 * Only the description varies per element type; if a description needs
 * to be element-specific, use the `copy()` helper on the result.
 */
object CommonPropertySchemas {

    /** Position and size via the anchor system. */
    fun anchor(description: String = "Position and size of the element") = PropertySchema(
        name = PropertyName("Anchor"),
        type = PropertyType.ANCHOR,
        required = false,
        description = description
    )

    /** Whether the element is rendered. */
    fun visible(description: String = "Whether the element is visible") = PropertySchema(
        name = PropertyName("Visible"),
        type = PropertyType.BOOLEAN,
        required = false,
        description = description
    )

    /** Whether the element accepts interaction. */
    fun enabled(description: String = "Whether the element is enabled") = PropertySchema(
        name = PropertyName("Enabled"),
        type = PropertyType.BOOLEAN,
        required = false,
        description = description
    )

    /** Reference to a named style tuple. */
    fun style(description: String = "Reference to a style definition") = PropertySchema(
        name = PropertyName("Style"),
        type = PropertyType.STYLE,
        required = false,
        description = description
    )

    /** Inner spacing around child content. */
    fun padding(description: String = "Inner spacing (Left, Top, Right, Bottom)") = PropertySchema(
        name = PropertyName("Padding"),
        type = PropertyType.TUPLE,
        required = false,
        description = description
    )

    /** Gap between child elements in a layout. */
    fun spacing(description: String = "Gap between child elements in layout") = PropertySchema(
        name = PropertyName("Spacing"),
        type = PropertyType.NUMBER,
        required = false,
        description = description
    )

    /** Background color with optional alpha. */
    fun background(description: String = "Background color (with optional alpha)") = PropertySchema(
        name = PropertyName("Background"),
        type = PropertyType.COLOR,
        required = false,
        description = description
    )

    /** Border color. */
    fun borderColor(description: String = "Border color") = PropertySchema(
        name = PropertyName("BorderColor"),
        type = PropertyType.COLOR,
        required = false,
        description = description
    )

    /** Border width in pixels. */
    fun borderWidth(description: String = "Border width in pixels") = PropertySchema(
        name = PropertyName("BorderWidth"),
        type = PropertyType.NUMBER,
        required = false,
        description = description
    )

    /** Font size in points. */
    fun fontSize(description: String = "Font size") = PropertySchema(
        name = PropertyName("FontSize"),
        type = PropertyType.NUMBER,
        required = false,
        description = description
    )

    /** Text color. */
    fun color(description: String = "Text color") = PropertySchema(
        name = PropertyName("Color"),
        type = PropertyType.COLOR,
        required = false,
        description = description
    )
}
