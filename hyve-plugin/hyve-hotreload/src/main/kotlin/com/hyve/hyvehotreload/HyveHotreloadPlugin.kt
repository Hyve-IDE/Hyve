package com.hyve.hyvehotreload

import com.hypixel.hytale.server.core.asset.common.events.CommonAssetMonitorEvent
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.Universe
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * Hot-reload companion plugin for Hyve IDE.
 *
 * Watches for .ui file changes on disk. The engine pushes updated assets
 * to clients automatically — this plugin re-sends active HUD packets
 * so clients re-render with the new content.
 */
class HyveHotreloadPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    override fun setup() {
        eventRegistry.registerGlobal(
            CommonAssetMonitorEvent::class.java,
            ::onAssetChanged,
        )
    }

    override fun start() {
        logger.at(Level.INFO).log("Hyve Hot Reload enabled — watching for .ui changes")
    }

    override fun shutdown() {
        HudRegistry.clear()
        logger.at(Level.INFO).log("Hyve Hot Reload disabled")
    }

    private fun onAssetChanged(event: CommonAssetMonitorEvent) {
        val changedUiFiles = event.createdOrModifiedFilesToLoad
            .filter { it.toString().endsWith(".ui") }

        if (changedUiFiles.isEmpty()) return
        if (Universe.get().playerCount == 0) return

        for (path in changedUiFiles) {
            logger.at(Level.INFO).log("UI file changed: %s", path)
        }

        // Delay to let the asset pipeline finish processing
        CompletableFuture.delayedExecutor(RELOAD_DELAY_MS, TimeUnit.MILLISECONDS).execute {
            refreshHuds()
        }
    }

    private fun refreshHuds() {
        val huds = HudRegistry.snapshot()
        if (huds.isEmpty()) {
            logger.at(Level.INFO).log("No HUDs registered — skipping refresh")
            return
        }

        var refreshed = 0
        for (hud in huds) {
            try {
                hud.show()
                refreshed++
            } catch (e: Exception) {
                logger.at(Level.WARNING).log("Failed to refresh HUD: %s", e.message)
            }
        }
        logger.at(Level.INFO).log("Refreshed %d/%d HUD(s)", refreshed, huds.size)
    }

    companion object {
        private const val RELOAD_DELAY_MS = 500L
    }
}
