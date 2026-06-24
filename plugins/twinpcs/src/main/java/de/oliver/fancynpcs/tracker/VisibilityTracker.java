package de.oliver.fancynpcs.tracker;

import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.listeners.NpcViewRefresh;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VisibilityTracker implements Runnable {

    private static final double CLIENT_REPAIR_DISTANCE_SQUARED = 48.0D * 48.0D;

    private Set<UUID> joinDelayPlayers;
    private final Map<UUID, ChunkPosition> playerChunks;
    private final Set<ViewerNpcKey> nearbyVisibleViews;

    public VisibilityTracker() {
        this.joinDelayPlayers = ConcurrentHashMap.newKeySet();
        this.playerChunks = new ConcurrentHashMap<>();
        this.nearbyVisibleViews = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void run() {
        FancyNpcs plugin = FancyNpcs.getInstance();
        Collection<Npc> npcs = List.copyOf(plugin.getNpcManagerImpl().getAllNpcs());

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            Location playerLocation = player.getLocation();
            ChunkPosition currentChunk = ChunkPosition.from(playerLocation);
            ChunkPosition previousChunk = playerChunks.put(playerId, currentChunk);
            boolean changedChunk = previousChunk != null && !previousChunk.equals(currentChunk);
            boolean bedrockPlayer = plugin.getNpcRuntime().isBedrockPlayer(player);

            if (joinDelayPlayers.contains(playerId)) {
                continue;
            }

            List<Npc> repairNpcs = changedChunk ? new ArrayList<>() : List.of();
            for (Npc npc : npcs) {
                if (changedChunk && !bedrockPlayer && shouldRepairVisibleNpc(player, playerLocation, npc)) {
                    repairNpcs.add(npc);
                    continue;
                }
                npc.checkAndUpdateVisibility(player);
            }

            if (!repairNpcs.isEmpty()) {
                NpcViewRefresh.refreshJavaViews(player, repairNpcs);
            }
        }
    }

    public void addJoinDelayPlayer(UUID player) {
        joinDelayPlayers.add(player);
    }

    public void removeJoinDelayPlayer(UUID player) {
        joinDelayPlayers.remove(player);
    }

    public void removePlayer(UUID player) {
        joinDelayPlayers.remove(player);
        playerChunks.remove(player);
        nearbyVisibleViews.removeIf(key -> key.viewerId().equals(player));
    }

    public void removeNpc(String npcId) {
        nearbyVisibleViews.removeIf(key -> key.npcId().equals(npcId));
    }

    public void clear() {
        joinDelayPlayers.clear();
        playerChunks.clear();
        nearbyVisibleViews.clear();
    }

    private boolean shouldRepairVisibleNpc(Player player, Location playerLocation, Npc npc) {
        UUID playerId = player.getUniqueId();
        ViewerNpcKey key = new ViewerNpcKey(playerId, npc.getData().getId());

        if (!npc.isShownFor(player)) {
            nearbyVisibleViews.remove(key);
            return false;
        }

        Location npcLocation = npc.getData().getLocation();
        if (npcLocation == null || playerLocation.getWorld() != npcLocation.getWorld()) {
            nearbyVisibleViews.remove(key);
            return false;
        }

        double distanceSquared = playerLocation.distanceSquared(npcLocation);
        if (Double.isNaN(distanceSquared) || distanceSquared > CLIENT_REPAIR_DISTANCE_SQUARED) {
            nearbyVisibleViews.remove(key);
            return false;
        }

        return nearbyVisibleViews.add(key);
    }

    private record ChunkPosition(String worldName, int x, int z) {

        private static ChunkPosition from(Location location) {
            return new ChunkPosition(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        }
    }

    private record ViewerNpcKey(UUID viewerId, String npcId) {
    }
}
