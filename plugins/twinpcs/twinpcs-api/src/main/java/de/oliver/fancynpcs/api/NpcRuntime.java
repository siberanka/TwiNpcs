package de.oliver.fancynpcs.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface NpcRuntime {

    NpcRuntime NO_OP = new NpcRuntime() {
    };

    default void afterCreate(Npc npc) {
    }

    default boolean spawnOverride(Npc npc, Player player) {
        return false;
    }

    default void afterSpawn(Npc npc, Player player) {
    }

    default boolean removeOverride(Npc npc, Player player) {
        return false;
    }

    default boolean updateOverride(Npc npc, Player player, boolean swingArm) {
        return false;
    }

    default boolean moveOverride(Npc npc, Player player, boolean swingArm) {
        return false;
    }

    default boolean lookAtOverride(Npc npc, Player player, Location location) {
        return false;
    }

    default void close(Npc npc) {
    }

    default Npc resolveInteractionNpc(int entityId) {
        return null;
    }

    default Npc resolveInteractionNpc(int entityId, Player player) {
        return resolveInteractionNpc(entityId);
    }

    default boolean allowDirectInteraction(Npc npc, Player player) {
        return true;
    }

    default void removePlayer(Player player) {
    }

    default void shutdown() {
    }
}
