package com.hyve.prefab.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hyve.common.compose.HyveTypography
import com.hyve.common.compose.components.StatusBar
import com.hyve.prefab.domain.PrefabDocument
import org.jetbrains.jewel.ui.component.Text

/**
 * Status bar at the bottom of the prefab editor showing summary statistics.
 */
@Composable
fun PrefabStatsBar(doc: PrefabDocument) {
    StatusBar {
        Text(text = "Blocks: ${doc.blockData.blockCount}", style = HyveTypography.statusBar)
        Text(text = "Entities: ${doc.entities.size}", style = HyveTypography.statusBar)

        if (doc.componentBlocks.isNotEmpty()) {
            Text(text = "Block Entities: ${doc.componentBlocks.size}", style = HyveTypography.statusBar)
        }

        if (doc.fluidSummary.count > 0) {
            Text(text = "Fluids: ${doc.fluidSummary.count}", style = HyveTypography.statusBar)
        }

        Spacer(Modifier.weight(1f))
    }
}
