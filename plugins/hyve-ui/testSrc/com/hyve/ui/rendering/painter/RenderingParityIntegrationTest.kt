// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.rendering.painter

import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.result.Result
import com.hyve.ui.parser.VariableAwareParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * End-to-end integration tests: parse a mini .ui source with styles,
 * then verify resolved tuples on elements match what draw methods would extract.
 * Uses VariableAwareParser.forSourceWithPath + TemporaryFolder for search paths.
 */
class RenderingParityIntegrationTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    /** Parse source with Common.ui in a search path and return the resolved root element. */
    private fun parseWithCommon(commonContent: String, mainSource: String): com.hyve.ui.core.domain.elements.UIElement {
        val vanillaDir = tempFolder.newFolder("vanilla")
        val projectDir = tempFolder.newFolder("project")
        File(vanillaDir, "Common.ui").writeText(commonContent)

        val mainFilePath = projectDir.toPath().resolve("Main.ui")
        val result = VariableAwareParser.forSourceWithPath(
            mainSource, mainFilePath,
            importSearchPaths = listOf(vanillaDir.toPath())
        ).parse()

        assertThat(result.isSuccess()).isTrue()
        return (result as Result.Success).value.document.root
    }

    @Test
    fun `TextField with imported PatchStyle Background resolves to nine-patch tuple`() {
        val common = """
            @InputBoxBackground = (TexturePath: "textures/input_bg.png", Border: 16);
        """.trimIndent()

        val main = """
            ${'$'}Common = "Common.ui";

            TextField {
                Background: ${'$'}Common.@InputBoxBackground;
                Text: "Enter text...";
            }
        """.trimIndent()

        val root = parseWithCommon(common, main)
        val bgProp = root.properties[PropertyName("Background")]
        assertThat(bgProp).isInstanceOf(PropertyValue.Tuple::class.java)

        val bgTuple = bgProp as PropertyValue.Tuple
        val texPath = (bgTuple.get("TexturePath") as? PropertyValue.Text)?.value
        val border = (bgTuple.get("Border") as? PropertyValue.Number)?.value?.toFloat()

        assertThat(texPath).isEqualTo("textures/input_bg.png")
        assertThat(border).isEqualTo(16f)
    }

    @Test
    fun `DropdownBox with imported DropdownBoxStyle resolves all sub-properties`() {
        val common = """
            @DefaultDropdownBoxStyle = (
                DefaultBackground: (TexturePath: "textures/dropdown_bg.png", Border: 12),
                DefaultArrowTexturePath: "textures/arrow_down.png",
                ArrowWidth: 10,
                ArrowHeight: 8,
                HorizontalPadding: 6,
                LabelStyle: (TextColor: "#FFFFFF", FontSize: 14, RenderBold: true, RenderUppercase: false)
            );
        """.trimIndent()

        val main = """
            ${'$'}Common = "Common.ui";

            DropdownBox {
                Style: ${'$'}Common.@DefaultDropdownBoxStyle;
                Placeholder: "Select...";
            }
        """.trimIndent()

        val root = parseWithCommon(common, main)
        val styleProp = root.properties[PropertyName("Style")]
        assertThat(styleProp).isInstanceOf(PropertyValue.Tuple::class.java)

        val styleTuple = resolveStyleToTuple(styleProp)
        assertThat(styleTuple).isNotNull

        // DefaultBackground
        val defaultBg = styleTuple!!.get("DefaultBackground") as? PropertyValue.Tuple
        assertThat(defaultBg).isNotNull
        assertThat((defaultBg!!.get("TexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/dropdown_bg.png")

        // Arrow
        assertThat((styleTuple.get("DefaultArrowTexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/arrow_down.png")
        assertThat((styleTuple.get("ArrowWidth") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(10f)
        assertThat((styleTuple.get("ArrowHeight") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(8f)

        // LabelStyle
        val labelStyle = resolveStyleToTuple(styleTuple.get("LabelStyle"))
        assertThat(labelStyle).isNotNull
        assertThat((labelStyle!!.get("FontSize") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(14f)
    }

    @Test
    fun `CheckBox with imported CheckBoxStyle resolves Checked and Unchecked states`() {
        val common = """
            @DefaultCheckBoxStyle = (
                Checked: (DefaultBackground: (TexturePath: "textures/cb_checked.png")),
                Unchecked: (DefaultBackground: (Color: "#00000000"))
            );
        """.trimIndent()

        val main = """
            ${'$'}Common = "Common.ui";

            CheckBox {
                Style: ${'$'}Common.@DefaultCheckBoxStyle;
                Checked: true;
            }
        """.trimIndent()

        val root = parseWithCommon(common, main)
        val styleProp = root.properties[PropertyName("Style")]
        assertThat(styleProp).isInstanceOf(PropertyValue.Tuple::class.java)

        val styleTuple = resolveStyleToTuple(styleProp)
        assertThat(styleTuple).isNotNull

        // Checked state
        val checked = styleTuple!!.get("Checked") as? PropertyValue.Tuple
        assertThat(checked).isNotNull
        val checkedBg = checked!!.get("DefaultBackground") as? PropertyValue.Tuple
        assertThat(checkedBg).isNotNull
        assertThat((checkedBg!!.get("TexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/cb_checked.png")

        // Unchecked state
        val unchecked = styleTuple.get("Unchecked") as? PropertyValue.Tuple
        assertThat(unchecked).isNotNull
        val uncheckedBg = unchecked!!.get("DefaultBackground") as? PropertyValue.Tuple
        assertThat(uncheckedBg).isNotNull
        assertThat(uncheckedBg!!.get("Color")).isNotNull
    }

    @Test
    fun `Slider with imported SliderStyle resolves Background and Handle`() {
        val common = """
            @DefaultSliderStyle = (
                Background: (TexturePath: "textures/slider_track.png", Border: 8),
                Handle: "textures/slider_handle.png",
                HandleWidth: 20,
                HandleHeight: 20
            );
        """.trimIndent()

        val main = """
            ${'$'}Common = "Common.ui";

            Slider {
                SliderStyle: ${'$'}Common.@DefaultSliderStyle;
                Value: 0.5;
                Min: 0;
                Max: 100;
            }
        """.trimIndent()

        val root = parseWithCommon(common, main)
        val sliderStyleProp = root.properties[PropertyName("SliderStyle")]
        assertThat(sliderStyleProp).isInstanceOf(PropertyValue.Tuple::class.java)

        val styleTuple = resolveStyleToTuple(sliderStyleProp)
        assertThat(styleTuple).isNotNull

        // Background sub-tuple
        val bg = styleTuple!!.get("Background") as? PropertyValue.Tuple
        assertThat(bg).isNotNull
        assertThat((bg!!.get("TexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/slider_track.png")
        assertThat((bg.get("Border") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(8f)

        // Handle
        assertThat((styleTuple.get("Handle") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/slider_handle.png")
        assertThat((styleTuple.get("HandleWidth") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(20f)
        assertThat((styleTuple.get("HandleHeight") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(20f)
    }

    @Test
    fun `Full SettingsPanel-like source resolves all widget styles from Common import`() {
        val common = """
            @InputBoxBackground = (TexturePath: "textures/input.png", Border: 16);
            @DefaultDropdownBoxStyle = (
                DefaultBackground: (TexturePath: "textures/dropdown.png", Border: 12),
                DefaultArrowTexturePath: "textures/arrow.png",
                ArrowWidth: 10,
                ArrowHeight: 8
            );
            @DefaultCheckBoxStyle = (
                Checked: (DefaultBackground: (TexturePath: "textures/checked.png")),
                Unchecked: (DefaultBackground: (Color: "#00000000"))
            );
            @DefaultSliderStyle = (
                Background: (TexturePath: "textures/track.png", Border: 8),
                Handle: "textures/handle.png",
                HandleWidth: 20,
                HandleHeight: 20
            );
        """.trimIndent()

        val main = """
            ${'$'}Common = "Common.ui";

            Group {
                TextField {
                    Background: ${'$'}Common.@InputBoxBackground;
                }
                DropdownBox {
                    Style: ${'$'}Common.@DefaultDropdownBoxStyle;
                }
                CheckBox {
                    Style: ${'$'}Common.@DefaultCheckBoxStyle;
                }
                Slider {
                    SliderStyle: ${'$'}Common.@DefaultSliderStyle;
                }
            }
        """.trimIndent()

        val root = parseWithCommon(common, main)
        assertThat(root.children).hasSize(4)

        // TextField
        val textField = root.children[0]
        val tfBg = resolveStyleToTuple(textField.properties[PropertyName("Background")])
        assertThat(tfBg).isNotNull
        assertThat((tfBg!!.get("TexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/input.png")

        // DropdownBox
        val dropdown = root.children[1]
        val ddStyle = resolveStyleToTuple(dropdown.properties[PropertyName("Style")])
        assertThat(ddStyle).isNotNull
        assertThat(ddStyle!!.get("DefaultBackground")).isNotNull

        // CheckBox
        val checkBox = root.children[2]
        val cbStyle = resolveStyleToTuple(checkBox.properties[PropertyName("Style")])
        assertThat(cbStyle).isNotNull
        assertThat(cbStyle!!.get("Checked")).isNotNull
        assertThat(cbStyle.get("Unchecked")).isNotNull

        // Slider
        val slider = root.children[3]
        val slStyle = resolveStyleToTuple(slider.properties[PropertyName("SliderStyle")])
        assertThat(slStyle).isNotNull
        assertThat(slStyle!!.get("Background")).isNotNull
        assertThat(slStyle.get("Handle")).isNotNull
    }

    @Test
    fun `Search path import with spread operator preserves merged values`() {
        val common = """
            @Base = (FontSize: 14, RenderBold: false);
        """.trimIndent()

        val main = """
            ${'$'}Common = "Common.ui";

            @Derived = (...${'$'}Common.@Base, FontSize: 24, RenderUppercase: true);

            Label {
                Style: @Derived;
            }
        """.trimIndent()

        val root = parseWithCommon(common, main)
        val styleProp = root.properties[PropertyName("Style")]
        assertThat(styleProp).isInstanceOf(PropertyValue.Tuple::class.java)

        val tuple = styleProp as PropertyValue.Tuple
        // FontSize: 24 overrides base 14
        assertThat((tuple.values["FontSize"] as? PropertyValue.Number)?.value).isEqualTo(24.0)
        // RenderBold from spread base
        assertThat((tuple.values["RenderBold"] as? PropertyValue.Boolean)?.value).isFalse()
        // New override
        assertThat((tuple.values["RenderUppercase"] as? PropertyValue.Boolean)?.value).isTrue()
    }
}
