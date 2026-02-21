// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.decompile

/**
 * Post-processing fixes for FernFlower decompilation output.
 * Ported from TypeScript `parser.ts` preprocessSource().
 *
 * These patterns clean up artifacts from decompiling obfuscated Hytale bytecode:
 * 1. Empty assertion-disabled if blocks
 * 2. Empty static initializer blocks
 * 3. $assertionsDisabled references in unrepresentable contexts
 * 4. Remaining <unrepresentable> placeholders
 * 5. Broken enum declarations (empty enums with fields → class)
 * 6. Qualified generic inner class instantiation artifacts
 * 7. Static blocks in interfaces (invalid Java)
 */
object DecompilationFixes {

    private data class Fix(val pattern: Regex, val replacement: String)

    // Fix 1: Remove empty if blocks checking $assertionsDisabled
    private val FIX_EMPTY_ASSERTION_IF = Fix(
        Regex("""\s*if\s*\([^)]*\${'$'}assertionsDisabled[^)]*\)\s*\{\s*\}\s*\n?"""),
        "\n"
    )

    // Fix 2: Remove empty static initializer blocks
    private val FIX_EMPTY_STATIC_BLOCK = Fix(
        Regex("""\n\s*static\s*\{\s*\}"""),
        ""
    )

    // Fix 3: Replace <unrepresentable>.$assertionsDisabled with false
    private val FIX_UNREPRESENTABLE_ASSERTIONS = Fix(
        Regex("""<unrepresentable>\.\${'$'}assertionsDisabled"""),
        "false"
    )

    // Fix 4: Replace remaining <unrepresentable> with null comment
    // Negative lookbehind ensures we don't re-wrap inside an existing /* ... */ comment
    private val FIX_UNREPRESENTABLE_OTHER = Fix(
        Regex("""(?<!/\* )<unrepresentable>(?! \*/)"""),
        "null /* <unrepresentable> */"
    )

    // Fix 5: Convert empty enums with fields to classes
    // Matches: enum Name {\n   private/protected/public/static/final
    private val FIX_EMPTY_ENUM = Fix(
        Regex("""\benum\s+(\w+)\s*\{\s*\n(\s*)(private|protected|public|static|final)"""),
        "class \$1 {\n\$2\$3"
    )

    // Fix 6: Fix qualified generic inner class instantiation
    // e.g. new Foo<Bar>.Baz → new Foo.Baz
    private val FIX_GENERIC_INNER_CLASS = Fix(
        Regex("""new\s+(\w+)<[^>]+>\.(\w+)"""),
        "new \$1.\$2"
    )

    private val ALL_FIXES = listOf(
        FIX_EMPTY_ASSERTION_IF,
        FIX_EMPTY_STATIC_BLOCK,
        FIX_UNREPRESENTABLE_ASSERTIONS,
        FIX_UNREPRESENTABLE_OTHER,
        FIX_EMPTY_ENUM,
        FIX_GENERIC_INNER_CLASS,
    )

    /** Pattern to detect top-level interface declarations */
    private val INTERFACE_PATTERN = Regex("""^\s*(public\s+)?interface\s+""", RegexOption.MULTILINE)

    /** Static block pattern for interface cleanup (Fix 7) */
    private val INTERFACE_STATIC_BLOCK = Regex("""\n\s*static\s*\{[^}]*\}\s*\n""")

    /**
     * Apply all decompilation fixes to a source string.
     * This is idempotent — safe to call multiple times on the same source.
     */
    fun applyAll(source: String): String {
        var result = source
        for (fix in ALL_FIXES) {
            result = fix.pattern.replace(result, fix.replacement)
        }
        // Fix 7: Strip static blocks from top-level interfaces
        if (INTERFACE_PATTERN.containsMatchIn(result)) {
            result = INTERFACE_STATIC_BLOCK.replace(result, "\n")
        }
        return result
    }

    /**
     * Apply fixes to a file in-place.
     * @return true if the file was modified
     */
    fun applyToFile(file: java.io.File): Boolean {
        val original = file.readText()
        val fixed = applyAll(original)
        if (fixed != original) {
            file.writeText(fixed)
            return true
        }
        return false
    }
}
