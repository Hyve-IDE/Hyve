// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.codegen

import com.hyve.ui.composer.model.ComposerPropertyType
import com.hyve.ui.composer.model.ElementDefinition
import com.hyve.ui.composer.model.FillMode
import com.hyve.ui.composer.model.PropertySlot

/**
 * Pure functions for generating `.ui` file markup from an [ElementDefinition].
 *
 * The generator produces a read-only text representation of the element's current
 * state, including import declarations and the element block with all filled properties.
 * It is a pure function of the input — no caching, no side effects.
 *
 * ## Spec Reference
 * - FR-2: Import Declarations
 * - FR-3: Element Block
 * - FR-4: Value Formatting
 * - FR-5: Live Update (purity guarantee)
 */
object CodeGenerator {

    private val ANCHOR_FIELD_ORDER = listOf("left", "top", "right", "bottom", "width", "height")

    /**
     * Generates the complete `.ui` markup for the given element.
     *
     * The output includes:
     * 1. Import declarations (if any slots use import references), followed by a blank line
     * 2. The element block with type, optional ID, and indented property lines
     *
     * Empty slots are omitted. An element with no filled slots produces a minimal block.
     *
     * @param element The element definition to generate code for
     * @return The generated `.ui` markup string
     */
    fun generateUiCode(element: ElementDefinition): String {
        val sb = StringBuilder()

        // FR-2: Import declarations
        val importAliases = collectImportAliases(element)
        if (importAliases.isNotEmpty()) {
            for (alias in importAliases) {
                sb.appendLine("$alias = \".../$alias.ui\";")
            }
            sb.appendLine()
        }

        // FR-3: Element block
        sb.append(element.type.value)
        if (element.id.isNotBlank()) {
            sb.append(" #${element.id}")
        }
        sb.appendLine(" {")

        // Property lines (only non-empty slots)
        for (slot in element.slots) {
            if (slot.fillMode == FillMode.EMPTY) continue
            val formattedValue = formatSlotValue(slot)
            sb.appendLine("  ${slot.name}: $formattedValue;")
        }

        sb.append("};")

        return sb.toString()
    }

    /**
     * Collects deduplicated import aliases from all slots using import fill mode.
     *
     * For a value like `$Common.@HeaderStyle`, extracts the alias `$Common`.
     * Multiple references to the same alias produce only one import line.
     */
    internal fun collectImportAliases(element: ElementDefinition): List<String> {
        return element.slots
            .filter { it.fillMode == FillMode.IMPORT && it.value.isNotBlank() }
            .mapNotNull { slot ->
                val dotIndex = slot.value.indexOf('.')
                if (dotIndex > 0) slot.value.substring(0, dotIndex) else slot.value
            }
            .distinct()
    }

    /**
     * Formats a single slot's value according to its fill mode and type.
     *
     * Formatting rules (FR-4):
     * - Literal text → double-quoted string
     * - Literal number/color/boolean/percent/image/font/tuple → raw value
     * - Literal anchor → structured tuple from anchorValues map
     * - Variable/Import/Localization/Expression → raw reference string
     */
    internal fun formatSlotValue(slot: PropertySlot): String {
        return when (slot.fillMode) {
            FillMode.LITERAL -> formatLiteralValue(slot)
            FillMode.VARIABLE -> slot.value
            FillMode.IMPORT -> slot.value
            FillMode.LOCALIZATION -> slot.value
            FillMode.EXPRESSION -> slot.value
            FillMode.EMPTY -> ""
        }
    }

    /**
     * Formats a literal value based on the slot's property type.
     */
    private fun formatLiteralValue(slot: PropertySlot): String {
        return when (slot.type) {
            ComposerPropertyType.TEXT -> "\"${slot.value}\""
            ComposerPropertyType.ANCHOR -> formatAnchorValue(slot.anchorValues)
            ComposerPropertyType.TUPLE -> {
                if (slot.tupleValues.isNotEmpty()) formatTupleValue(slot.tupleValues)
                else slot.value
            }
            // Number, Color, Boolean, Percent, Image, Font, Style — raw value
            else -> slot.value
        }
    }

    /**
     * Formats tuple values as a structured tuple.
     *
     * Includes all fields with non-empty values. String values that contain
     * spaces and aren't numbers, booleans, colors, or nested tuples are quoted.
     *
     * Example: `(FontSize: 24, TextColor: #ffffff, RenderBold: true)`
     */
    internal fun formatTupleValue(tupleValues: Map<String, String>): String {
        if (tupleValues.isEmpty()) return "()"
        val fields = tupleValues.entries
            .filter { it.value.isNotEmpty() }
            .joinToString(", ") { (k, v) ->
                "$k: ${formatTupleFieldValue(v)}"
            }
        return if (fields.isEmpty()) "()" else "($fields)"
    }

    /**
     * Formats a single tuple field value, adding quotes for string values
     * that need them (contain spaces and aren't special types).
     */
    private fun formatTupleFieldValue(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> "\"\""
            trimmed.equals("true", ignoreCase = true) -> trimmed
            trimmed.equals("false", ignoreCase = true) -> trimmed
            trimmed.toDoubleOrNull() != null -> trimmed
            trimmed.startsWith("#") -> trimmed
            trimmed.startsWith("(") -> trimmed
            trimmed.startsWith("\"") && trimmed.endsWith("\"") -> trimmed
            trimmed.contains(" ") || trimmed.contains(",") -> "\"$trimmed\""
            else -> trimmed
        }
    }

    /**
     * Formats anchor values as a structured tuple.
     *
     * Only includes fields that have non-empty values, in canonical order:
     * Left, Top, Right, Bottom, Width, Height.
     *
     * Example: `(Left: 10, Top: 20, Width: 200, Height: 40)`
     */
    internal fun formatAnchorValue(anchorValues: Map<String, String>): String {
        val fields = ANCHOR_FIELD_ORDER
            .mapNotNull { key ->
                val value = anchorValues[key]
                if (value.isNullOrBlank()) null
                else "${key.replaceFirstChar { it.uppercase() }}: $value"
            }

        return if (fields.isEmpty()) "()"
        else "(${fields.joinToString(", ")})"
    }
}
