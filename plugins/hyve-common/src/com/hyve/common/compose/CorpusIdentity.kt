// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * Visual identity metadata for each knowledge corpus.
 */
enum class CorpusVisual(
    val corpusId: String,
    val shortLabel: String,
    val displayName: String,
    val tooltipText: String,
    val iconKey: IconKey,
) {
    CODE("code", "Code", "Server Code", "Search decompiled Java server code", AllIconsKeys.Nodes.Console),
    GAMEDATA("gamedata", "Game Data", "Game Data", "Search game items, recipes, and NPCs", AllIconsKeys.Nodes.DataTables),
    CLIENT("client", "Client UI", "Client UI", "Search client UI templates and components", AllIconsKeys.FileTypes.UiForm),
    DOCS("docs", "Docs", "Modding Docs", "Search modding documentation and guides", AllIconsKeys.FileTypes.Text),
    ;

    companion object {
        /** Canonical display order. */
        val ordered: List<CorpusVisual> = listOf(CODE, GAMEDATA, CLIENT, DOCS)

        fun forId(id: String): CorpusVisual? = entries.find { it.corpusId == id }
    }
}

/**
 * Corpus-specific accent colors that adapt to the current theme.
 */
object CorpusColors {

    val code: Color
        @Composable @ReadOnlyComposable
        get() = HyveThemeColors.colors.info

    val gameData: Color
        @Composable @ReadOnlyComposable
        get() = HyveThemeColors.colors.success

    val clientUi: Color
        @Composable @ReadOnlyComposable
        get() = HyveThemeColors.colors.accent

    val docs: Color
        @Composable @ReadOnlyComposable
        get() = HyveThemeColors.colors.honey

    @Composable
    @ReadOnlyComposable
    fun forCorpus(id: String): Color = when (id) {
        "code" -> code
        "gamedata" -> gameData
        "client" -> clientUi
        "docs" -> docs
        else -> HyveThemeColors.colors.textSecondary
    }
}
