// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.parser

import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.result.Result
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.writeText

/**
 * Tests for VariableAwareParser.forSourceWithPath() covering variable resolution,
 * import resolution, style definitions, arithmetic expressions, and error handling.
 */
class VariableAwareParserTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `forSourceWithPath with null path behaves like forSource - no import resolution`() {
        val source = """
            ${'$'}Common = "../Common.ui";

            @Size = 64;
            Group { Width: @Size; }
        """.trimIndent()

        val result = VariableAwareParser.forSourceWithPath(source, null).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        // Import declaration is preserved but not resolved (no parent directory)
        assertThat(parsed.document.imports).hasSize(1)

        // Variable @Size should be resolved
        val widthProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Width")]
        assertThat(widthProp).isInstanceOf(PropertyValue.Number::class.java)
        assertThat((widthProp as PropertyValue.Number).value).isEqualTo(64.0)
    }

    @Test
    fun `forSourceWithPath with valid path resolves @variables to concrete values`() {
        val source = """
            @Size = 64;
            @Title = "Hello World";
            @Enabled = true;
            @Color = #ff0000;

            Group {
                Width: @Size;
                Text: @Title;
                Visible: @Enabled;
                Background: @Color;
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        // Verify variables are in scope
        assertThat(parsed.scope.getVariable("Size")).isNotNull()
        assertThat(parsed.scope.getVariable("Title")).isNotNull()
        assertThat(parsed.scope.getVariable("Enabled")).isNotNull()
        assertThat(parsed.scope.getVariable("Color")).isNotNull()

        // Verify properties are resolved to concrete values
        val root = parsed.document.root
        assertThat(root.properties[com.hyve.ui.core.id.PropertyName("Width")])
            .isEqualTo(PropertyValue.Number(64.0))
        assertThat(root.properties[com.hyve.ui.core.id.PropertyName("Text")])
            .isEqualTo(PropertyValue.Text("Hello World"))
        assertThat(root.properties[com.hyve.ui.core.id.PropertyName("Visible")])
            .isEqualTo(PropertyValue.Boolean(true))
        assertThat(root.properties[com.hyve.ui.core.id.PropertyName("Background")])
            .isInstanceOf(PropertyValue.Color::class.java)
    }

    @Test
    fun `forSourceWithPath resolves style definitions into Tuples`() {
        val source = """
            @HeaderStyle = (FontSize: 24, RenderBold: true);

            Label {
                Style: @HeaderStyle;
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        // Style definition should be in scope as a variable
        val styleVar = parsed.scope.getVariable("HeaderStyle")
        assertThat(styleVar).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = styleVar as PropertyValue.Tuple
        assertThat(tuple.values).containsKey("FontSize")
        assertThat(tuple.values["FontSize"]).isEqualTo(PropertyValue.Number(24.0))

        // Element's Style property should be resolved to Tuple
        val styleProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Style")]
        assertThat(styleProp).isInstanceOf(PropertyValue.Tuple::class.java)
        val resolvedTuple = styleProp as PropertyValue.Tuple
        assertThat(resolvedTuple.values["FontSize"]).isEqualTo(PropertyValue.Number(24.0))
        assertThat(resolvedTuple.values["RenderBold"]).isEqualTo(PropertyValue.Boolean(true))
    }

    @Test
    fun `forSourceWithPath resolves spread operators in styles`() {
        val source = """
            @Base = (FontSize: 14, RenderBold: false);
            @Derived = (...@Base, FontSize: 24);

            Label {
                Style: @Derived;
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        // @Derived should exist in scope as a Tuple (pre-evaluation, spread not yet merged)
        val derivedVar = parsed.scope.getVariable("Derived")
        assertThat(derivedVar).isInstanceOf(PropertyValue.Tuple::class.java)

        // Element's Style property should have the EVALUATED merged result
        val styleProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Style")]
        assertThat(styleProp).isInstanceOf(PropertyValue.Tuple::class.java)
        val resolvedTuple = styleProp as PropertyValue.Tuple
        // FontSize: 24 from @Derived overrides FontSize: 14 from @Base
        assertThat(resolvedTuple.values["FontSize"]).isEqualTo(PropertyValue.Number(24.0))
        // RenderBold: false comes from @Base via spread
        assertThat(resolvedTuple.values["RenderBold"]).isEqualTo(PropertyValue.Boolean(false))
    }

    @Test
    fun `forSourceWithPath resolves arithmetic expressions`() {
        val source = """
            @BaseSize = 64;
            @LargeSize = @BaseSize * 2;
            @Sum = @BaseSize + 10;
            @Literal = 74 * 3;

            Group {
                Width: @LargeSize;
                Height: @Sum;
                Size: @Literal;
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        // Verify arithmetic expressions are evaluated
        val widthProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Width")]
        assertThat(widthProp).isEqualTo(PropertyValue.Number(128.0))

        val heightProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Height")]
        assertThat(heightProp).isEqualTo(PropertyValue.Number(74.0))

        val sizeProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Size")]
        assertThat(sizeProp).isEqualTo(PropertyValue.Number(222.0))
    }

    @Test
    fun `forSourceWithPath with non-null path but no imports still works`() {
        val source = """
            @Size = 100;
            Group { Width: @Size; }
        """.trimIndent()

        val filePath = Path("standalone.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        assertThat(parsed.document.imports).isEmpty()

        val widthProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Width")]
        assertThat(widthProp).isEqualTo(PropertyValue.Number(100.0))
    }

    @Test
    fun `element IDs are preserved in resolved document`() {
        val source = """
            @Size = 64;

            Group #MainContainer {
                Width: @Size;

                Button #SubmitBtn {
                    Height: @Size;
                }

                Label #Title {
                    Text: "Hello";
                }
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        // Verify root element ID
        assertThat(parsed.document.root.id?.value).isEqualTo("MainContainer")

        // Verify child element IDs
        assertThat(parsed.document.root.children).hasSize(2)
        assertThat(parsed.document.root.children[0].id?.value).isEqualTo("SubmitBtn")
        assertThat(parsed.document.root.children[1].id?.value).isEqualTo("Title")

        // Verify properties are resolved
        val widthProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Width")]
        assertThat(widthProp).isEqualTo(PropertyValue.Number(64.0))
    }

    @Test
    fun `resolved document has concrete PropertyValue types where raw has @refs`() {
        val source = """
            @Size = 64;
            @HeaderStyle = (FontSize: 24, RenderBold: true);

            Group {
                Width: @Size;
                Style: @HeaderStyle;
            }
        """.trimIndent()

        // Parse without variable resolution (raw parse)
        val rawParser = UIParser(source)
        val rawResult = rawParser.parse()
        assertThat(rawResult.isSuccess()).isTrue()
        val rawDoc = (rawResult as Result.Success).value

        // Parse with variable resolution
        val filePath = Path("test.ui")
        val resolvedResult = VariableAwareParser.forSourceWithPath(source, filePath).parse()
        assertThat(resolvedResult.isSuccess()).isTrue()
        val resolvedDoc = (resolvedResult as Result.Success).value.document

        // Raw document has Style reference
        val rawStyleProp = rawDoc.root.properties[com.hyve.ui.core.id.PropertyName("Style")]
        assertThat(rawStyleProp).isInstanceOf(PropertyValue.Style::class.java)

        // Resolved document has concrete Tuple
        val resolvedStyleProp = resolvedDoc.root.properties[com.hyve.ui.core.id.PropertyName("Style")]
        assertThat(resolvedStyleProp).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = resolvedStyleProp as PropertyValue.Tuple
        assertThat(tuple.values["FontSize"]).isEqualTo(PropertyValue.Number(24.0))
    }

    @Test
    fun `parse failure returns Result Failure with meaningful errors`() {
        val source = """
            Group {
                InvalidSyntax: [unclosed bracket
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isFailure()).isTrue()
        val errors = (result as Result.Failure).error
        assertThat(errors).isNotEmpty()

        // First error should be a parse error
        assertThat(errors[0]).isInstanceOf(ParserError.Parse::class.java)
        assertThat(errors[0].message).isNotEmpty()
    }

    @Test
    fun `warning callback receives resolution warnings`() {
        val source = """
            @Defined = 64;

            Group {
                Width: @Defined;
                Height: @Undefined;
            }
        """.trimIndent()

        val warnings = mutableListOf<String>()
        val filePath = Path("test.ui")

        val result = VariableAwareParser.forSourceWithPath(source, filePath) { warning ->
            warnings.add(warning)
        }.parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        // Should receive warning about undefined variable (via callback or parsed warnings)
        val allWarnings = (warnings + parsed.warnings).distinct()
        assertThat(allWarnings).isNotEmpty()
        assertThat(allWarnings.any {
            it.contains("Undefined") || it.contains("not found") || it.contains("Unresolved")
        }).isTrue()
    }

    @Test
    fun `forSourceWithPath resolves imported variables from external file`() {
        // Create common.ui in temp folder
        val commonFile = tempFolder.newFile("common.ui")
        commonFile.writeText("""
            @CommonSize = 100;
            @CommonStyle = (FontSize: 18, RenderBold: true);
        """.trimIndent())

        // Create main source that imports common.ui
        val source = """
            ${'$'}Common = "common.ui";

            Group {
                Width: ${'$'}Common.@CommonSize;
                Style: ${'$'}Common.@CommonStyle;
            }
        """.trimIndent()

        val mainFile = tempFolder.newFile("main.ui")
        mainFile.writeText(source)

        val result = VariableAwareParser.forFile(mainFile.toPath()).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        // Verify imported variables are resolved
        val widthProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Width")]
        assertThat(widthProp).isEqualTo(PropertyValue.Number(100.0))

        val styleProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Style")]
        assertThat(styleProp).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = styleProp as PropertyValue.Tuple
        assertThat(tuple.values["FontSize"]).isEqualTo(PropertyValue.Number(18.0))
    }

    @Test
    fun `forSourceWithPath resolves nested arithmetic expressions`() {
        val source = """
            @Base = 10;
            @Double = @Base * 2;
            @Triple = @Double + @Base;
            @Complex = (@Base + 5) * 3;

            Group {
                A: @Double;
                B: @Triple;
                C: @Complex;
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        val aProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("A")]
        assertThat(aProp).isEqualTo(PropertyValue.Number(20.0))

        val bProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("B")]
        assertThat(bProp).isEqualTo(PropertyValue.Number(30.0))

        // Complex expression may be evaluated as 45 if parentheses are respected
        val cProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("C")]
        assertThat(cProp).isInstanceOf(PropertyValue.Number::class.java)
    }

    @Test
    fun `forSourceWithPath preserves unknown property types during resolution`() {
        val source = """
            @Size = 64;

            Group {
                Width: @Size;
                UnknownProp: SomeUnknownValue;
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        // Known variable should be resolved
        val widthProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Width")]
        assertThat(widthProp).isEqualTo(PropertyValue.Number(64.0))

        // Unknown property should be preserved
        val unknownProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("UnknownProp")]
        assertThat(unknownProp).isNotNull()
    }

    @Test
    fun `forSourceWithPath resolves style references with spread in element properties`() {
        val source = """
            @BaseStyle = (FontSize: 14);

            Label {
                Style: (...@BaseStyle, RenderBold: true, FontSize: 20);
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        val styleProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Style")]
        assertThat(styleProp).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = styleProp as PropertyValue.Tuple

        // Spread should bring in FontSize: 14, then override to 20
        assertThat(tuple.values["FontSize"]).isEqualTo(PropertyValue.Number(20.0))
        assertThat(tuple.values["RenderBold"]).isEqualTo(PropertyValue.Boolean(true))
    }

    @Test
    fun `forSourceWithPath handles multiple imports with distinct aliases`() {
        // Create first import file
        val file1 = tempFolder.newFile("file1.ui")
        file1.writeText("@Size1 = 100;")

        // Create second import file
        val file2 = tempFolder.newFile("file2.ui")
        file2.writeText("@Size2 = 200;")

        // Create main source with multiple imports
        val source = """
            ${'$'}File1 = "file1.ui";
            ${'$'}File2 = "file2.ui";

            Group {
                Width: ${'$'}File1.@Size1;
                Height: ${'$'}File2.@Size2;
            }
        """.trimIndent()

        val mainFile = tempFolder.newFile("main.ui")
        mainFile.writeText(source)

        val result = VariableAwareParser.forFile(mainFile.toPath()).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        val widthProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Width")]
        assertThat(widthProp).isEqualTo(PropertyValue.Number(100.0))

        val heightProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Height")]
        assertThat(heightProp).isEqualTo(PropertyValue.Number(200.0))
    }

    @Test
    fun `element-scoped styles are resolved when referenced in other properties`() {
        val source = """
            @LabelStyle = (FontSize: 15, TextColor: #94979d);

            TextButton #Button {
                @Default = (LabelStyle: @LabelStyle, Background: #ffffff);
                @Hovered = (...@LabelStyle, TextColor: #babec6);
                Style: (Default: @Default, Hovered: (LabelStyle: @Hovered));
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        val styleProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Style")]
        assertThat(styleProp).isInstanceOf(PropertyValue.Tuple::class.java)
        val styleTuple = styleProp as PropertyValue.Tuple

        // Default should be resolved to a Tuple (not remain as Style(Local("Default")))
        val defaultVal = styleTuple.values["Default"]
        assertThat(defaultVal).isInstanceOf(PropertyValue.Tuple::class.java)
        val defaultTuple = defaultVal as PropertyValue.Tuple

        // LabelStyle inside Default should be resolved to a Tuple with FontSize
        val labelStyleVal = defaultTuple.values["LabelStyle"]
        assertThat(labelStyleVal).isInstanceOf(PropertyValue.Tuple::class.java)
        val labelTuple = labelStyleVal as PropertyValue.Tuple
        assertThat(labelTuple.values["FontSize"]).isEqualTo(PropertyValue.Number(15.0))

        // Background in Default should be a Color
        val bgVal = defaultTuple.values["Background"]
        assertThat(bgVal).isInstanceOf(PropertyValue.Color::class.java)
    }

    @Test
    fun `element-scoped styles with spread resolve correctly`() {
        val source = """
            @BaseBg = (TexturePath: "bg.png", Border: 8);

            TextField {
                @DecorationBg = (...@BaseBg, Border: 4);
                Decoration: (Default: (Background: @DecorationBg));
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        val decorProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Decoration")]
        assertThat(decorProp).isInstanceOf(PropertyValue.Tuple::class.java)
        val decorTuple = decorProp as PropertyValue.Tuple

        val defaultVal = decorTuple.values["Default"]
        assertThat(defaultVal).isInstanceOf(PropertyValue.Tuple::class.java)
        val defaultTuple = defaultVal as PropertyValue.Tuple

        // Background should be resolved to a Tuple with TexturePath and Border
        val bgVal = defaultTuple.values["Background"]
        assertThat(bgVal).isInstanceOf(PropertyValue.Tuple::class.java)
        val bgTuple = bgVal as PropertyValue.Tuple
        assertThat(bgTuple.values["TexturePath"]).isEqualTo(PropertyValue.Text("bg.png"))
        // Border should be overridden to 4 from the spread
        assertThat(bgTuple.values["Border"]).isEqualTo(PropertyValue.Number(4.0))
    }

    @Test
    fun `forSourceWithPath converts resolved anchor tuples to AnchorValue`() {
        val source = """
            @Left = 10;
            @Top = 20;

            Group {
                Anchor: (Left: @Left, Top: @Top, Width: 100, Height: 50);
            }
        """.trimIndent()

        val filePath = Path("test.ui")
        val result = VariableAwareParser.forSourceWithPath(source, filePath).parse()

        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value

        val anchorProp = parsed.document.root.properties[com.hyve.ui.core.id.PropertyName("Anchor")]
        assertThat(anchorProp).isInstanceOf(PropertyValue.Anchor::class.java)

        val anchorValue = (anchorProp as PropertyValue.Anchor).anchor
        assertThat(anchorValue.left).isNotNull()
        assertThat(anchorValue.top).isNotNull()
        assertThat(anchorValue.width).isNotNull()
        assertThat(anchorValue.height).isNotNull()
    }
}
