// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.result.Result
import com.hyve.ui.exporter.UIExporter
import com.hyve.ui.parser.UIParser
import com.hyve.ui.parser.VariableAwareParser
import com.hyve.ui.state.EditDeltaTracker
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Integration tests for the dual-document architecture.
 * Validates that:
 * 1. Raw document preserves @refs and raw syntax
 * 2. Resolved document has concrete values for rendering
 * 3. Delta tracker + export pipeline preserves @refs for unedited properties
 * 4. Edited properties overwrite @refs with concrete values in export
 */
class DualParseExportTest {

    @Test
    fun `dual parse produces raw doc with @refs and resolved doc with concrete values`() {
        val source = """
            @Size = 64;
            @HeaderStyle = (FontSize: 24, RenderBold: true);

            Group #Main {
                Width: @Size;
                Style: @HeaderStyle;
                Text: "Static text";
            }
        """.trimIndent()

        // Raw parse (UIParser)
        val rawResult = UIParser(source).parse()
        assertThat(rawResult.isSuccess()).isTrue()
        val rawDoc = (rawResult as Result.Success).value

        // Resolved parse (VariableAwareParser)
        val resolvedResult = VariableAwareParser.forSourceWithPath(source, Path("test.ui")).parse()
        assertThat(resolvedResult.isSuccess()).isTrue()
        val resolvedDoc = (resolvedResult as Result.Success).value.document

        // Raw doc: Style should still be a @ref
        val rawStyle = rawDoc.root.getProperty("Style")
        assertThat(rawStyle).isInstanceOf(PropertyValue.Style::class.java)

        // Resolved doc: Style should be a concrete Tuple
        val resolvedStyle = resolvedDoc.root.getProperty("Style")
        assertThat(resolvedStyle).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = resolvedStyle as PropertyValue.Tuple
        assertThat(tuple.values["FontSize"]).isEqualTo(PropertyValue.Number(24.0))

        // Both should have same element IDs
        assertThat(rawDoc.root.id).isEqualTo(resolvedDoc.root.id)
    }

    @Test
    fun `export with no deltas preserves original @refs exactly`() {
        val source = """
            @Size = 64;
            @HeaderStyle = (FontSize: 24, RenderBold: true);

            Group #Main {
                Width: @Size;
                Style: @HeaderStyle;
            }
        """.trimIndent()

        val rawResult = UIParser(source).parse()
        assertThat(rawResult.isSuccess()).isTrue()
        val rawDoc = (rawResult as Result.Success).value

        val tracker = EditDeltaTracker()
        val withDeltas = tracker.applyTo(rawDoc)

        val exportResult = UIExporter().export(withDeltas)
        assertThat(exportResult.isSuccess()).isTrue()
        val exported = (exportResult as Result.Success).value

        // Export should contain @refs in element body, not expanded values
        assertThat(exported).contains("@Size")
        assertThat(exported).contains("@HeaderStyle")
        // Style property should use @ref, not inline expansion
        assertThat(exported).contains("Style: @HeaderStyle")
        assertThat(exported).doesNotContain("Style: (FontSize:")
    }

    @Test
    fun `export with property delta overwrites edited property but preserves other @refs`() {
        val source = """
            @MyPadding = (Full: 10);
            @MyStyle = (FontSize: 24, RenderBold: true);

            Group #Main {
                Padding: @MyPadding;
                Style: @MyStyle;
                Text: "Original";
            }
        """.trimIndent()

        val rawResult = UIParser(source).parse()
        assertThat(rawResult.isSuccess()).isTrue()
        val rawDoc = (rawResult as Result.Success).value

        // Simulate user editing the Text property
        val tracker = EditDeltaTracker()
        tracker.record(EditDeltaTracker.EditDelta.SetProperty(
            elementId = ElementId("Main"),
            propertyName = "Text",
            value = PropertyValue.Text("Updated")
        ))

        val withDeltas = tracker.applyTo(rawDoc)

        val exportResult = UIExporter().export(withDeltas)
        assertThat(exportResult.isSuccess()).isTrue()
        val exported = (exportResult as Result.Success).value

        // Text should be updated
        assertThat(exported).contains("\"Updated\"")
        assertThat(exported).doesNotContain("\"Original\"")

        // @refs for non-edited properties should be preserved
        assertThat(exported).contains("@MyPadding")
        assertThat(exported).contains("@MyStyle")
    }

