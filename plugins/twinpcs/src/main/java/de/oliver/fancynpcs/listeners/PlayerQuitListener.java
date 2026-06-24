package de.oliver.fancynpcs.listeners;

import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcStopLookingEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        FancyNpcs.getInstance().getNpcRuntime().removePlayer(event.getPlayer());
        UUID uuid = event.getPlayer().getUniqueId();
        for (Npc npc : FancyNpcs.getInstance().getNpcManagerImpl().getAllNpcs()) {
            npc.getIsVisibleForPlayer().remove(uuid);
            npc.getIsLookingAtPlayer().remove(uuid);
            npc.getIsTeamCreated().remove(uuid);
            npc.getLastPlayerInteraction().remove(uuid);
            new NpcStopLookingEvent(npc, event.getPlayer()).callEvent();
        }
    }
}
