// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.parser

import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.result.Result
import com.hyve.ui.exporter.UIExporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.path.writeText

/**
 * Tests for template expansion — element-based style definitions
 * (e.g., @Container = Group { ... }) that are instantiated via
 * @Container #Id { ... } or $Alias.@Container #Id { ... }.
 */
class TemplateExpansionTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    // --- Local template expansion ---

    @Test
    fun `local template expands StylePrefixedElement to concrete type`() {
        val source = """
            @MyGroup = Group {
                LayoutMode: Top;
            };

            @MyGroup #Panel {
                Background: #ff0000;
            }
        """.trimIndent()

        val result = VariableAwareParser.forSource(source).parse()

        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value.document

        // Should expand to Group, not _StylePrefixedElement
        assertThat(doc.root.type.value).isEqualTo("Group")
        assertThat(doc.root.id?.value).isEqualTo("Panel")

        // Template properties + instance properties merged
        assertThat(doc.root.properties[PropertyName("LayoutMode")])
            .isEqualTo(PropertyValue.Text("Top", quoted = false))
        assertThat(doc.root.properties[PropertyName("Background")])
            .isInstanceOf(PropertyValue.Color::class.java)

        // Preserve _stylePrefix for round-trip export
        assertThat(doc.root.properties[PropertyName("_stylePrefix")])
            .isEqualTo(PropertyValue.Text("@MyGroup"))
    }

    @Test
    fun `local template with children preserves template children`() {
        val source = """
            @Container = Group {
                LayoutMode: Top;

                Group #Title {
                    FontSize: 24;
                }

                Group #Content {
                    FontSize: 14;
                }
            };

            @Container #Panel {
            }
        """.trimIndent()

        val result = VariableAwareParser.forSource(source).parse()

        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value.document

        assertThat(doc.root.type.value).isEqualTo("Group")
        assertThat(doc.root.id?.value).isEqualTo("Panel")
        assertThat(doc.root.children).hasSize(2)
        assertThat(doc.root.children[0].id?.value).isEqualTo("Title")
        assertThat(doc.root.children[1].id?.value).isEqualTo("Content")
    }

    // --- Child override merging ---

    @Test
    fun `IdOnlyBlock children merge onto matching template children`() {
        val source = """
            @Container = Group {
                Group #Title {
                    FontSize: 24;
                }

                Group #Content {
                    FontSize: 14;
                }
            };

            @Container #Panel {
                #Title {
                    Text: "Hello";
                }
                #Content {
                    Text: "World";
                }
            }
        """.trimIndent()

        val result = VariableAwareParser.forSource(source).parse()

        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value.document

        assertThat(doc.root.type.value).isEqualTo("Group")
        assertThat(doc.root.children).hasSize(2)

        // #Title: template FontSize + instance Text
        val title = doc.root.children[0]
        assertThat(title.id?.value).isEqualTo("Title")
        assertThat(title.properties[PropertyName("FontSize")])
            .isEqualTo(PropertyValue.Number(24.0))
        assertThat(title.properties[PropertyName("Text")])
            .isEqualTo(PropertyValue.Text("Hello"))

        // #Content: template FontSize + instance Text
        val content = doc.root.children[1]
        assertThat(content.id?.value).isEqualTo("Content")
        assertThat(content.properties[PropertyName("FontSize")])
            .isEqualTo(PropertyValue.Number(14.0))
        assertThat(content.properties[PropertyName("Text")])
            .isEqualTo(PropertyValue.Text("World"))
    }

    // --- Property merging (instance overrides template) ---

    @Test
    fun `instance properties override template properties`() {
        val source = """
            @MyButton = Group {
                Background: #000000;
                FontSize: 14;
            };

            @MyButton #Btn {
                Background: #ffffff;
            }
        """.trimIndent()

        val result = VariableAwareParser.forSource(source).parse()

        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value.document

        // Background should be overridden by instance
        val bg = doc.root.properties[PropertyName("Background")] as PropertyValue.Color
        assertThat(bg.hex).isEqualTo("#ffffff")

        // FontSize should come from template
        assertThat(doc.root.properties[PropertyName("FontSize")])
            .isEqualTo(PropertyValue.Number(14.0))
    }

    // --- Abstraction mapping ---

    @Test
    fun `TextButton template expands to Button type`() {
        val source = """
            @FooterButton = TextButton {
                FontSize: 16;
            };

            @FooterButton #Submit {
                Text: "OK";
            }
        """.trimIndent()

        val result = VariableAwareParser.forSource(source).parse()

        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value.document

        // TextButton → Button (abstraction mapping)
        assertThat(doc.root.type.value).isEqualTo("Button")
        assertThat(doc.root.id?.value).isEqualTo("Submit")
    }

    @Test
    fun `AssetImage template expands to Image type`() {
        val source = """
            @Icon = AssetImage {
                Source: "icon.png";
            };

            @Icon #Logo {
            }
        """.trimIndent()

        val result = VariableAwareParser.forSource(source).parse()

        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value.document

        assertThat(doc.root.type.value).isEqualTo("Image")
    }

    // --- Cross-file template expansion ---

    @Test
    fun `cross-file template expansion via VariableRefElement`() {
        val dir = tempFolder.newFolder("ui")

        // Common.ui defines a template
        val commonPath = dir.toPath().resolve("Common.ui")
        commonPath.writeText("""
            @Container = Group {
                LayoutMode: Top;

                Group #Title {
                    FontSize: 24;
                }

                Group #Body {
                    FontSize: 14;
                }
            };
        """.trimIndent())

        // Main.ui imports and uses it
        val mainPath = dir.toPath().resolve("Main.ui")
        val mainSource = """
            ${'$'}Common = "Common.ui";

            ${'$'}Common.@Container #Panel {
                #Title {
                    Text: "Welcome";
                }
            }
        """.trimIndent()

        val result = VariableAwareParser.forSourceWithPath(mainSource, mainPath).parse()

        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value.document

        // Should expand to Group, not _VariableRefElement
        assertThat(doc.root.type.value).isEqualTo("Group")
        assertThat(doc.root.id?.value).isEqualTo("Panel")

        // Template properties should be present
        assertThat(doc.root.properties[PropertyName("LayoutMode")])
            .isEqualTo(PropertyValue.Text("Top", quoted = false))

        // Children: template's #Title (with override) and #Body
        assertThat(doc.root.children).hasSize(2)

        val title = doc.root.children[0]
        assertThat(title.id?.value).isEqualTo("Title")
        assertThat(title.properties[PropertyName("FontSize")])
            .isEqualTo(PropertyValue.Number(24.0))
        assertThat(title.properties[PropertyName("Text")])
            .isEqualTo(PropertyValue.Text("Welcome"))

        val body = doc.root.children[1]
        assertThat(body.id?.value).isEqualTo("Body")
    }

    // --- Graceful fallback ---

    @Test
    fun `unknown template gracefully falls back to original element type`() {
        val source = """
            @UnknownTemplate #Panel {
                Text: "Hello";
            }
        """.trimIndent()

        val result = VariableAwareParser.forSource(source).parse()

        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value.document

        // Should stay as _StylePrefixedElement since @UnknownTemplate is not defined
        assertThat(doc.root.type.value).isEqualTo("_StylePrefixedElement")
    }

    @Test
    fun `unknown cross-file template stays as VariableRefElement`() {
        val source = """
            ${'$'}Missing.@Template #Panel {
                Text: "Hello";
            }
        """.trimIndent()

        val result = VariableAwareParser.forSource(source).parse()

        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value.document

        assertThat(doc.root.type.value).isEqualTo("_VariableRefElement")
    }

    // --- Export round-trip ---
    // Note: The editor exports from the raw document (UIParser output), not the expanded
    // document. These tests verify that raw parse → export preserves template reference syntax.

    @Test
    fun `export preserves original StylePrefixedElement syntax from raw document`() {
        val source = """
            @MyGroup = Group {
                LayoutMode: Top;
            };

            @MyGroup #Panel {
                Background: #ff0000;
            }
        """.trimIndent()

        // Parse with UIParser (raw, no expansion)
        val result = UIParser(source).parse()
        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value

        val exporter = UIExporter()
        val exportResult = exporter.export(doc)
        assertThat(exportResult.isSuccess()).isTrue()
        val exported = (exportResult as Result.Success<String>).value

        // The exported text should contain the @MyGroup syntax
        assertThat(exported).contains("@MyGroup #Panel {")
    }

    @Test
    fun `export preserves original VariableRefElement syntax from raw document`() {
        val source = """
            ${'$'}Common = "Common.ui";

            ${'$'}Common.@Container #Panel {
                Background: #ff0000;
            }
        """.trimIndent()

        // Parse with UIParser (raw, no expansion)
        val result = UIParser(source).parse()
        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value

        val exporter = UIExporter()
        val exportResult = exporter.export(doc)
        assertThat(exportResult.isSuccess()).isTrue()
        val exported = (exportResult as Result.Success<String>).value

        // Should preserve $Common.@Container syntax
        assertThat(exported).contains("\$Common.@Container #Panel {")
    }

    // --- Style definition children parse and export round-trip ---

    @Test
    fun `element-based style definition preserves children through parse and export`() {
        val source = """
            @Container = Group {
                LayoutMode: Top;

                Label #Title {
                    FontSize: 24;
                }
            };
        """.trimIndent()

        val parser = UIParser(source)
        val result = parser.parse()
        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value

        // Style should have children
        val styleDef = doc.styles.values.first()
        assertThat(styleDef.elementType).isEqualTo("Group")
        assertThat(styleDef.children).hasSize(1)
        assertThat(styleDef.children[0].type.value).isEqualTo("Label")
        assertThat(styleDef.children[0].id?.value).isEqualTo("Title")

        // Export should include children
        val exporter = UIExporter()
        val exportResult = exporter.export(doc)
        assertThat(exportResult.isSuccess()).isTrue()
        val exported = (exportResult as Result.Success<String>).value

        assertThat(exported).contains("Label #Title {")
        assertThat(exported).contains("FontSize: 24;")
    }

    // --- Nested template expansion ---

    @Test
    fun `template expansion inside regular element children`() {
        val source = """
            @MyLabel = Label {
                FontSize: 16;
            };

            Group #Root {
                @MyLabel #Title {
                    Text: "Hello";
                }
            }
        """.trimIndent()

        val result = VariableAwareParser.forSource(source).parse()

        assertThat(result.isSuccess()).isTrue()
        val doc = (result as Result.Success).value.document

        assertThat(doc.root.type.value).isEqualTo("Group")
        assertThat(doc.root.children).hasSize(1)

        val child = doc.root.children[0]
        assertThat(child.type.value).isEqualTo("Label")
        assertThat(child.id?.value).isEqualTo("Title")
        assertThat(child.properties[PropertyName("FontSize")])
            .isEqualTo(PropertyValue.Number(16.0))
        assertThat(child.properties[PropertyName("Text")])
            .isEqualTo(PropertyValue.Text("Hello"))
    }
}
