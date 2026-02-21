package com.hyve.ui.core.id

/**
 * Type-safe wrapper for element IDs.
 * Elements can be unnamed (null ID) or have unique identifiers.
 */
@JvmInline
value class ElementId(val value: String) {
    init {
        require(value.isNotBlank()) { "ElementId cannot be blank" }
    }

    override fun toString(): String = value
}

/**
 * Type-safe wrapper for element types (Group, Label, Button, etc.)
 */
@JvmInline
value class ElementType(val value: String) {
    init {
        require(value.isNotBlank()) { "ElementType cannot be blank" }
    }

    override fun toString(): String = value
}

/**
 * Type-safe wrapper for property names (Text, Anchor, Background, etc.)
 */
@JvmInline
value class PropertyName(val value: String) {
    init {
        require(value.isNotBlank()) { "PropertyName cannot be blank" }
    }

    override fun toString(): String = value
}

/**
 * Type-safe wrapper for style names (@MyStyle, @HeaderStyle, etc.)
 */
@JvmInline
value class StyleName(val value: String) {
    init {
        require(value.isNotBlank()) { "StyleName cannot be blank" }
    }

    override fun toString(): String = value
}

/**
 * Type-safe wrapper for import aliases ($Common, $Shared, etc.)
 */
@JvmInline
value class ImportAlias(val value: String) {
    init {
        require(value.isNotBlank()) { "ImportAlias cannot be blank" }
        require(value.startsWith("$")) { "ImportAlias must start with $" }
        require(value.length > 1) { "ImportAlias must have a name after $" }
    }

    override fun toString(): String = value
}
