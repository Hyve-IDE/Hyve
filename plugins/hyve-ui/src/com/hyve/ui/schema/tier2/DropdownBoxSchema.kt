package com.hyve.ui.schema.tier2

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for DropdownBox element - dropdown selection control.
 *
 * DropdownBox elements allow users to select a single option from a
 * dropdown list. They display the currently selected value and expand
 * to show all available options when clicked.
 *
 * Common properties:
 * - Items: List of available options
 * - SelectedIndex: Index of currently selected item
 * - OnChange: Event triggered when selection changes
 */
object DropdownBoxSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("DropdownBox"),
        category = ElementCategory.INPUT,
        description = "Dropdown selection control for choosing from a list of options",
        canHaveChildren = false,
        properties = listOf(
            // Content
            PropertySchema(
                name = PropertyName("Items"),
                type = PropertyType.LIST,
                required = false,
                description = "List of available options"
            ),
            PropertySchema(
                name = PropertyName("SelectedIndex"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Index of currently selected item"
            ),
            PropertySchema(
                name = PropertyName("SelectedValue"),
                type = PropertyType.TEXT,
                required = false,
                description = "Value of currently selected item"
            ),
            PropertySchema(
                name = PropertyName("Placeholder"),
                type = PropertyType.TEXT,
                required = false,
                description = "Text shown when no item is selected"
            ),

            // Event handling
            PropertySchema(
                name = PropertyName("OnChange"),
                type = PropertyType.TEXT,
                required = false,
                description = "Event triggered when selection changes"
            ),
            PropertySchema(
                name = PropertyName("OnOpen"),
                type = PropertyType.TEXT,
                required = false,
                description = "Event triggered when dropdown opens"
            ),
            PropertySchema(
                name = PropertyName("OnClose"),
                type = PropertyType.TEXT,
                required = false,
                description = "Event triggered when dropdown closes"
            ),

            // Visual styling - Main box
            CommonPropertySchemas.background("Background color of the dropdown"),
            CommonPropertySchemas.borderColor(),
            CommonPropertySchemas.borderWidth(),
            PropertySchema(
                name = PropertyName("CornerRadius"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Corner radius for rounded corners"
            ),

            // Text styling
            CommonPropertySchemas.fontSize("Size of the text"),
            CommonPropertySchemas.color(),

            // Dropdown list styling
            PropertySchema(
                name = PropertyName("DropdownBackground"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color of the dropdown list"
            ),
            PropertySchema(
                name = PropertyName("DropdownMaxHeight"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Maximum height of the dropdown list"
            ),
            PropertySchema(
                name = PropertyName("ItemHoverColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color of hovered item"
            ),
            PropertySchema(
                name = PropertyName("ItemSelectedColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color of selected item"
            ),

            // Layout
            CommonPropertySchemas.anchor("Position and size of the dropdown"),
            CommonPropertySchemas.padding("Inner padding around text"),

            // State
            CommonPropertySchemas.enabled("Whether the dropdown is interactive"),
            CommonPropertySchemas.visible(),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            DropdownBox #DifficultySelector {
                Items: ["Easy", "Normal", "Hard", "Expert"];
                SelectedIndex: 1;
                Placeholder: "Select difficulty";
                Anchor: (Left: 10, Top: 10, Width: 200, Height: 30);
                OnChange: "difficulty_changed";
            }
            """.trimIndent()
        )
    )
}
