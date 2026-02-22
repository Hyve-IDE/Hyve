// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.parser

import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.result.Result
import com.hyve.ui.exporter.UIExporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for round-trip fidelity: parse → export should produce identical output.
 * Covers:
 * - Comment ordering relative to child elements (Fix 2)
 * - Anchor field order preservation (Fix 3)
 * - General round-trip stability (no-change export)
 */
class RoundTripFidelityTest {

    // =====================================================================
    // Fix 2: Comment ordering relative to children
    // =====================================================================

    @Test
    fun `comment before first child stays in property position`() {
        val source = """
            Group {
                // Header comment
                Label { Text: "Hello"; }
            }
        """.trimIndent()

        val exported = roundTrip(source)

        // Comment should appear before the child element
        val commentIdx = exported.indexOf("// Header comment")
        val labelIdx = exported.indexOf("Label")
        assertThat(commentIdx).isGreaterThan(-1)
        assertThat(commentIdx).isLessThan(labelIdx)
    }

    @Test
    fun `comment between children preserved in position`() {
        val source = """
            Group {
                Label #First {
                    Text: "One";
                }
                // Divider comment
                Label #Second {
                    Text: "Two";
                }
            }
        """.trimIndent()

        val exported = roundTrip(source)

        val firstIdx = exported.indexOf("Label #First")
        val commentIdx = exported.indexOf("// Divider comment")
        val secondIdx = exported.indexOf("Label #Second")

        assertThat(firstIdx).isGreaterThan(-1)
        assertThat(commentIdx).isGreaterThan(firstIdx)
        assertThat(secondIdx).isGreaterThan(commentIdx)
    }

    @Test
    fun `multiple comments between different children preserved in order`() {
        val source = """
            Group {
                Label #A {
                    Text: "A";
                }
                // Between A and B
                Label #B {
                    Text: "B";
                }
                // Between B and C
                Label #C {
                    Text: "C";
                }
            }
        """.trimIndent()

        val exported = roundTrip(source)

        val aIdx = exported.indexOf("Label #A")
        val comment1Idx = exported.indexOf("// Between A and B")
        val bIdx = exported.indexOf("Label #B")
        val comment2Idx = exported.indexOf("// Between B and C")
        val cIdx = exported.indexOf("Label #C")

        assertThat(aIdx).isGreaterThan(-1)
        assertThat(comment1Idx).isGreaterThan(aIdx)
        assertThat(bIdx).isGreaterThan(comment1Idx)
        assertThat(comment2Idx).isGreaterThan(bIdx)
        assertThat(cIdx).isGreaterThan(comment2Idx)
    }

    @Test
    fun `comment before properties and before children both preserved`() {
        val source = """
            Group {
                // Property comment
                Text: "Hello";
                Label #Child {
                    Text: "World";
                }
                // After-child comment
                Label #Child2 {
                    Text: "Bye";
                }
            }
        """.trimIndent()

        val exported = roundTrip(source)

        val propCommentIdx = exported.indexOf("// Property comment")
        val textIdx = exported.indexOf("Text:")
        val childIdx = exported.indexOf("Label #Child {")
        val afterCommentIdx = exported.indexOf("// After-child comment")
        val child2Idx = exported.indexOf("Label #Child2")

        assertThat(propCommentIdx).isGreaterThan(-1)
        assertThat(propCommentIdx).isLessThan(textIdx)
        assertThat(afterCommentIdx).isGreaterThan(childIdx)
        assertThat(child2Idx).isGreaterThan(afterCommentIdx)
    }

