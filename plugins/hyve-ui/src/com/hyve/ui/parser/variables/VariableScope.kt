package com.hyve.ui.parser.variables

import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.styles.StyleDefinition
import com.hyve.ui.core.id.ImportAlias

/**
 * Represents a scope containing variable definitions and file imports.
 * Supports nested scopes for local overrides within elements.
 *
 * Variables are named with @ prefix: @DefaultItemSlotSize = 64
 * Imports are named with $ prefix: $InGame = "../Common.ui"
 */
class VariableScope(
    private val parent: VariableScope? = null,
    private val name: String = "root"
) {
    // Local variables defined in this scope (without @ prefix in key)
    private val variables = mutableMapOf<String, PropertyValue>()

    // Template definitions (element-based styles like @Container = Group { ... })
    private val templateDefinitions = mutableMapOf<String, StyleDefinition>()

    // File imports defined in this scope (without $ prefix in key)
    private val imports = mutableMapOf<String, String>()

    // Cached resolved import scopes (populated by ImportResolver)
    private val resolvedImports = mutableMapOf<String, VariableScope>()

    /**
     * Define a local variable: @VarName = value
     */
    fun defineVariable(name: String, value: PropertyValue) {
        variables[name] = value
    }

    /**
     * Define a file import: $Alias = "path/to/file.ui"
     */
    fun defineImport(alias: String, path: String) {
        imports[alias] = path
    }

    /**
     * Set the resolved scope for an import alias
     */
    fun setResolvedImport(alias: String, scope: VariableScope) {
        resolvedImports[alias] = scope
    }

    /**
     * Get the resolved scope for an import alias
     */
    fun getResolvedImport(alias: String): VariableScope? {
        return resolvedImports[alias] ?: parent?.getResolvedImport(alias)
    }

    /**
     * Define an element-based template: @Container = Group { ... }
     */
    fun defineTemplate(name: String, definition: StyleDefinition) {
        templateDefinitions[name] = definition
    }

    /**
     * Look up a template by name (searches parent scopes)
     */
    fun getTemplate(name: String): StyleDefinition? {
        return templateDefinitions[name] ?: parent?.getTemplate(name)
    }

    /**
     * Resolve a template from an imported file: $Alias.@TemplateName
     */
    fun resolveImportedTemplate(alias: String, templateName: String): StyleDefinition? {
        return getResolvedImport(alias)?.getTemplate(templateName)
    }

    /**
     * Look up a local variable by name (searches parent scopes)
     * @param name Variable name without @ prefix
     */
    fun getVariable(name: String): PropertyValue? {
        return variables[name] ?: parent?.getVariable(name)
    }

    /**
     * Look up an import path by alias
     * @param alias Import alias without $ prefix
     */
    fun getImportPath(alias: String): String? {
        return imports[alias] ?: parent?.getImportPath(alias)
    }

    /**
     * Resolve a cross-file variable reference: $Alias.@VarName
     * @param alias Import alias without $ prefix
     * @param varName Variable name without @ prefix
     * @return The resolved value or null if not found
     */
    fun resolveImportedVariable(alias: String, varName: String): PropertyValue? {
        val importedScope = getResolvedImport(alias)
        return importedScope?.getVariable(varName)
    }

    /**
     * Check if a variable is defined in this scope or any parent
     */
    fun hasVariable(name: String): Boolean {
        return variables.containsKey(name) || (parent?.hasVariable(name) ?: false)
    }

    /**
     * Check if an import is defined in this scope or any parent
     */
    fun hasImport(alias: String): Boolean {
        return imports.containsKey(alias) || (parent?.hasImport(alias) ?: false)
    }

    /**
     * Get all variables in this scope (not including parents)
     */
    fun getLocalVariables(): Map<String, PropertyValue> = variables.toMap()

    /**
     * Get all imports in this scope (not including parents)
     */
    fun getLocalImports(): Map<String, String> = imports.toMap()

    /**
     * Get all variables including parent scopes (child overrides parent)
     */
    fun getAllVariables(): Map<String, PropertyValue> {
        val result = mutableMapOf<String, PropertyValue>()
        parent?.getAllVariables()?.let { result.putAll(it) }
        result.putAll(variables)
        return result
    }

    /**
     * Get all imports including parent scopes (child overrides parent)
     */
    fun getAllImports(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        parent?.getAllImports()?.let { result.putAll(it) }
        result.putAll(imports)
        return result
    }

    /**
     * Create a child scope for local variable definitions
     */
    fun createChildScope(name: String = "child"): VariableScope {
        return VariableScope(parent = this, name = name)
    }

    override fun toString(): String {
        return "VariableScope($name, vars=${variables.keys}, imports=${imports.keys})"
    }
}

/**
 * Result of variable resolution - tracks whether resolution succeeded
 * and provides useful error information
 */
sealed class VariableResolutionResult {
    /**
     * Successfully resolved variable to a value
     */
    data class Success(val value: PropertyValue) : VariableResolutionResult()

    /**
     * Variable not found
     */
    data class NotFound(val variableName: String, val importAlias: String? = null) : VariableResolutionResult() {
        val message: String get() = if (importAlias != null) {
            "Variable '@$variableName' not found in import '\$$importAlias'"
        } else {
            "Variable '@$variableName' not found"
        }
    }

    /**
     * Import alias not defined
     */
    data class ImportNotFound(val alias: String) : VariableResolutionResult() {
        val message: String get() = "Import '\$$alias' not defined"
    }

    /**
     * Circular reference detected
     */
    data class CircularReference(val path: List<String>) : VariableResolutionResult() {
        val message: String get() = "Circular reference detected: ${path.joinToString(" -> ")}"
    }
}
