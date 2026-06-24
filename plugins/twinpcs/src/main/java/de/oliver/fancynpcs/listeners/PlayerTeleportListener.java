package de.oliver.fancynpcs.listeners;

import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerTeleportListener implements Listener {

    private static final double LARGE_TELEPORT_DISTANCE_SQUARED = 64.0D * 64.0D;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(@NotNull final PlayerTeleportEvent event) {
        if (event.isCancelled() || event.getTo() == null) {
            return;
        }

        Player player = event.getPlayer();
        if (shouldForceRefresh(event.getFrom(), event.getTo())) {
            NpcViewRefresh.refreshJavaView(player);
            return;
        }

        for (Npc npc : FancyNpcs.getInstance().getNpcManager().getAllNpcs()) {
            npc.checkAndUpdateVisibility(player);
        }
    }

    private boolean shouldForceRefresh(Location from, Location to) {
        if (from.getWorld() != to.getWorld()) {
            return true;
        }
        return from.distanceSquared(to) >= LARGE_TELEPORT_DISTANCE_SQUARED;
    }

}