    @Test
    fun `element with only pre-child comments round-trips correctly`() {
        val source = """
            Group {
                // This is a comment
                // Another comment
                Text: "Hello";
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("// This is a comment")
        assertThat(exported).contains("// Another comment")
        assertThat(exported).contains("Text:")
    }

    @Test
    fun `comment-only element body round-trips correctly`() {
        val source = """
            Group {
                // Just a comment, no properties or children
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("// Just a comment, no properties or children")
    }

    // =====================================================================
    // Fix 3: Anchor field order preservation
    // =====================================================================

    @Test
    fun `anchor field order Width Height Right Top preserved`() {
        val source = """
            Group {
                Anchor: (Width: 190, Height: 35, Right: 0, Top: 0);
            }
        """.trimIndent()

        val exported = roundTrip(source)

        // Verify the exported anchor has fields in original order
        assertThat(exported).contains("(Width: 190, Height: 35, Right: 0, Top: 0)")
    }

    @Test
    fun `anchor field order Left Top Width Height preserved`() {
        val source = """
            Group {
                Anchor: (Left: 10, Top: 20, Width: 100, Height: 50);
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("(Left: 10, Top: 20, Width: 100, Height: 50)")
    }

    @Test
    fun `anchor field order Right Bottom Width Height preserved`() {
        val source = """
            Group {
                Anchor: (Right: 5, Bottom: 10, Width: 200, Height: 100);
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("(Right: 5, Bottom: 10, Width: 200, Height: 100)")
    }

    @Test
    fun `anchor with all six fields in non-standard order preserved`() {
        val source = """
            Group {
                Anchor: (Height: 50, Width: 100, Bottom: 0, Right: 0, Top: 10, Left: 20);
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("(Height: 50, Width: 100, Bottom: 0, Right: 0, Top: 10, Left: 20)")
    }

    @Test
    fun `anchor with percentage values preserves field order`() {
        val source = """
            Group {
                Anchor: (Width: 100%, Height: 100%, Left: 0, Top: 0);
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("(Width: 100%, Height: 100%, Left: 0, Top: 0)")
    }

    @Test
    fun `partial anchor preserves field order`() {
        val source = """
            Group {
                Anchor: (Height: 35);
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("(Height: 35)")
    }

    @Test
    fun `programmatic AnchorValue without fieldOrder uses default order`() {
        val anchor = AnchorValue(
            width = AnchorDimension.Absolute(100f),
            height = AnchorDimension.Absolute(50f),
            left = AnchorDimension.Absolute(10f),
            top = AnchorDimension.Absolute(20f)
        )

        // Default order: Left, Top, Right, Bottom, Width, Height
        val str = anchor.toString()
        assertThat(str).isEqualTo("(Left: 10, Top: 20, Width: 100, Height: 50)")
    }

    @Test
    fun `AnchorValue with explicit fieldOrder uses that order`() {
        val anchor = AnchorValue(
            width = AnchorDimension.Absolute(100f),
            height = AnchorDimension.Absolute(50f),
            left = AnchorDimension.Absolute(10f),
            top = AnchorDimension.Absolute(20f),
            fieldOrder = listOf("Width", "Height", "Left", "Top")
        )

        val str = anchor.toString()
        assertThat(str).isEqualTo("(Width: 100, Height: 50, Left: 10, Top: 20)")
    }

    // =====================================================================
    // General round-trip stability
    // =====================================================================

    @Test
    fun `simple file round-trips to identical output`() {
        val source = """
            Group #Main {
                LayoutMode: Top;
                Anchor: (Left: 0, Top: 0, Right: 0, Bottom: 0);

                Label #Title {
                    Text: "Hello World";
                    Anchor: (Left: 10, Top: 5, Width: 200, Height: 30);
                }
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported.trim()).isEqualTo(source.trim())
    }

    @Test
    fun `file with mixed comments children and anchors round-trips`() {
        val source = """
            Group #HUD {
                Anchor: (Width: 190, Height: 35, Right: 0, Top: 0);

                // Health section
                Label #Health {
                    Text: "HP";
                    Anchor: (Left: 5, Top: 5, Width: 50, Height: 25);
                }
                // Mana section
                Label #Mana {
                    Text: "MP";
                    Anchor: (Left: 60, Top: 5, Width: 50, Height: 25);
                }
            }
        """.trimIndent()

        val exported = roundTrip(source)

        // Verify anchor field order
        assertThat(exported).contains("(Width: 190, Height: 35, Right: 0, Top: 0)")

        // Verify comment positioning
        val healthIdx = exported.indexOf("Label #Health")
        val healthCommentIdx = exported.indexOf("// Health section")
        val manaIdx = exported.indexOf("Label #Mana")
        val manaCommentIdx = exported.indexOf("// Mana section")

        assertThat(healthCommentIdx).isLessThan(healthIdx)
        assertThat(manaCommentIdx).isGreaterThan(healthIdx)
        assertThat(manaCommentIdx).isLessThan(manaIdx)
    }

    @Test
    fun `double round-trip produces stable output`() {
        val source = """
            Group #Container {
                Anchor: (Width: 300, Height: 200, Right: 10, Top: 10);

                // First element
                Label #One {
                    Text: "One";
                }
                // Second element
                Label #Two {
                    Text: "Two";
                }
            }
        """.trimIndent()

        val firstExport = roundTrip(source)
        val secondExport = roundTrip(firstExport)

        assertThat(secondExport).isEqualTo(firstExport)
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private fun roundTrip(source: String): String {
        val parser = UIParser(source)
        val parseResult = parser.parse()
        assertThat(parseResult.isSuccess()).`as`("Parse should succeed").isTrue()
        val document = (parseResult as Result.Success).value

        val exporter = UIExporter()
        val exportResult = exporter.export(document)
        assertThat(exportResult.isSuccess()).`as`("Export should succeed").isTrue()
        return (exportResult as Result.Success).value
    }
}
