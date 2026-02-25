// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.docs

/**
 * Converts MDX files to clean Markdown.
 *
 * Handles the JSX components used in HytaleModding/site docs:
 * - `<Callout type="...">content</Callout>` → styled blockquote
 * - `<OfficialDocumentationNotice />` → standard notice paragraph
 * - Import statements → removed
 * - Other JSX tags → tags stripped, content preserved
 * - Frontmatter → preserved as-is
 */
object MdxConverter {

    /**
     * Convert MDX content to clean Markdown.
     * Preserves frontmatter, code blocks, standard markdown syntax, and images.
     */
    fun convert(mdx: String): String {
        var result = mdx

        // Remove import statements
        result = result.replace(
            Regex("""^import\s+.+from\s+['"].+['"]\s*;?\s*$""", RegexOption.MULTILINE),
            ""
        )

        // Convert <OfficialDocumentationNotice /> to a notice paragraph
        result = result.replace(
            Regex("""<OfficialDocumentationNotice\s*/>\s*"""),
            "> *This is official Hytale documentation.*\n\n"
        )

        // Convert <Callout type="...">content</Callout> to blockquotes
        // Handle multiline callouts
        result = convertCallouts(result)

        // Remove remaining self-closing JSX tags: <Component ... />
        result = result.replace(Regex("""<[A-Z][A-Za-z0-9.]*[^>]*/>\s*"""), "")

        // Remove remaining JSX open/close tags but keep inner content
        result = result.replace(Regex("""<([A-Z][A-Za-z0-9.]*)[^>]*>"""), "")
        result = result.replace(Regex("""</([A-Z][A-Za-z0-9.]*)>"""), "")

        // Remove JSX expressions like {variable}
        // Be careful not to remove code block content — only strip at line start or standalone
        result = result.replace(Regex("""^\{[a-zA-Z_]\w*\}\s*$""", RegexOption.MULTILINE), "")

        // Collapse 3+ consecutive blank lines to 2
        result = result.replace(Regex("""\n{3,}"""), "\n\n")

        return result.trim() + "\n"
    }

    /**
     * Convert `<Callout type="X">content</Callout>` blocks to Markdown blockquotes.
     *
     * Produces:
     * ```
     * > **Warning:** content line 1
     * > content line 2
     * ```
     */
    private fun convertCallouts(input: String): String {
        val pattern = Regex(
            """<Callout\s+type=["'](\w+)["']\s*>\s*\n?(.*?)\n?\s*</Callout>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        return pattern.replace(input) { match ->
            val type = match.groupValues[1].replaceFirstChar { it.uppercase() }
            val content = match.groupValues[2].trim()

            val label = when (type.lowercase()) {
                "warning" -> "Warning"
                "info" -> "Info"
                "tip" -> "Tip"
                "danger", "error" -> "Danger"
                "note" -> "Note"
                else -> type
            }

            val lines = content.lines()
            val firstLine = "> **$label:** ${lines.first()}"
            if (lines.size == 1) {
                "$firstLine\n"
            } else {
                val rest = lines.drop(1).joinToString("\n") { "> $it" }
                "$firstLine\n$rest\n"
            }
        }
    }
}
