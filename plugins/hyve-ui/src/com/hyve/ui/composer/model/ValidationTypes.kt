// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

import com.hyve.ui.registry.ElementCapability
import com.hyve.ui.registry.ElementTypeRegistry

/**
 * Severity level for a validation problem.
 */
enum class ProblemSeverity {
    ERROR, WARNING
}

/**
 * A single validation problem found in the current element.
 *
 * @param severity Whether this is an error or warning
 * @param message Human-readable description of the problem
 * @param property The associated slot name, if any. Null for element-level problems (e.g. missing ID).
 */
data class Problem(
    val severity: ProblemSeverity,
    val message: String,
    val property: String? = null,
)

/**
 * Regex for valid hex color formats: #RGB, #RRGGBB, or #RRGGBBAA (FR-7).
 */
private val COLOR_REGEX = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

/**
 * Validates an element definition against the current word bank state.
 *
 * This is a pure function with no side effects, caching, or debouncing.
 * It is called on every recomposition. The returned list is sorted per FR-9:
 * errors before warnings, then alphabetically by property name (nulls last),
 * then by message.
 *
 * @param element The element being edited
 * @param wordBankItems The current word bank contents
 * @return Sorted list of problems, empty if no issues found
 */
fun validate(
    element: ElementDefinition,
    wordBankItems: List<WordBankItem>,
): List<Problem> {
    val problems = mutableListOf<Problem>()

    // FR-2: Missing required properties
    for (slot in element.slots) {
        if (slot.required && slot.fillMode == FillMode.EMPTY) {
            problems += Problem(
                severity = ProblemSeverity.ERROR,
                message = "Required property \"${slot.name}\" is not set",
                property = slot.name,
            )
        }
    }

    // FR-3: Missing element ID for interactive types
    val isInteractive = ElementCapability.INTERACTIVE in
            ElementTypeRegistry.getOrDefault(element.type.value).capabilities
    if (isInteractive && element.id.isBlank()) {
        problems += Problem(
            severity = ProblemSeverity.WARNING,
            message = "${element.type.value} has no #id \u2014 Java event binding requires an ID",
        )
    }

    // FR-4: Undefined variable references
    for (slot in element.slots) {
        if (slot.fillMode == FillMode.VARIABLE && slot.value.isNotBlank()) {
            val exists = wordBankItems.any {
                (it.kind == WordBankKind.VARIABLE || it.kind == WordBankKind.STYLE) &&
                    it.name == slot.value
            }
            if (!exists) {
                problems += Problem(
                    severity = ProblemSeverity.ERROR,
                    message = "Variable \"${slot.value}\" is not defined in this file or imports",
                    property = slot.name,
                )
            }
        }
    }

    // FR-5: Undefined import references
    for (slot in element.slots) {
        if (slot.fillMode == FillMode.IMPORT && slot.value.isNotBlank()) {
            val alias = extractImportAlias(slot.value)
            if (alias != null) {
                val exists = wordBankItems.any {
                    it.kind == WordBankKind.IMPORT && it.name.startsWith(alias)
                }
                if (!exists) {
                    problems += Problem(
                        severity = ProblemSeverity.ERROR,
                        message = "Import \"$alias\" not found \u2014 file does not exist",
                        property = slot.name,
                    )
                }
            }
        }
    }

    // FR-6: Undefined localization keys
    for (slot in element.slots) {
        if (slot.fillMode == FillMode.LOCALIZATION && slot.value.isNotBlank()) {
            val exists = wordBankItems.any {
                it.kind == WordBankKind.LOCALIZATION && it.name == slot.value
            }
            if (!exists) {
                problems += Problem(
                    severity = ProblemSeverity.WARNING,
                    message = "Localization key \"${slot.value}\" not found in any .lang file",
                    property = slot.name,
                )
            }
        }
    }

    // FR-7: Color format validation
    for (slot in element.slots) {
        if (slot.type == ComposerPropertyType.COLOR
            && slot.fillMode == FillMode.LITERAL
            && slot.value.isNotBlank()
        ) {
            if (!COLOR_REGEX.matches(slot.value)) {
                problems += Problem(
                    severity = ProblemSeverity.WARNING,
                    message = "\"${slot.value}\" may not be a valid color \u2014 expected #RGB, #RRGGBB, or #RRGGBBAA",
                    property = slot.name,
                )
            }
        }
    }

    // FR-8: Number format validation
    for (slot in element.slots) {
        if ((slot.type == ComposerPropertyType.NUMBER || slot.type == ComposerPropertyType.PERCENT)
            && slot.fillMode == FillMode.LITERAL
        ) {
            val trimmed = slot.value.trim()
            if (trimmed.isEmpty() || trimmed.toDoubleOrNull() == null) {
                problems += Problem(
                    severity = ProblemSeverity.WARNING,
                    message = "\"${slot.value}\" is not a valid number",
                    property = slot.name,
                )
            }
        }
    }

    // FR-9: Sort — errors first, then by property (nulls last), then by message
    return problems.sortedWith(
        compareBy<Problem> { it.severity.ordinal }
            .thenBy(nullsLast()) { it.property }
            .thenBy { it.message }
    )
}

/**
 * Extracts the `$Alias` portion from an import value string.
 *
 * For `$Common.@HeaderStyle`, returns `$Common`.
 * Returns null if the value has no `$` prefix.
 */
internal fun extractImportAlias(value: String): String? {
    if (!value.startsWith("$")) return null
    val dotIndex = value.indexOf('.')
    return if (dotIndex > 0) value.substring(0, dotIndex) else value
}
