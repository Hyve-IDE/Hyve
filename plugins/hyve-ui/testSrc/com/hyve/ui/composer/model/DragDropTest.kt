// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DragDropTest {

    // -- Test helpers --

    private fun item(
        type: ComposerPropertyType,
        kind: WordBankKind,
        name: String = "@test",
    ) = WordBankItem(id = "test", name = name, type = type, kind = kind)

    private fun slot(
        type: ComposerPropertyType,
        name: String = "testSlot",
    ) = PropertySlot(name = name, type = type, category = SlotCategory.APPEARANCE)

    // -- canDrop: acceptance rules --

    @Test
    fun `should accept style item on style slot`() {
        val result = canDrop(
            item(ComposerPropertyType.STYLE, WordBankKind.STYLE),
            slot(ComposerPropertyType.STYLE),
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept exact type match for text`() {
        val result = canDrop(
            item(ComposerPropertyType.TEXT, WordBankKind.VARIABLE),
            slot(ComposerPropertyType.TEXT),
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept exact type match for color`() {
        val result = canDrop(
            item(ComposerPropertyType.COLOR, WordBankKind.VARIABLE),
            slot(ComposerPropertyType.COLOR),
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept exact type match for number`() {
        val result = canDrop(
            item(ComposerPropertyType.NUMBER, WordBankKind.VARIABLE),
            slot(ComposerPropertyType.NUMBER),
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept number item on percent slot`() {
        val result = canDrop(
            item(ComposerPropertyType.NUMBER, WordBankKind.VARIABLE),
            slot(ComposerPropertyType.PERCENT),
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept localization item on text slot`() {
        val result = canDrop(
            item(ComposerPropertyType.TEXT, WordBankKind.LOCALIZATION),
            slot(ComposerPropertyType.TEXT),
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept localization item on text slot regardless of item type`() {
        // Localization kind accepts text slot even if item.type differs
        val result = canDrop(
            item(ComposerPropertyType.NUMBER, WordBankKind.LOCALIZATION),
            slot(ComposerPropertyType.TEXT),
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept asset item on image slot`() {
        val result = canDrop(
            item(ComposerPropertyType.IMAGE, WordBankKind.ASSET),
            slot(ComposerPropertyType.IMAGE),
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept asset item on font slot`() {
        val result = canDrop(
            item(ComposerPropertyType.FONT, WordBankKind.ASSET),
            slot(ComposerPropertyType.FONT),
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept asset item on image slot regardless of item type`() {
        // Asset kind overrides type — an asset with TEXT type can go on IMAGE slot
        val result = canDrop(
            item(ComposerPropertyType.TEXT, WordBankKind.ASSET),
            slot(ComposerPropertyType.IMAGE),
        )
        assertThat(result).isTrue()
    }

    // -- canDrop: rejection rules --

    @Test
    fun `should reject color item on text slot`() {
        val result = canDrop(
            item(ComposerPropertyType.COLOR, WordBankKind.VARIABLE),
            slot(ComposerPropertyType.TEXT),
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject text item on number slot`() {
        val result = canDrop(
            item(ComposerPropertyType.TEXT, WordBankKind.VARIABLE),
            slot(ComposerPropertyType.NUMBER),
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject style item on non-style slot`() {
        val result = canDrop(
            item(ComposerPropertyType.STYLE, WordBankKind.STYLE),
            slot(ComposerPropertyType.COLOR),
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `should accept asset item on text slot via exact type match`() {
        // Rule 2 (exact type match) fires before kind-based rules,
        // so a TEXT-typed asset on a TEXT slot is accepted.
        val result = canDrop(
            item(ComposerPropertyType.TEXT, WordBankKind.ASSET),
            slot(ComposerPropertyType.TEXT),
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `should reject asset item on color slot`() {
        val result = canDrop(
            item(ComposerPropertyType.TEXT, WordBankKind.ASSET),
            slot(ComposerPropertyType.COLOR),
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject number item on color slot`() {
        val result = canDrop(
            item(ComposerPropertyType.NUMBER, WordBankKind.VARIABLE),
            slot(ComposerPropertyType.COLOR),
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject boolean item on text slot`() {
        val result = canDrop(
            item(ComposerPropertyType.BOOLEAN, WordBankKind.VARIABLE),
            slot(ComposerPropertyType.TEXT),
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject percent item on number slot`() {
        // Coercion is one-way: number → percent, not percent → number
        val result = canDrop(
            item(ComposerPropertyType.PERCENT, WordBankKind.VARIABLE),
            slot(ComposerPropertyType.NUMBER),
        )
        assertThat(result).isFalse()
    }

    // -- fillModeForDrop --

    @Test
    fun `should assign VARIABLE fill mode for variable kind`() {
        val (mode, value) = fillModeForDrop(
            item(ComposerPropertyType.COLOR, WordBankKind.VARIABLE, "@AccentColor"),
        )
        assertThat(mode).isEqualTo(FillMode.VARIABLE)
        assertThat(value).isEqualTo("@AccentColor")
    }

    @Test
    fun `should assign VARIABLE fill mode for style kind`() {
        val (mode, value) = fillModeForDrop(
            item(ComposerPropertyType.STYLE, WordBankKind.STYLE, "@ButtonStyle"),
        )
        assertThat(mode).isEqualTo(FillMode.VARIABLE)
        assertThat(value).isEqualTo("@ButtonStyle")
    }

    @Test
    fun `should assign IMPORT fill mode for import kind`() {
        val (mode, value) = fillModeForDrop(
            item(ComposerPropertyType.STYLE, WordBankKind.IMPORT, "\$Common.@DefaultButtonStyle"),
        )
        assertThat(mode).isEqualTo(FillMode.IMPORT)
        assertThat(value).isEqualTo("\$Common.@DefaultButtonStyle")
    }

    @Test
    fun `should assign LOCALIZATION fill mode for localization kind`() {
        val (mode, value) = fillModeForDrop(
            item(ComposerPropertyType.TEXT, WordBankKind.LOCALIZATION, "%button.submit"),
        )
        assertThat(mode).isEqualTo(FillMode.LOCALIZATION)
        assertThat(value).isEqualTo("%button.submit")
    }

    @Test
    fun `should assign LITERAL fill mode for asset kind`() {
        val (mode, value) = fillModeForDrop(
            item(ComposerPropertyType.IMAGE, WordBankKind.ASSET, "Slot.png"),
        )
        assertThat(mode).isEqualTo(FillMode.LITERAL)
        assertThat(value).isEqualTo("Slot.png")
    }
}
