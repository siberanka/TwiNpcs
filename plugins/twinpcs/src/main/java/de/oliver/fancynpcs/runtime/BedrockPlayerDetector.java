package de.oliver.fancynpcs.runtime;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BedrockPlayerDetector {

    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();

    boolean isBedrock(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), this::detect);
    }

    void remove(UUID uuid) {
        cache.remove(uuid);
    }

    private boolean detect(UUID uuid) {
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            Boolean floodgateResult = invokeDetector(
                    "floodgate",
                    "org.geysermc.floodgate.api.FloodgateApi",
                    "getInstance",
                    "isFloodgatePlayer",
                    uuid
            );
            if (floodgateResult != null) {
                return floodgateResult;
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            try {
                Class<?> apiClass = ReflectionCalls.loadFromPlugin("Geyser-Spigot", "org.geysermc.geyser.api.GeyserApi");
                Object api = apiClass.getMethod("api").invoke(null);
                Method connectionByUuid = api.getClass().getMethod("connectionByUuid", UUID.class);
                return connectionByUuid.invoke(api, uuid) != null;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }

        return false;
    }

    private Boolean invokeDetector(String pluginName, String className, String instanceMethod, String detectorMethod, UUID uuid) {
        try {
            Class<?> apiClass = ReflectionCalls.loadFromPlugin(pluginName, className);
            Object api = apiClass.getMethod(instanceMethod).invoke(null);
            return (Boolean) api.getClass().getMethod(detectorMethod, UUID.class).invoke(api, uuid);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
