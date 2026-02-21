// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.wordbank

import androidx.compose.ui.geometry.Offset
import com.hyve.ui.composer.model.ComposerPropertyType
import com.hyve.ui.composer.model.PopoverKind
import com.hyve.ui.composer.model.WordBankItem
import com.hyve.ui.composer.model.WordBankKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class WordBankStateTest {

    private fun createTestItems() = listOf(
        WordBankItem("v1", "@AccentColor", ComposerPropertyType.COLOR, WordBankKind.VARIABLE, "#ff6b00"),
        WordBankItem("v2", "@FontSize", ComposerPropertyType.NUMBER, WordBankKind.VARIABLE, "14"),
        WordBankItem("s1", "@ButtonStyle", ComposerPropertyType.STYLE, WordBankKind.STYLE, null, "local"),
        WordBankItem("i1", "\$Common.@PrimaryColor", ComposerPropertyType.COLOR, WordBankKind.IMPORT, "#0066ff", "Common.ui"),
        WordBankItem("l1", "%button.submit", ComposerPropertyType.TEXT, WordBankKind.LOCALIZATION, "Submit"),
    )

    // -- Initialization --

    @Test
    fun `should initialize with provided items`() {
        val items = createTestItems()
        val state = WordBankState(items)

        assertThat(state.items.value).isEqualTo(items)
        assertThat(state.searchQuery.value).isEmpty()
        assertThat(state.collapsedSections.value).isEmpty()
        assertThat(state.contextMenu.value).isNull()
    }

    @Test
    fun `should initialize empty by default`() {
        val state = WordBankState()

        assertThat(state.items.value).isEmpty()
    }

    // -- Search / filter (FR-4) --

    @Test
    fun `should filter items by name case-insensitively`() {
        val state = WordBankState(createTestItems())

        state.setSearchQuery("accent")

        assertThat(state.filteredItems).hasSize(1)
        assertThat(state.filteredItems[0].id).isEqualTo("v1")
    }

    @Test
    fun `should filter items by value`() {
        val state = WordBankState(createTestItems())

        state.setSearchQuery("Submit")

        assertThat(state.filteredItems).hasSize(1)
        assertThat(state.filteredItems[0].id).isEqualTo("l1")
    }

    @Test
    fun `should return all items when search is blank`() {
        val state = WordBankState(createTestItems())

        state.setSearchQuery("   ")

        assertThat(state.filteredItems).hasSize(5)
    }

    @Test
    fun `should return all items when search is empty`() {
        val state = WordBankState(createTestItems())

        state.setSearchQuery("accent")
        state.clearSearch()

        assertThat(state.filteredItems).hasSize(5)
        assertThat(state.searchQuery.value).isEmpty()
    }

    @Test
    fun `should return empty list when no items match search`() {
        val state = WordBankState(createTestItems())

        state.setSearchQuery("nonexistent")

        assertThat(state.filteredItems).isEmpty()
    }

    // -- Items by kind --

    @Test
    fun `should return items filtered by kind`() {
        val state = WordBankState(createTestItems())

        val variables = state.itemsForKind(WordBankKind.VARIABLE)

        assertThat(variables).hasSize(2)
        assertThat(variables.map { it.id }).containsExactly("v1", "v2")
    }

    @Test
    fun `should return empty list for kind with no items`() {
        val state = WordBankState(createTestItems())

        val assets = state.itemsForKind(WordBankKind.ASSET)

        assertThat(assets).isEmpty()
    }

    // -- Section visibility --

    @Test
    fun `should show all sections when no search is active`() {
        val state = WordBankState(createTestItems())

        for (kind in WordBankKind.entries) {
            assertThat(state.isSectionVisible(kind)).isTrue()
        }
    }

    @Test
    fun `should hide sections with no matches during search`() {
        val state = WordBankState(createTestItems())

        state.setSearchQuery("accent")

        assertThat(state.isSectionVisible(WordBankKind.VARIABLE)).isTrue()
        assertThat(state.isSectionVisible(WordBankKind.STYLE)).isFalse()
        assertThat(state.isSectionVisible(WordBankKind.IMPORT)).isFalse()
        assertThat(state.isSectionVisible(WordBankKind.LOCALIZATION)).isFalse()
        assertThat(state.isSectionVisible(WordBankKind.ASSET)).isFalse()
    }

    // -- Section collapse (FR-2) --

    @Test
    fun `should start with all sections expanded`() {
        val state = WordBankState(createTestItems())

        for (kind in WordBankKind.entries) {
            assertThat(state.isSectionCollapsed(kind)).isFalse()
        }
    }

    @Test
    fun `should toggle section collapse`() {
        val state = WordBankState(createTestItems())

        state.toggleSection(WordBankKind.VARIABLE)

        assertThat(state.isSectionCollapsed(WordBankKind.VARIABLE)).isTrue()
        assertThat(state.isSectionCollapsed(WordBankKind.STYLE)).isFalse()
    }

    @Test
    fun `should toggle section back to expanded`() {
        val state = WordBankState(createTestItems())

        state.toggleSection(WordBankKind.VARIABLE)
        state.toggleSection(WordBankKind.VARIABLE)

        assertThat(state.isSectionCollapsed(WordBankKind.VARIABLE)).isFalse()
    }

    // -- Item management (FR-8) --

    @Test
    fun `should add item to the list`() {
        val state = WordBankState(createTestItems())
        val newItem = WordBankItem("v3", "@NewVar", ComposerPropertyType.TEXT, WordBankKind.VARIABLE)

        state.addItem(newItem)

        assertThat(state.items.value).hasSize(6)
        assertThat(state.items.value.last()).isEqualTo(newItem)
    }

    @Test
    fun `should remove item by id`() {
        val state = WordBankState(createTestItems())

        state.removeItem("v1")

        assertThat(state.items.value).hasSize(4)
        assertThat(state.items.value.none { it.id == "v1" }).isTrue()
    }

    @Test
    fun `should allow duplicate names`() {
        val state = WordBankState(createTestItems())
        val duplicate = WordBankItem("v99", "@AccentColor", ComposerPropertyType.COLOR, WordBankKind.VARIABLE, "#00ff00")

        state.addItem(duplicate)

        val accentItems = state.items.value.filter { it.name == "@AccentColor" }
        assertThat(accentItems).hasSize(2)
    }

    @Test
    fun `should show empty state when last item in section is removed`() {
        val state = WordBankState(listOf(
            WordBankItem("s1", "@OnlyStyle", ComposerPropertyType.STYLE, WordBankKind.STYLE, null, "local"),
        ))

        state.removeItem("s1")

        assertThat(state.itemsForKind(WordBankKind.STYLE)).isEmpty()
    }

    // -- Context menu (FR-6) --

    @Test
    fun `should open context menu for item`() {
        val state = WordBankState(createTestItems())
        val item = createTestItems()[0]
        val position = Offset(100f, 200f)

        state.showContextMenu(item, position)

        assertThat(state.contextMenu.value).isNotNull
        assertThat(state.contextMenu.value!!.item).isEqualTo(item)
        assertThat(state.contextMenu.value!!.position).isEqualTo(position)
    }

    @Test
    fun `should close context menu`() {
        val state = WordBankState(createTestItems())
        state.showContextMenu(createTestItems()[0], Offset(100f, 200f))

        state.closeContextMenu()

        assertThat(state.contextMenu.value).isNull()
    }

    @Test
    fun `should close context menu when its item is removed`() {
        val state = WordBankState(createTestItems())
        val item = createTestItems()[0]
        state.showContextMenu(item, Offset(100f, 200f))

        state.removeItem(item.id)

        assertThat(state.contextMenu.value).isNull()
    }

    @Test
    fun `should keep context menu open when a different item is removed`() {
        val state = WordBankState(createTestItems())
        val menuItem = createTestItems()[0]
        state.showContextMenu(menuItem, Offset(100f, 200f))

        state.removeItem("v2") // Remove a different item

        assertThat(state.contextMenu.value).isNotNull
        assertThat(state.contextMenu.value!!.item).isEqualTo(menuItem)
    }

    // -- Popover state (spec 06) --

    @Test
    fun `should start with no popover open`() {
        val state = WordBankState()
        assertThat(state.activePopover.value).isNull()
    }

    @Test
    fun `should open popover`() {
        val state = WordBankState()

        state.openPopover(PopoverKind.ADD_VARIABLE)

        assertThat(state.activePopover.value).isEqualTo(PopoverKind.ADD_VARIABLE)
    }

    @Test
    fun `should close popover`() {
        val state = WordBankState()
        state.openPopover(PopoverKind.ADD_VARIABLE)

        state.closePopover()

        assertThat(state.activePopover.value).isNull()
    }

    @Test
    fun `should replace popover when opening a different one`() {
        val state = WordBankState()
        state.openPopover(PopoverKind.ADD_VARIABLE)

        state.openPopover(PopoverKind.ADD_STYLE)

        assertThat(state.activePopover.value).isEqualTo(PopoverKind.ADD_STYLE)
    }

    // -- Search + kind interaction --

    @Test
    fun `should filter items by kind and search together`() {
        val state = WordBankState(createTestItems())

        state.setSearchQuery("color")

        val variables = state.itemsForKind(WordBankKind.VARIABLE)
        val imports = state.itemsForKind(WordBankKind.IMPORT)

        assertThat(variables).hasSize(1)
        assertThat(variables[0].name).isEqualTo("@AccentColor")
        assertThat(imports).hasSize(1)
        assertThat(imports[0].name).isEqualTo("\$Common.@PrimaryColor")
    }
}
