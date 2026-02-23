// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.parser

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.result.Result
import com.hyve.ui.exporter.UIExporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for parser ↔ exporter symmetry.
 *
 * Covers fixes for:
 * 1. Element-scoped style round-trip (type constructor, element-based, tuple)
 * 2. Negative percentage parsing (Percent constraint removed)
 * 3. Null literal round-trip
 * 4. ImagePath / FontPath property-name-aware upgrading
 */
class ParserExporterSymmetryTest {

    // =====================================================================
    // Fix 1: Element-scoped style round-trip
    // =====================================================================

    @Test
    fun `element-scoped tuple style round-trips`() {
        val source = """
            Group {
                @HoverState = (Background: #ff0000, FontSize: 16);
                Text: "Hello";
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("@HoverState = (Background: #ff0000, FontSize: 16);")
    }

    @Test
    fun `element-scoped type constructor style round-trips`() {
        val source = """
            Group {
                @PopupStyle = PopupMenuLayerStyle(Background: #2e2e2e, Border: 8);
                Text: "Hello";
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("@PopupStyle = PopupMenuLayerStyle(Background: #2e2e2e, Border: 8);")
    }

    @Test
    fun `element-scoped type constructor style preserves typeName across double round-trip`() {
        val source = """
            Group {
                @MyStyle = CustomType(Prop1: 10, Prop2: true);
                Style: @MyStyle;
            }
        """.trimIndent()

        val first = roundTrip(source)
        val second = roundTrip(first)

        assertThat(second).isEqualTo(first)
        assertThat(second).contains("CustomType(")
    }

    @Test
    fun `element-scoped element-based style round-trips`() {
        val source = """
            Group {
                @FooterButton = TextButton {
                    Text: "Click";
                };
                Text: "Hello";
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("@FooterButton = TextButton {")
        assertThat(exported).contains("Text: \"Click\";")
        assertThat(exported).contains("};")
    }

    @Test
    fun `element-scoped element-based style with children round-trips`() {
        val source = """
            Group {
                @Template = Group {
                    LayoutMode: Top;
                    Label {
                        Text: "Child";
                    }
                };
                Text: "Hello";
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("@Template = Group {")
        assertThat(exported).contains("LayoutMode: Top;")
        assertThat(exported).contains("Label {")
        assertThat(exported).contains("Text: \"Child\";")
    }

    @Test
    fun `element-scoped simple value style round-trips`() {
        val source = """
            Group {
                @Label = "";
                @Min = 0;
                Text: "Hello";
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("@Label = \"\";")
        assertThat(exported).contains("@Min = 0;")
    }

    @Test
    fun `element-scoped style reference value round-trips`() {
        val source = """
            Group {
                @Alias = @OtherStyle;
                Text: "Hello";
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("@Alias = @OtherStyle;")
    }

    @Test
    fun `element-scoped type constructor with spread round-trips`() {
        val source = """
            Group {
                @Extended = ExtendedType(...@Base, Extra: 42);
                Text: "Hello";
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("@Extended = ExtendedType(...@Base, Extra: 42);")
    }

    // =====================================================================
    // Fix 2: Negative percentage handling
    // =====================================================================

    @Test
    fun `negative percentage parses without exception`() {
        val source = """
            Group {
                Offset: -10%;
            }
        """.trimIndent()

        val parser = UIParser(source)
        val result = parser.parse()

        assertThat(result.isSuccess()).`as`("Parse should succeed for negative percent").isTrue()

        val doc = (result as Result.Success).value
        val offset = doc.root.properties[PropertyName("Offset")]
        assertThat(offset).isInstanceOf(PropertyValue.Percent::class.java)
        assertThat((offset as PropertyValue.Percent).ratio).isEqualTo(-0.1)
    }

    @Test
    fun `negative percentage round-trips`() {
        val source = """
            Group {
                Offset: -10%;
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("-10%")
    }

    @Test
    fun `negative percentage toString formats correctly`() {
        val percent = PropertyValue.Percent(-0.25)
        assertThat(percent.toString()).isEqualTo("-25%")
    }

    @Test
    fun `zero percent still works after constraint removal`() {
        val zero = PropertyValue.Percent(0.0)
        assertThat(zero.ratio).isEqualTo(0.0)
        assertThat(zero.percentage).isEqualTo(0.0)
    }

    @Test
    fun `100 percent still works after constraint removal`() {
        val full = PropertyValue.Percent(1.0)
        assertThat(full.ratio).isEqualTo(1.0)
        assertThat(full.percentage).isEqualTo(100.0)
    }

    // =====================================================================
    // Fix 3: Null literal round-trip
    // =====================================================================

    @Test
    fun `null literal parses as PropertyValue Null`() {
        val source = """
            Group {
                Value: null;
            }
        """.trimIndent()

        val parser = UIParser(source)
        val result = parser.parse()

        assertThat(result.isSuccess()).isTrue()

        val doc = (result as Result.Success).value
        val value = doc.root.properties[PropertyName("Value")]
        assertThat(value).isEqualTo(PropertyValue.Null)
    }

    @Test
    fun `null literal round-trips to identical text`() {
        val source = """
            Group {
                Value: null;
            }
        """.trimIndent()

        val exported = roundTrip(source)

        assertThat(exported).contains("Value: null;")
    }

    @Test
    fun `null literal preserves type across double round-trip`() {
        val source = """
            Group {
                Value: null;
            }
        """.trimIndent()

        val doc = parseDoc(source)
        val firstExport = export(doc)
        val reparsedDoc = parseDoc(firstExport)
        val secondExport = export(reparsedDoc)

        // Both exports should be identical
        assertThat(secondExport).isEqualTo(firstExport)

        // The value should be Null in both parsed documents
        assertThat(doc.root.properties[PropertyName("Value")]).isEqualTo(PropertyValue.Null)
        assertThat(reparsedDoc.root.properties[PropertyName("Value")]).isEqualTo(PropertyValue.Null)
    }

    @Test
    fun `null is not confused with unquoted text identifier`() {
        val source = """
            Group {
                NullProp: null;
                EnumProp: Center;
            }
        """.trimIndent()

        val doc = parseDoc(source)

        assertThat(doc.root.properties[PropertyName("NullProp")]).isEqualTo(PropertyValue.Null)
        assertThat(doc.root.properties[PropertyName("EnumProp")])
            .isEqualTo(PropertyValue.Text("Center", quoted = false))
    }

    // =====================================================================
    // Fix 4: ImagePath / FontPath property-name-aware upgrading
    // =====================================================================

    @Test
    fun `Source property string upgrades to ImagePath`() {
        val source = """
            Image {
                Source: "textures/icon.png";
            }
        """.trimIndent()

        val doc = parseDoc(source)
        // Parser applies abstraction: AssetImage -> Image (reverse), but we parse as Image
        val img = doc.root
        val sourceProp = img.properties[PropertyName("Source")]

        assertThat(sourceProp).isInstanceOf(PropertyValue.ImagePath::class.java)
        assertThat((sourceProp as PropertyValue.ImagePath).path).isEqualTo("textures/icon.png")
    }

    @Test
    fun `Font property string upgrades to FontPath`() {
        val source = """
            Label {
                Font: "fonts/roboto.ttf";
            }
        """.trimIndent()

        val doc = parseDoc(source)
        val fontProp = doc.root.properties[PropertyName("Font")]

        assertThat(fontProp).isInstanceOf(PropertyValue.FontPath::class.java)
        assertThat((fontProp as PropertyValue.FontPath).path).isEqualTo("fonts/roboto.ttf")
    }

    @Test
    fun `ImagePath round-trips through export and re-parse`() {
        val source = """
            Image {
                Source: "textures/sword.png";
            }
        """.trimIndent()

        val doc = parseDoc(source)
        assertThat(doc.root.properties[PropertyName("Source")])
            .isInstanceOf(PropertyValue.ImagePath::class.java)

        val exported = export(doc)
        assertThat(exported).contains("Source: \"textures/sword.png\";")

        val reparsed = parseDoc(exported)
        assertThat(reparsed.root.properties[PropertyName("Source")])
            .isInstanceOf(PropertyValue.ImagePath::class.java)
    }

    @Test
    fun `FontPath round-trips through export and re-parse`() {
        val source = """
            Label {
                Font: "fonts/arial.ttf";
            }
        """.trimIndent()

        val doc = parseDoc(source)
        assertThat(doc.root.properties[PropertyName("Font")])
            .isInstanceOf(PropertyValue.FontPath::class.java)

        val exported = export(doc)
        val reparsed = parseDoc(exported)
        assertThat(reparsed.root.properties[PropertyName("Font")])
            .isInstanceOf(PropertyValue.FontPath::class.java)
    }

    @Test
    fun `BackgroundImage property upgrades to ImagePath`() {
        val source = """
            Group {
                BackgroundImage: "textures/bg.png";
            }
        """.trimIndent()

        val doc = parseDoc(source)
        val prop = doc.root.properties[PropertyName("BackgroundImage")]

        assertThat(prop).isInstanceOf(PropertyValue.ImagePath::class.java)
    }

    @Test
    fun `Icon property upgrades to ImagePath`() {
        val source = """
            Button {
                Icon: "textures/close.png";
            }
        """.trimIndent()

        val doc = parseDoc(source)
        // Button -> TextButton abstraction, but property still exists
        val prop = doc.root.properties[PropertyName("Icon")]

        assertThat(prop).isInstanceOf(PropertyValue.ImagePath::class.java)
    }

    @Test
    fun `non-image property stays as Text`() {
        val source = """
            Label {
                Text: "hello world";
            }
        """.trimIndent()

        val doc = parseDoc(source)
        val textProp = doc.root.properties[PropertyName("Text")]

        assertThat(textProp).isInstanceOf(PropertyValue.Text::class.java)
    }

    @Test
    fun `unquoted identifier is not upgraded even for image property name`() {
        val source = """
            Image {
                Source: Default;
            }
        """.trimIndent()

        val doc = parseDoc(source)
        val sourceProp = doc.root.properties[PropertyName("Source")]

        // Unquoted identifier — should NOT be upgraded to ImagePath
        assertThat(sourceProp).isInstanceOf(PropertyValue.Text::class.java)
        assertThat((sourceProp as PropertyValue.Text).quoted).isFalse()
    }

    @Test
    fun `TexturePath inside tuple stays as Text (not upgraded)`() {
        // Values inside tuples are not upgraded — only top-level element properties
        val source = """
            Group {
                Background: (TexturePath: "bg.png", Border: 8);
            }
        """.trimIndent()

        val doc = parseDoc(source)
        val bg = doc.root.properties[PropertyName("Background")] as PropertyValue.Tuple
        val texturePath = bg.values["TexturePath"]

        assertThat(texturePath).isInstanceOf(PropertyValue.Text::class.java)
    }

    @Test
    fun `FontName property upgrades to FontPath`() {
        val source = """
            Label {
                FontName: "arial.ttf";
            }
        """.trimIndent()

        val doc = parseDoc(source)
        val prop = doc.root.properties[PropertyName("FontName")]

        assertThat(prop).isInstanceOf(PropertyValue.FontPath::class.java)
    }

    // =====================================================================
    // Combined round-trip: all fixes together
    // =====================================================================

    @Test
    fun `complex file with all fix types round-trips stably`() {
        val source = """
            Group #Main {
                @ButtonStyle = TextButtonStyle(Background: #333333, FontSize: 14);
                @NullValue = 0;
                Source: "textures/bg.png";
                Offset: -10%;
                Value: null;

                Label {
                    Text: "Hello";
                    Font: "fonts/default.ttf";
                }
            }
        """.trimIndent()

        val first = roundTrip(source)
        val second = roundTrip(first)

        assertThat(second).isEqualTo(first)
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private fun parseDoc(source: String): UIDocument {
        val parser = UIParser(source)
        val result = parser.parse()
        assertThat(result.isSuccess()).`as`("Parse should succeed").isTrue()
        return (result as Result.Success).value
    }

    private fun export(doc: UIDocument): String {
        val exporter = UIExporter()
        val result = exporter.export(doc)
        assertThat(result.isSuccess()).`as`("Export should succeed").isTrue()
        return (result as Result.Success).value
    }

    private fun roundTrip(source: String): String {
        return export(parseDoc(source))
    }
}
