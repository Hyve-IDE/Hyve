package com.hyve.ui.core.domain

import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.styles.StyleDefinition
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ImportAlias
import com.hyve.ui.core.id.StyleName

/**
 * Complete representation of a .ui file.
 *
 * Structure:
 * - imports: File imports ($Common = "path/to/Common.ui")
 * - styles: Style definitions (@MyStyle = (...))
 * - root: Root UI element with children
 * - comments: Preserved comments for round-trip safety
 *
 * Example .ui file:
 * ```
 * $Common = "../../Common.ui";
 *
 * @HeaderStyle = (FontSize: 24, RenderBold: true);
 *
 * Group #MainContainer {
 *   LayoutMode: Top;
 *   Anchor: (Left: 0, Top: 0, Width: 800, Height: 600);
 *
 *   Label {
 *     Text: "Welcome";
 *     Style: @HeaderStyle;
 *   }
 * }
 * ```
 */
data class UIDocument(
    val imports: Map<ImportAlias, String>,  // Alias -> file path
    val styles: Map<StyleName, StyleDefinition>,
    val root: UIElement,
    val comments: List<Comment> = emptyList()
) {
    /**
     * Get import path by alias
     */
    fun getImport(alias: ImportAlias): String? = imports[alias]

    /**
     * Get style definition by name
     */
    fun getStyle(name: StyleName): StyleDefinition? = styles[name]

    /**
     * Add import (returns new UIDocument, immutable)
     */
    fun addImport(alias: ImportAlias, path: String): UIDocument =
        copy(imports = imports + (alias to path))

    /**
     * Add style definition (returns new UIDocument, immutable)
     */
    fun addStyle(style: StyleDefinition): UIDocument =
        copy(styles = styles + (style.name to style))

    /**
     * Update root element (returns new UIDocument, immutable)
     */
    fun updateRoot(newRoot: UIElement): UIDocument =
        copy(root = newRoot)

    /**
     * Find element by ID anywhere in document
     */
    fun findElementById(id: ElementId): UIElement? =
        root.findDescendantById(id)

    /**
     * Get all element IDs in document (for validation)
     */
    fun getAllElementIds(): Set<ElementId> {
        val ids = mutableSetOf<ElementId>()
        root.visitDescendants { element ->
            element.id?.let { ids.add(it) }
        }
        return ids
    }


    /**
     * Validate document (check for broken references, etc.)
     * Returns list of validation errors
     */
    fun validate(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Check for duplicate element IDs
        val idCounts = mutableMapOf<ElementId, Int>()
        root.visitDescendants { element ->
            element.id?.let { id ->
                idCounts[id] = (idCounts[id] ?: 0) + 1
            }
        }
        idCounts.forEach { (id, count) ->
            if (count > 1) {
                errors.add(ValidationError.DuplicateElementId(id, count))
            }
        }

        // TODO: Add more validation rules
        // - Check for broken style references
        // - Check for circular style dependencies
        // - Check for invalid property values
        // - Check for missing required properties

        return errors
    }

    companion object {
        /**
         * Create empty document
         */
        fun empty(): UIDocument = UIDocument(
            imports = emptyMap(),
            styles = emptyMap(),
            root = UIElement.root()
        )
    }
}

/**
 * Represents a comment in a .ui file.
 * Comments are preserved for round-trip safety.
 */
data class Comment(
    val text: String,
    val position: CommentPosition
)

/**
 * Position of a comment in the file
 */
sealed class CommentPosition {
    /**
     * Comment before an element
     */
    data class BeforeElement(val elementId: ElementId) : CommentPosition()

    /**
     * Comment before a style definition
     */
    data class BeforeStyle(val styleName: StyleName) : CommentPosition()

    /**
     * Comment at the top of the file
     */
    data object FileHeader : CommentPosition()

    /**
     * Comment at the end of the file
     */
    data object FileFooter : CommentPosition()

    /**
     * Comment inside an element body (standalone comment line between properties/children).
     * Stores the insertion index to reconstruct placement during export.
     */
    data class InElement(
        val parentElementId: ElementId?,
        val insertionIndex: Int
    ) : CommentPosition()
}

/**
 * Validation errors found in a UIDocument
 */
sealed class ValidationError {
    abstract val message: String

    data class DuplicateElementId(val id: ElementId, val count: Int) : ValidationError() {
        override val message: String = "Duplicate element ID '$id' found $count times"
    }

    data class BrokenStyleReference(val styleName: StyleName, val elementId: ElementId?) : ValidationError() {
        override val message: String = if (elementId != null) {
            "Style '${styleName.value}' not found (referenced by element '${elementId.value}')"
        } else {
            "Style '${styleName.value}' not found"
        }
    }

    data class CircularStyleDependency(val cycle: List<StyleName>) : ValidationError() {
        override val message: String = "Circular style dependency detected: ${cycle.joinToString(" -> ") { it.value }}"
    }

    data class InvalidPropertyValue(
        val elementId: ElementId?,
        val propertyName: String,
        val reason: String
    ) : ValidationError() {
        override val message: String = if (elementId != null) {
            "Invalid property '$propertyName' in element '${elementId.value}': $reason"
        } else {
            "Invalid property '$propertyName': $reason"
        }
    }

    data class MissingRequiredProperty(
        val elementId: ElementId?,
        val propertyName: String
    ) : ValidationError() {
        override val message: String = if (elementId != null) {
            "Required property '$propertyName' missing in element '${elementId.value}'"
        } else {
            "Required property '$propertyName' missing"
        }
    }

    data class UnknownProperty(
        val elementId: ElementId?,
        val propertyName: String
    ) : ValidationError() {
        override val message: String = if (elementId != null) {
            "Unknown property '$propertyName' in element '${elementId.value}'"
        } else {
            "Unknown property '$propertyName'"
        }
    }
}
