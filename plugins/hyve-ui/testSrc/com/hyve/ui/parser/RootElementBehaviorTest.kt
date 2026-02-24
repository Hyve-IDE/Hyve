// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.parser

import com.hyve.ui.core.domain.elements.AUTO_ID_PREFIX
import com.hyve.ui.core.domain.elements.assignAutoIds
import com.hyve.ui.core.result.Result
import com.hyve.ui.exporter.UIExporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for root element behavior in the parser.
 *
 * Single top-level element files should use the element directly as the document root
 * (no synthetic Root wrapper). Multi-element files should wrap in a synthetic Root.
 * This ensures the hierarchy tree shows the authored structure without phantom wrappers.
 */
class RootElementBehaviorTest {

    // --- Single element: no wrapper ---

    @Test
    fun `single element becomes root directly`() {
        val source = "Group { }"
        val doc = parseDoc(source)

        assertThat(doc.root.type.value).isEqualTo("Group")
    }

    @Test
    fun `single element with ID becomes root directly`() {
        val source = "Group #Root { }"
        val doc = parseDoc(source)

        assertThat(doc.root.type.value).isEqualTo("Group")
        assertThat(doc.root.id?.value).isEqualTo("Root")
    }

    @Test
    fun `single element with properties becomes root directly`() {
        val source = """
            Group #Root {
                Anchor: (Left: 0, Top: 0, Width: 100%, Height: 100%);
            }
        """.trimIndent()
        val doc = parseDoc(source)

        assertThat(doc.root.type.value).isEqualTo("Group")
        assertThat(doc.root.id?.value).isEqualTo("Root")
        assertThat(doc.root.properties.isNotEmpty()).isTrue()
    }

    @Test
    fun `single element with children becomes root directly`() {
        val source = """
            Group #Container {
                Button #Btn1 { }
                Label #Lbl1 { }
            }
        """.trimIndent()
        val doc = parseDoc(source)

        assertThat(doc.root.type.value).isEqualTo("Group")
        assertThat(doc.root.id?.value).isEqualTo("Container")
        assertThat(doc.root.children).hasSize(2)
        assertThat(doc.root.children[0].id?.value).isEqualTo("Btn1")
        assertThat(doc.root.children[1].id?.value).isEqualTo("Lbl1")
    }

    @Test
    fun `single element with imports and styles becomes root directly`() {
        val source = """
            ${'$'}Common = "../../Common.ui";
            @HeaderStyle = (FontSize: 24, RenderBold: true);

            Group #MainContainer {
                LayoutMode: Top;
            }
        """.trimIndent()
        val doc = parseDoc(source)

        assertThat(doc.root.type.value).isEqualTo("Group")
        assertThat(doc.root.id?.value).isEqualTo("MainContainer")
        assertThat(doc.imports).hasSize(1)
        assertThat(doc.styles).hasSize(1)
    }

    @Test
    fun `new UI file template produces single root - no wrapper`() {
        // This is the template from NewUIFileAction
        val source = """
            // New Hytale UI File
            // Created with Hyve IDE

            Group #Root {
                Anchor: (Left: 0, Top: 0, Width: 100%, Height: 100%);

                // Add your UI elements here
            }
        """.trimIndent()
        val doc = parseDoc(source)

        // Must be the Group itself, not a synthetic Root wrapping it
        assertThat(doc.root.type.value).isEqualTo("Group")
        assertThat(doc.root.id?.value).isEqualTo("Root")
    }

    // --- Multiple elements: wrapped in Root ---

    @Test
    fun `multiple top-level elements get wrapped in synthetic Root`() {
        val source = """
            Group #Panel { }
            Button #Btn { }
        """.trimIndent()
        val doc = parseDoc(source)

        assertThat(doc.root.type.value).isEqualTo("Root")
        assertThat(doc.root.id).isNull()
        assertThat(doc.root.children).hasSize(2)
        assertThat(doc.root.children[0].type.value).isEqualTo("Group")
        assertThat(doc.root.children[1].type.value).isEqualTo("Button")
    }