    @Test
    fun `export with move delta updates Anchor but preserves other @refs`() {
        val source = """
            @MyStyle = (FontSize: 24);

            Button #Btn {
                Anchor: (Left: 10, Top: 20, Width: 100, Height: 30);
                Style: @MyStyle;
            }
        """.trimIndent()

        val rawResult = UIParser(source).parse()
        assertThat(rawResult.isSuccess()).isTrue()
        val rawDoc = (rawResult as Result.Success).value

        val newAnchor = AnchorValue.absolute(left = 50f, top = 60f, width = 100f, height = 30f)
        val tracker = EditDeltaTracker()
        tracker.record(EditDeltaTracker.EditDelta.MoveElement(
            elementId = ElementId("Btn"),
            newAnchor = PropertyValue.Anchor(newAnchor)
        ))

        val withDeltas = tracker.applyTo(rawDoc)
        val btn = withDeltas.findElementById(ElementId("Btn"))
        assertThat(btn).isNotNull

        // Anchor should be updated
        val anchor = btn!!.getProperty("Anchor") as PropertyValue.Anchor
        assertThat(anchor.anchor.left).isNotNull

        // Style @ref should be preserved
        val styleProp = btn.getProperty("Style")
        assertThat(styleProp).isInstanceOf(PropertyValue.Style::class.java)
    }

    @Test
    fun `export with structural delta adds new element to raw document`() {
        val source = """
            Group #Container {
                Button #Btn1 {
                    Text: "First";
                }
            }
        """.trimIndent()

        val rawResult = UIParser(source).parse()
        assertThat(rawResult.isSuccess()).isTrue()
        val rawDoc = (rawResult as Result.Success).value

        val tracker = EditDeltaTracker()
        val newElement = com.hyve.ui.core.domain.elements.UIElement(
            type = com.hyve.ui.core.id.ElementType("Label"),
            id = ElementId("Lbl1"),
            properties = com.hyve.ui.core.domain.properties.PropertyMap.of(
                "Text" to PropertyValue.Text("New Label")
            )
        )

        tracker.record(EditDeltaTracker.EditDelta.AddElement(
            parentId = ElementId("Container"),
            index = 1,
            element = newElement
        ))

        val withDeltas = tracker.applyTo(rawDoc)

        val container = withDeltas.findElementById(ElementId("Container"))
        assertThat(container!!.children).hasSize(2)
        assertThat(container.children[1].id).isEqualTo(ElementId("Lbl1"))

        // Export should include new element
        val exportResult = UIExporter().export(withDeltas)
        assertThat(exportResult.isSuccess()).isTrue()
        val exported = (exportResult as Result.Success).value
        assertThat(exported).contains("Lbl1")
        assertThat(exported).contains("New Label")
    }

    @Test
    fun `round-trip with deltas - parse, edit, export, re-parse produces correct document`() {
        val source = """
            Group #Root {
                Button #Btn1 {
                    Text: "Click Me";
                    Anchor: (Left: 10, Top: 20, Width: 100, Height: 30);
                }
            }
        """.trimIndent()

        // Step 1: Parse raw
        val rawResult = UIParser(source).parse()
        assertThat(rawResult.isSuccess()).isTrue()
        val rawDoc = (rawResult as Result.Success).value

        // Step 2: Apply edit deltas
        val tracker = EditDeltaTracker()
        tracker.record(EditDeltaTracker.EditDelta.SetProperty(
            elementId = ElementId("Btn1"),
            propertyName = "Text",
            value = PropertyValue.Text("Updated!")
        ))

        val withDeltas = tracker.applyTo(rawDoc)

        // Step 3: Export
        val exportResult = UIExporter().export(withDeltas)
        assertThat(exportResult.isSuccess()).isTrue()
        val exported = (exportResult as Result.Success).value

        // Step 4: Re-parse and verify
        val reParsed = UIParser(exported).parse()
        assertThat(reParsed.isSuccess()).isTrue()
        val reDoc = (reParsed as Result.Success).value

        val btn = reDoc.findElementById(ElementId("Btn1"))
        assertThat(btn).isNotNull
        assertThat(btn!!.getProperty("Text")).isEqualTo(PropertyValue.Text("Updated!"))

        // Anchor should be preserved
        assertThat(btn.getProperty("Anchor")).isInstanceOf(PropertyValue.Anchor::class.java)
    }

    @Test
    fun `undo scenario - record delta then remove it produces clean export`() {
        val source = """
            @MyColor = #ff0000;

            Label #Lbl {
                Text: "Hello";
                Background: @MyColor;
            }
        """.trimIndent()

        val rawResult = UIParser(source).parse()
        assertThat(rawResult.isSuccess()).isTrue()
        val rawDoc = (rawResult as Result.Success).value

        val tracker = EditDeltaTracker()

        // User edits Text
        tracker.record(EditDeltaTracker.EditDelta.SetProperty(
            elementId = ElementId("Lbl"),
            propertyName = "Text",
            value = PropertyValue.Text("Modified")
        ))

        // User undoes the edit
        tracker.removeDelta(ElementId("Lbl"), "Text")

        val withDeltas = tracker.applyTo(rawDoc)
        val exportResult = UIExporter().export(withDeltas)
        assertThat(exportResult.isSuccess()).isTrue()
        val exported = (exportResult as Result.Success).value

        // Text should be original
        assertThat(exported).contains("\"Hello\"")
        assertThat(exported).doesNotContain("\"Modified\"")

        // @ref should be preserved
        assertThat(exported).contains("@MyColor")
    }
}
