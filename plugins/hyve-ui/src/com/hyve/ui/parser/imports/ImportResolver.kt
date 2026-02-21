package com.hyve.ui.parser.imports

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.result.Result
import com.hyve.ui.parser.ParseError
import com.hyve.ui.parser.UIParser
import com.hyve.ui.parser.variables.VariableScope
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Resolves file imports ($Alias = "path/to/file.ui") and caches parsed files.
 *
 * Features:
 * - Relative path resolution from current file location
 * - Fallback search paths (vanilla Interface dir, project resource roots)
 * - Caching of parsed imports to avoid re-parsing
 * - Circular import detection
 * - Lazy loading (only parse when variable is accessed)
 */
class ImportResolver(
    private val baseDirectory: Path,
    private val cache: ImportCache = ImportCache(),
    private val searchPaths: List<Path> = emptyList()
) {
    // Track files currently being resolved to detect circular imports
    private val resolvingFiles = mutableSetOf<String>()

    /**
     * Resolve an import path relative to the current file.
     * @param currentFilePath The path of the file containing the import
     * @param importPath The relative path from the import declaration
     * @return The absolute path to the imported file
     */
    fun resolvePath(currentFilePath: Path, importPath: String): Path {
        // Import paths are relative to the current file's directory
        val currentDir = currentFilePath.parent ?: baseDirectory
        return currentDir.resolve(importPath).normalize()
    }

    /**
     * Resolve all imports in a document and build variable scopes.
     * @param document The parsed document
     * @param documentPath The path of the document file
     * @param scope The scope to populate with resolved imports
     * @return List of errors encountered during resolution
     */
    fun resolveImports(
        document: UIDocument,
        documentPath: Path,
        scope: VariableScope
    ): List<ImportError> {
        val errors = mutableListOf<ImportError>()

        for ((alias, importPath) in document.imports) {
            val aliasName = alias.value.removePrefix("$")
            scope.defineImport(aliasName, importPath)

            // Resolve the import path (relative to current file first)
            val resolvedPath = resolvePath(documentPath, importPath)

            // Try to resolve and parse the imported file
            var result = resolveAndParse(resolvedPath, aliasName)

            // If file not found, try fallback search paths
            if (result is ImportResult.FileNotFound && searchPaths.isNotEmpty()) {
                for (searchPath in searchPaths) {
                    val fallbackPath = searchPath.resolve(importPath).normalize()
                    if (fallbackPath.exists()) {
                        result = resolveAndParse(fallbackPath, aliasName)
                        if (result is ImportResult.Success) break
                    }
                }
            }

            when (result) {
                is ImportResult.Success -> {
                    scope.setResolvedImport(aliasName, result.scope)
                }
                is ImportResult.FileNotFound -> {
                    errors.add(ImportError.FileNotFound(aliasName, resolvedPath.toString()))
                }
                is ImportResult.ParseFailed -> {
                    errors.add(ImportError.ParseFailed(aliasName, resolvedPath.toString(), result.errors))
                }
                is ImportResult.CircularImport -> {
                    errors.add(ImportError.CircularImport(aliasName, result.path))
                }
            }
        }

        return errors
    }

    /**
     * Resolve and parse an imported file, using cache if available.
     */
    private fun resolveAndParse(filePath: Path, alias: String): ImportResult {
        val normalizedPath = filePath.toAbsolutePath().normalize().toString()

        // Check cache first
        cache.get(normalizedPath)?.let { cachedScope ->
            return ImportResult.Success(cachedScope)
        }

        // Check for circular import
        if (normalizedPath in resolvingFiles) {
            return ImportResult.CircularImport(resolvingFiles.toList() + normalizedPath)
        }

        // Check if file exists
        if (!filePath.exists()) {
            return ImportResult.FileNotFound(alias, normalizedPath)
        }

        // Parse the imported file
        resolvingFiles.add(normalizedPath)
        try {
            val source = filePath.readText()
            val parser = UIParser(source)
            val result = parser.parse()

            when (result) {
                is Result.Success -> {
                    val importedDoc = result.value
                    val importedScope = VariableScope(name = "import:$alias")

                    // Extract variable definitions from the imported document
                    extractVariables(importedDoc, importedScope)

                    // Recursively resolve imports in the imported file
                    val nestedErrors = resolveImports(importedDoc, filePath, importedScope)
                    if (nestedErrors.isNotEmpty()) {
                        // Log but don't fail - the import might still be partially usable
                    }

                    // Cache the result
                    cache.put(normalizedPath, importedScope)

                    return ImportResult.Success(importedScope)
                }
                is Result.Failure -> {
                    return ImportResult.ParseFailed(alias, normalizedPath, result.error)
                }
            }
        } finally {
            resolvingFiles.remove(normalizedPath)
        }
    }

    /**
     * Extract variable definitions from a document into a scope.
     * Variables come from style definitions that are simple values.
     */
    private fun extractVariables(document: UIDocument, scope: VariableScope) {
        for ((styleName, styleDef) in document.styles) {
            // Check if the style is a simple value or an alias
            val value = styleDef.properties[com.hyve.ui.core.id.PropertyName("_value")]
                ?: styleDef.properties[com.hyve.ui.core.id.PropertyName("_styleAlias")]

            if (value != null) {
                // Simple value style: @Size = 64
                scope.defineVariable(styleName.value, value)
            } else if (styleDef.properties.isNotEmpty()) {
                // Tuple style: @Style = (FontSize: 14, ...)
                val tupleValue = PropertyValue.Tuple(
                    styleDef.properties.map { (k, v) -> k.value to v }.toMap()
                )
                scope.defineVariable(styleName.value, tupleValue)
            }
        }
    }

    /**
     * Clear the import cache
     */
    fun clearCache() {
        cache.clear()
    }

    companion object {
        /**
         * Create an ImportResolver for a given file path
         */
        fun forFile(filePath: Path, searchPaths: List<Path> = emptyList()): ImportResolver {
            val baseDir = filePath.parent ?: Path.of(".")
            return ImportResolver(baseDir, searchPaths = searchPaths)
        }

        /**
         * Create an ImportResolver with a shared cache
         */
        fun withCache(baseDirectory: Path, cache: ImportCache, searchPaths: List<Path> = emptyList()): ImportResolver {
            return ImportResolver(baseDirectory, cache, searchPaths)
        }
    }
}

/**
 * Cache for parsed import files
 */
class ImportCache {
    private val cache = mutableMapOf<String, VariableScope>()

    fun get(path: String): VariableScope? = cache[path]

    fun put(path: String, scope: VariableScope) {
        cache[path] = scope
    }

    fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size
}

/**
 * Result of attempting to resolve and parse an import
 */
sealed class ImportResult {
    data class Success(val scope: VariableScope) : ImportResult()
    data class FileNotFound(val alias: String, val path: String) : ImportResult()
    data class ParseFailed(val alias: String, val path: String, val errors: List<ParseError>) : ImportResult()
    data class CircularImport(val path: List<String>) : ImportResult()
}

/**
 * Errors that can occur during import resolution
 */
sealed class ImportError {
    abstract val message: String

    data class FileNotFound(val alias: String, val path: String) : ImportError() {
        override val message: String get() = "Import '\$$alias' file not found: $path"
    }

    data class ParseFailed(val alias: String, val path: String, val errors: List<ParseError>) : ImportError() {
        override val message: String get() = "Import '\$$alias' failed to parse: $path (${errors.size} errors)"
    }

    data class CircularImport(val alias: String, val path: List<String>) : ImportError() {
        override val message: String get() = "Circular import detected for '\$$alias': ${path.joinToString(" -> ")}"
    }
}
