// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class WordBankTypesTest {

    // -- WordBankItem --

    @Test
    fun `should create item with all fields`() {
        val item = WordBankItem(
            id = "v1",
            name = "@AccentColor",
            type = ComposerPropertyType.COLOR,
            kind = WordBankKind.VARIABLE,
            value = "#ff6b00",
            source = "local",
        )

        assertThat(item.id).isEqualTo("v1")
        assertThat(item.name).isEqualTo("@AccentColor")
        assertThat(item.type).isEqualTo(ComposerPropertyType.COLOR)
        assertThat(item.kind).isEqualTo(WordBankKind.VARIABLE)
        assertThat(item.value).isEqualTo("#ff6b00")
        assertThat(item.source).isEqualTo("local")
    }

    @Test
    fun `should default value and source to null`() {
        val item = WordBankItem(
            id = "a1",
            name = "icon.png",
            type = ComposerPropertyType.IMAGE,
            kind = WordBankKind.ASSET,
        )

        assertThat(item.value).isNull()
        assertThat(item.source).isNull()
    }

    @Test
    fun `should support equality by value`() {
        val a = WordBankItem("v1", "@Foo", ComposerPropertyType.TEXT, WordBankKind.VARIABLE)
        val b = WordBankItem("v1", "@Foo", ComposerPropertyType.TEXT, WordBankKind.VARIABLE)

        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `should distinguish items by id`() {
        val a = WordBankItem("v1", "@Foo", ComposerPropertyType.TEXT, WordBankKind.VARIABLE)
        val b = WordBankItem("v2", "@Foo", ComposerPropertyType.TEXT, WordBankKind.VARIABLE)

        assertThat(a).isNotEqualTo(b)
    }

    // -- WordBankKind --

    @Test
    fun `should have correct labels`() {
        assertThat(WordBankKind.VARIABLE.label).isEqualTo("Variables")
        assertThat(WordBankKind.STYLE.label).isEqualTo("Styles")
        assertThat(WordBankKind.IMPORT.label).isEqualTo("Imports")
        assertThat(WordBankKind.LOCALIZATION.label).isEqualTo("Localization")
        assertThat(WordBankKind.ASSET.label).isEqualTo("Assets")
    }

    @Test
    fun `should have correct badges`() {
        assertThat(WordBankKind.VARIABLE.badge).isEqualTo("@")
        assertThat(WordBankKind.STYLE.badge).isEqualTo("\u25C6")
        assertThat(WordBankKind.IMPORT.badge).isEqualTo("$")
        assertThat(WordBankKind.LOCALIZATION.badge).isEqualTo("%")
        assertThat(WordBankKind.ASSET.badge).isEqualTo("\u2B1A")
    }

    @Test
    fun `should have five kinds in display order`() {
        val kinds = WordBankKind.entries
        assertThat(kinds).hasSize(5)
        assertThat(kinds[0]).isEqualTo(WordBankKind.VARIABLE)
        assertThat(kinds[1]).isEqualTo(WordBankKind.STYLE)
        assertThat(kinds[2]).isEqualTo(WordBankKind.IMPORT)
        assertThat(kinds[3]).isEqualTo(WordBankKind.LOCALIZATION)
        assertThat(kinds[4]).isEqualTo(WordBankKind.ASSET)
    }

    // -- WordBankItem with each kind --

    @Test
    fun `should create items for each kind`() {
        val kinds = listOf(
            WordBankKind.VARIABLE to "@MyVar",
            WordBankKind.STYLE to "@MyStyle",
            WordBankKind.IMPORT to "\$Common.@Foo",
            WordBankKind.LOCALIZATION to "%ui.label",
            WordBankKind.ASSET to "icon.png",
        )

        for ((kind, name) in kinds) {
            val item = WordBankItem("id_${kind.name}", name, ComposerPropertyType.TEXT, kind)
            assertThat(item.kind).isEqualTo(kind)
            assertThat(item.name).isEqualTo(name)
        }
    }
}
