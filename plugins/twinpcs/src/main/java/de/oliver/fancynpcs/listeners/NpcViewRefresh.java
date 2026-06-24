package de.oliver.fancynpcs.listeners;

import de.oliver.fancylib.serverSoftware.ServerSoftware;
import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.tracker.VisibilityTracker;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class NpcViewRefresh {

    private static final long[] RESYNC_DELAYS = {2L, 10L, 40L};
    private static final AtomicLong REFRESH_COUNTER = new AtomicLong();
    private static final Map<UUID, Long> REFRESH_TOKENS = new ConcurrentHashMap<>();

    private NpcViewRefresh() {
    }

    public static void refreshJavaView(Player player) {
        FancyNpcs plugin = FancyNpcs.getInstance();
        if (plugin == null || player == null || !player.isOnline() || plugin.getNpcRuntime().isBedrockPlayer(player)) {
            return;
        }

        runOnPlayer(plugin, player, () -> refreshJavaViews(plugin, player, List.copyOf(plugin.getNpcManagerImpl().getAllNpcs())));
    }

    public static void refreshJavaViews(Player player, Collection<Npc> npcs) {
        FancyNpcs plugin = FancyNpcs.getInstance();
        if (plugin == null || player == null || npcs == null || npcs.isEmpty()
                || !player.isOnline() || plugin.getNpcRuntime().isBedrockPlayer(player)) {
            return;
        }

        List<Npc> snapshot = List.copyOf(npcs);
        runOnPlayer(plugin, player, () -> refreshJavaViews(plugin, player, snapshot));
    }

    public static void removePlayer(UUID playerId) {
        REFRESH_TOKENS.remove(playerId);
    }

    public static void clear() {
        REFRESH_TOKENS.clear();
    }

    private static void refreshJavaViews(FancyNpcs plugin, Player player, List<Npc> npcs) {
        if (!player.isOnline() || plugin.getNpcRuntime().isBedrockPlayer(player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long token = REFRESH_COUNTER.incrementAndGet();
        REFRESH_TOKENS.put(playerId, token);

        VisibilityTracker visibilityTracker = plugin.getVisibilityTracker();
        if (visibilityTracker != null) {
            visibilityTracker.addJoinDelayPlayer(playerId);
        }

        resetTrackedViews(player, playerId, npcs);
        for (int i = 0; i < RESYNC_DELAYS.length; i++) {
            long delay = RESYNC_DELAYS[i];
            boolean lastAttempt = i == RESYNC_DELAYS.length - 1;
            Location location = player.getLocation().clone();
            plugin.getScheduler().runTaskLater(location, delay, () -> resync(plugin, player, playerId, token, npcs, lastAttempt));
        }
    }

    private static void resetTrackedViews(Player player, UUID playerId, List<Npc> npcs) {
        for (Npc npc : npcs) {
            if (npc.isShownFor(player)) {
                npc.remove(player);
            }
            npc.resetViewerState(playerId);
        }
    }

    private static void resync(
            FancyNpcs plugin,
            Player player,
            UUID playerId,
            long token,
            List<Npc> npcs,
            boolean lastAttempt
    ) {
        if (REFRESH_TOKENS.getOrDefault(playerId, 0L) != token) {
            return;
        }

        if (!player.isOnline() || plugin.getNpcRuntime().isBedrockPlayer(player)) {
            finish(plugin, playerId, token);
            return;
        }

        for (Npc npc : npcs) {
            npc.checkAndUpdateVisibility(player);
        }

        if (lastAttempt) {
            finish(plugin, playerId, token);
        }
    }

    private static void finish(FancyNpcs plugin, UUID playerId, long token) {
        REFRESH_TOKENS.remove(playerId, token);
        VisibilityTracker visibilityTracker = plugin.getVisibilityTracker();
        if (visibilityTracker != null) {
            visibilityTracker.removeJoinDelayPlayer(playerId);
        }
    }

    private static void runOnPlayer(FancyNpcs plugin, Player player, Runnable task) {
        if (ServerSoftware.isFolia()) {
            player.getScheduler().run(plugin, ignored -> task.run(), null);
            return;
        }

        plugin.getScheduler().runTask(null, task);
    }
}
