package com.hyve.hyvehotreload

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for active HUD instances.
 *
 * Mods register HUDs when shown and unregister when hidden.
 * On .ui file change, the hot-reload plugin re-sends packets
 * for every registered HUD so clients re-render.
 */
object HudRegistry {

    private val huds: MutableSet<CustomUIHud> = ConcurrentHashMap.newKeySet()

    fun register(hud: CustomUIHud) {
        huds.add(hud)
    }

    fun unregister(hud: CustomUIHud) {
        huds.remove(hud)
    }

    /** Returns an immutable snapshot — safe to iterate while the registry mutates. */
    fun snapshot(): Set<CustomUIHud> = huds.toSet()

    fun clear() {
        huds.clear()
    }
}