    @Test
    fun `empty file produces empty Root`() {
        val source = ""
        val doc = parseDoc(source)

        assertThat(doc.root.type.value).isEqualTo("Root")
        assertThat(doc.root.id).isNull()
        assertThat(doc.root.children).isEmpty()
    }

    @Test
    fun `comment-only file produces empty Root`() {
        val source = """
            // Just a comment
            // No elements
        """.trimIndent()
        val doc = parseDoc(source)

        assertThat(doc.root.type.value).isEqualTo("Root")
        assertThat(doc.root.children).isEmpty()
    }

    // --- Auto-ID interaction ---

    @Test
    fun `auto-IDs on single element root do not create wrapper`() {
        val source = """
            Group #Root {
                Label { Text: "Hello"; }
                Button { Text: "Click"; }
            }
        """.trimIndent()
        val doc = parseDoc(source)
        val withAutoIds = doc.copy(root = doc.root.assignAutoIds())

        // Root should still be the Group, not a wrapper
        assertThat(withAutoIds.root.type.value).isEqualTo("Group")
        assertThat(withAutoIds.root.id?.value).isEqualTo("Root")

        // Children should get auto-IDs (they had no explicit IDs)
        assertThat(withAutoIds.root.children).hasSize(2)
        withAutoIds.root.children.forEach { child ->
            assertThat(child.id).isNotNull
            assertThat(child.id!!.value).startsWith(AUTO_ID_PREFIX)
        }
    }

    @Test
    fun `auto-IDs do not produce wrapper prefix on single root`() {
        val source = "Group #Root { }"
        val doc = parseDoc(source)
        val withAutoIds = doc.copy(root = doc.root.assignAutoIds())

        // Root already had an ID, so it should keep it
        assertThat(withAutoIds.root.id?.value).isEqualTo("Root")
        // Should NOT have _auto_ prefix
        assertThat(withAutoIds.root.id?.value).doesNotStartWith(AUTO_ID_PREFIX)
    }

    // --- Round-trip stability ---

    @Test
    fun `single element round-trip stays unwrapped`() {
        val source = """
            Group #Root {
                Anchor: (Left: 0, Top: 0, Width: 100%, Height: 100%);
            }
        """.trimIndent()

        val doc = parseDoc(source)
        val exported = export(doc)
        val reparsed = parseDoc(exported)

        // Both should have Group as root, not Root wrapper
        assertThat(doc.root.type.value).isEqualTo("Group")
        assertThat(reparsed.root.type.value).isEqualTo("Group")
        assertThat(reparsed.root.id?.value).isEqualTo("Root")

        // Export should contain "Group #Root {", not a bare synthetic "Root {" wrapper
        assertThat(exported).contains("Group #Root {")
        assertThat(exported.lines().none { it.trimStart().startsWith("Root {") }).isTrue()
    }

    @Test
    fun `multi-element round-trip preserves wrapper`() {
        val source = """
            Label { Text: "A"; }
            Button { Text: "B"; }
        """.trimIndent()

        val doc = parseDoc(source)
        val exported = export(doc)
        val reparsed = parseDoc(exported)

        // Both should have Root wrapper with 2 children
        assertThat(doc.root.type.value).isEqualTo("Root")
        assertThat(doc.root.children).hasSize(2)
        assertThat(reparsed.root.type.value).isEqualTo("Root")
        assertThat(reparsed.root.children).hasSize(2)
    }

    // --- Helpers ---

    private fun parseDoc(source: String): com.hyve.ui.core.domain.UIDocument {
        val result = UIParser(source).parse()
        assertThat(result.isSuccess()).`as`("Parse should succeed").isTrue()
        return (result as Result.Success).value
    }

    private fun export(doc: com.hyve.ui.core.domain.UIDocument): String {
        val result = UIExporter().export(doc)
        assertThat(result.isSuccess()).`as`("Export should succeed").isTrue()
        return (result as Result.Success).value
    }
}
