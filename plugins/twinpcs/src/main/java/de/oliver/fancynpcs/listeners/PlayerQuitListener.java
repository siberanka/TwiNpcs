package de.oliver.fancynpcs.listeners;

import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcStopLookingEvent;
import de.oliver.fancynpcs.tracker.VisibilityTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        FancyNpcs.getInstance().getNpcRuntime().removePlayer(event.getPlayer());
        UUID uuid = event.getPlayer().getUniqueId();
        VisibilityTracker visibilityTracker = FancyNpcs.getInstance().getVisibilityTracker();
        if (visibilityTracker != null) {
            visibilityTracker.removePlayer(uuid);
        }
        NpcViewRefresh.removePlayer(uuid);
        for (Npc npc : FancyNpcs.getInstance().getNpcManagerImpl().getAllNpcs()) {
            npc.clearViewerState(uuid);
            new NpcStopLookingEvent(npc, event.getPlayer()).callEvent();
        }
    }
}
