package com.hyve.ui.parser.variables

import com.hyve.ui.core.domain.properties.PropertyValue

/**
 * Evaluates arithmetic expressions and resolves variable references to concrete values.
 *
 * Supports:
 * - Arithmetic operations: +, -, *, /
 * - Parentheses for grouping (handled at parse time)
 * - Variable references: @VarName
 * - Cross-file references: $Alias.@VarName
 * - Tuple spread: ...@StyleName (merges properties)
 */
class ExpressionEvaluator(
    private val scope: VariableScope,
    private val warningHandler: ((String) -> Unit)? = null
) {
    // Track variables being resolved to detect circular references
    private val resolvingVariables = mutableSetOf<String>()

    /**
     * Evaluate a PropertyValue, resolving all variables and computing expressions.
     * Returns a "concrete" value with all variables replaced by their values.
     */
    fun evaluate(value: PropertyValue): PropertyValue {
        return when (value) {
            // Already concrete values - return as-is
            is PropertyValue.Text,
            is PropertyValue.Number,
            is PropertyValue.Percent,
            is PropertyValue.Boolean,
            is PropertyValue.Color,
            is PropertyValue.LocalizedText,
            is PropertyValue.ImagePath,
            is PropertyValue.FontPath,
            is PropertyValue.Unknown,
            is PropertyValue.Null -> value

            // Style reference to local variable - resolve and return
            is PropertyValue.Style -> evaluateStyleReference(value)

            // Variable reference - resolve to value
            is PropertyValue.VariableRef -> evaluateVariableRef(value)

            // Expression - evaluate left and right, apply operator
            is PropertyValue.Expression -> evaluateExpression(value)

            // Tuple - evaluate all nested values
            is PropertyValue.Tuple -> evaluateTuple(value)

            // Anchor - evaluate all dimensions (may contain expressions)
            is PropertyValue.Anchor -> evaluateAnchor(value)

            // List - evaluate all elements
            is PropertyValue.List -> evaluateList(value)

            // Spread - resolve the inner value
            is PropertyValue.Spread -> evaluateSpread(value)
        }
    }

    /**
     * Evaluate a style reference. If it's a local reference, resolve it.
     */
    private fun evaluateStyleReference(style: PropertyValue.Style): PropertyValue {
        return when (val ref = style.reference) {
            is com.hyve.ui.core.domain.styles.StyleReference.Local -> {
                // Look up the style as a variable
                val styleName = ref.name.value
                val resolved = resolveLocalVariable(styleName)
                if (resolved == null) {
                    warn("Unresolved variable: @$styleName")
                }
                resolved ?: style
            }
            is com.hyve.ui.core.domain.styles.StyleReference.Imported -> {
                // Look up in imported scope
                val alias = ref.alias.value.removePrefix("$")
                val styleName = ref.name.value
                resolveImportedVariable(alias, styleName) ?: style
            }
            is com.hyve.ui.core.domain.styles.StyleReference.Spread -> {
                // Resolve the inner reference so tuples can merge spread properties
                evaluateStyleReference(PropertyValue.Style(ref.reference))
            }
            is com.hyve.ui.core.domain.styles.StyleReference.Inline -> {
                // Inline styles have properties already - evaluate them
                val evaluatedProps = ref.properties.mapValues { (_, v) -> evaluate(v) }
                PropertyValue.Tuple(evaluatedProps.map { it.key.value to it.value }.toMap())
            }
        }
    }

    /**
     * Evaluate a variable reference
     */
    private fun evaluateVariableRef(ref: PropertyValue.VariableRef): PropertyValue {
        val alias = ref.alias

        // Check for path (e.g., $InGame.@DefaultItemSlotSize)
        if (ref.path.isNotEmpty()) {
            val pathPart = ref.path.first()
            if (pathPart.startsWith("@")) {
                val varName = pathPart.removePrefix("@")
                return resolveImportedVariable(alias, varName) ?: ref
            }
        }

        // Simple alias reference - shouldn't happen in valid syntax
        warn("Unresolved variable reference: \$$alias")
        return ref
    }

    /**
     * Resolve a local variable by name
     */
    private fun resolveLocalVariable(name: String): PropertyValue? {
        // Check for circular reference
        val key = "@$name"
        if (key in resolvingVariables) {
            warn("Circular reference detected for @$name")
            return null
        }

        val value = scope.getVariable(name)
        if (value == null) {
            warn("Variable not found: @$name")
            return null
        }

        // Recursively evaluate if the value is an expression or reference
        resolvingVariables.add(key)
        try {
            return evaluate(value)
        } finally {
            resolvingVariables.remove(key)
        }
    }

    /**
     * Resolve an imported variable
     */
    private fun resolveImportedVariable(alias: String, varName: String): PropertyValue? {
        // Check for circular reference
        val key = "\$$alias.@$varName"
        if (key in resolvingVariables) {
            warn("Circular reference detected for $key")
            return null
        }

        val value = scope.resolveImportedVariable(alias, varName)
        if (value == null) {
            warn("Could not resolve $key")
            return null
        }

        // Recursively evaluate in case the value contains expressions
        resolvingVariables.add(key)
        try {
            // Need to evaluate in the imported scope's context
            val importedScope = scope.getResolvedImport(alias)
            if (importedScope != null) {
                val importedEvaluator = ExpressionEvaluator(importedScope, warningHandler)
                return importedEvaluator.evaluate(value)
            }
            return evaluate(value)
        } finally {
            resolvingVariables.remove(key)
        }
    }

    /**
     * Evaluate an arithmetic expression
     */
    private fun evaluateExpression(expr: PropertyValue.Expression): PropertyValue {
        val leftVal = evaluate(expr.left)
        val rightVal = evaluate(expr.right)

        // Try to get numeric values
        val leftNum = getNumericValue(leftVal)
        val rightNum = getNumericValue(rightVal)

        if (leftNum == null || rightNum == null) {
            // Can't evaluate - one or both operands are not numeric
            // Return the expression with evaluated operands
            return PropertyValue.Expression(leftVal, expr.operator, rightVal)
        }

        val result = when (expr.operator) {
            "+" -> leftNum + rightNum
            "-" -> leftNum - rightNum
            "*" -> leftNum * rightNum
            "/" -> {
                if (rightNum == 0.0) {
                    warn("Division by zero in expression")
                    0.0
                } else {
                    leftNum / rightNum
                }
            }
            else -> {
                warn("Unknown operator: ${expr.operator}")
                return PropertyValue.Expression(leftVal, expr.operator, rightVal)
            }
        }

        return PropertyValue.Number(result)
    }

    /**
     * Get the numeric value from a PropertyValue, or null if not numeric
     */
    private fun getNumericValue(value: PropertyValue): Double? {
        return when (value) {
            is PropertyValue.Number -> value.value
            is PropertyValue.Percent -> value.ratio * 100.0 // Convert to percentage for calculations
            is PropertyValue.Expression -> getNumericValue(evaluate(value))
            is PropertyValue.Style -> {
                // Try to resolve style to a numeric value
                val resolved = evaluateStyleReference(value)
                if (resolved !== value) getNumericValue(resolved) else null
            }
            is PropertyValue.VariableRef -> {
                val resolved = evaluateVariableRef(value)
                if (resolved !== value) getNumericValue(resolved) else null
            }
            else -> null
        }
    }

    /**
     * Evaluate a tuple, resolving all nested values and handling spreads
     */
    private fun evaluateTuple(tuple: PropertyValue.Tuple): PropertyValue {
        val evaluatedEntries = mutableMapOf<String, PropertyValue>()

        for ((key, value) in tuple.values) {
            if (key.startsWith("_spread_")) {
                // Handle spread - merge properties from the spread source
                val spreadValue = evaluate(value)
                when (spreadValue) {
                    is PropertyValue.Tuple -> evaluatedEntries.putAll(spreadValue.values)
                    is PropertyValue.Spread -> {
                        val inner = evaluate(spreadValue.value)
                        if (inner is PropertyValue.Tuple) {
                            evaluatedEntries.putAll(inner.values)
                        }
                    }
                    else -> {
                        // Can't spread non-tuple, keep as-is
                        evaluatedEntries[key] = spreadValue
                    }
                }
            } else {
                evaluatedEntries[key] = evaluate(value)
            }
        }

        return PropertyValue.Tuple(evaluatedEntries)
    }

    /**
     * Evaluate an anchor, resolving any expression dimensions
     */
    private fun evaluateAnchor(anchor: PropertyValue.Anchor): PropertyValue {
        // Anchor dimensions don't contain expressions in the AnchorDimension type,
        // but the anchor itself is already a concrete value after parsing.
        // If we need to support expressions in anchor tuples, we'd need to parse
        // them differently and evaluate here.
        return anchor
    }

    /**
     * Evaluate a list, resolving all elements
     */
    private fun evaluateList(list: PropertyValue.List): PropertyValue {
        return PropertyValue.List(list.values.map { evaluate(it) })
    }

    /**
     * Evaluate a spread value
     */
    private fun evaluateSpread(spread: PropertyValue.Spread): PropertyValue {
        val inner = evaluate(spread.value)
        return PropertyValue.Spread(inner)
    }

    private fun warn(message: String) {
        warningHandler?.invoke(message)
    }

    companion object {
        /**
         * Convenience method to evaluate a value with a scope
         */
        fun evaluate(value: PropertyValue, scope: VariableScope, warningHandler: ((String) -> Unit)? = null): PropertyValue {
            return ExpressionEvaluator(scope, warningHandler).evaluate(value)
        }

        /**
         * Check if a value contains unresolved variable references
         */
        fun containsVariables(value: PropertyValue): Boolean {
            return when (value) {
                is PropertyValue.VariableRef -> true
                is PropertyValue.Style -> {
                    when (value.reference) {
                        is com.hyve.ui.core.domain.styles.StyleReference.Local -> true
                        is com.hyve.ui.core.domain.styles.StyleReference.Imported -> true
                        else -> false
                    }
                }
                is PropertyValue.Expression -> containsVariables(value.left) || containsVariables(value.right)
                is PropertyValue.Tuple -> value.values.values.any { containsVariables(it) }
                is PropertyValue.List -> value.values.any { containsVariables(it) }
                is PropertyValue.Spread -> containsVariables(value.value)
                else -> false
            }
        }
    }
}
