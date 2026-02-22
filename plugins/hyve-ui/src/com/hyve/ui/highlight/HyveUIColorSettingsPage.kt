// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.highlight

import com.hyve.ui.HyveUIFileType
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

/**
 * Color settings page for Hytale UI syntax highlighting.
 * Accessible at Settings > Editor > Color Scheme > Hytale UI.
 */
class HyveUIColorSettingsPage : ColorSettingsPage {

    override fun getIcon(): Icon = HyveUIFileType.icon

    override fun getHighlighter(): SyntaxHighlighter = HyveUISyntaxHighlighter()

    override fun getDemoText(): String = DEMO_TEXT

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Hytale UI"

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Identifiers//Element type declaration", HyveUISyntaxHighlighter.ELEMENT_TYPE_NAME),
            AttributesDescriptor("Identifiers//Identifier", HyveUISyntaxHighlighter.IDENTIFIER),
            AttributesDescriptor("Literals//String", HyveUISyntaxHighlighter.STRING),
            AttributesDescriptor("Literals//Number", HyveUISyntaxHighlighter.NUMBER),
            AttributesDescriptor("Literals//Percentage", HyveUISyntaxHighlighter.PERCENT),
            AttributesDescriptor("Literals//Color", HyveUISyntaxHighlighter.COLOR),
            AttributesDescriptor("Literals//Boolean", HyveUISyntaxHighlighter.BOOLEAN),
            AttributesDescriptor("Literals//Localization key", HyveUISyntaxHighlighter.LOCALIZED_KEY),
            AttributesDescriptor("Prefixes//Variable ($)", HyveUISyntaxHighlighter.VARIABLE_PREFIX),
            AttributesDescriptor("Prefixes//Style (@)", HyveUISyntaxHighlighter.STYLE_PREFIX),
            AttributesDescriptor("Prefixes//ID (#)", HyveUISyntaxHighlighter.ID_PREFIX),
            AttributesDescriptor("Comments", HyveUISyntaxHighlighter.COMMENT),
            AttributesDescriptor("Operators//Operator", HyveUISyntaxHighlighter.OPERATOR),
            AttributesDescriptor("Operators//Colon", HyveUISyntaxHighlighter.COLON),
            AttributesDescriptor("Punctuation//Semicolon", HyveUISyntaxHighlighter.SEMICOLON),
            AttributesDescriptor("Punctuation//Comma", HyveUISyntaxHighlighter.COMMA),
            AttributesDescriptor("Punctuation//Dot", HyveUISyntaxHighlighter.DOT),
            AttributesDescriptor("Braces and brackets//Braces", HyveUISyntaxHighlighter.BRACES),
            AttributesDescriptor("Braces and brackets//Brackets", HyveUISyntaxHighlighter.BRACKETS),
            AttributesDescriptor("Braces and brackets//Parentheses", HyveUISyntaxHighlighter.PARENTHESES),
            AttributesDescriptor("Bad character", HyveUISyntaxHighlighter.BAD_CHARACTER),
        )

        private val DEMO_TEXT = """
// Main menu screen layout
${'$'}Common = "../../Common.ui";

/* Define reusable styles */
@HeaderStyle = (FontSize: 24, RenderBold: true);
@SubtitleStyle = (FontSize: 16, Color: #8A8A9A);

Group #MainMenu {
    LayoutMode: Top;
    BackgroundColor: #1A1A2E;
    Width: 100%;
    Height: 100%;
    Visible: true;

    Label #Title {
        Text: "Welcome to Hytale";
        ...HeaderStyle;
        Color: #F7A800;
        Alignment: Center;
        PaddingTop: 48;
    }

    Label #Subtitle {
        Text: %client.menu.subtitle;
        ...SubtitleStyle;
        Alignment: Center;
        PaddingTop: 8;
    }

    Group #ButtonContainer {
        LayoutMode: Top;
        Width: 300;
        Alignment: Center;
        PaddingTop: 32;
        Spacing: [0, 12];

        Button #PlayButton {
            Text: "Play";
            Width: 100%;
            Height: 48;
            Enabled: true;
        }

        Button #SettingsButton {
            Text: "Settings";
            Width: 100%;
            Height: 48;
        }
    }
}
""".trimIndent()
    }
}
