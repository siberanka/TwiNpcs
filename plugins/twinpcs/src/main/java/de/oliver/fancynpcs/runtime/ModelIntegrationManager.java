package de.oliver.fancynpcs.runtime;

import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.model.NpcModelProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

final class ModelIntegrationManager {

    private static final int MAX_CACHED_MODEL_IDS = 10_000;
    private static final int MAX_RETURNED_SUGGESTIONS = 512;
    private static final long SUGGESTION_CACHE_NANOS = 10_000_000_000L;
    private static final Pattern SAFE_MODEL_ID = Pattern.compile("[A-Za-z0-9_.:/-]{1,128}");

    private final FancyNpcs plugin;
    private final Map<String, ModelHandle> handles = new ConcurrentHashMap<>();
    private final Map<NpcModelProvider, Boolean> warnedUnavailable = new EnumMap<>(NpcModelProvider.class);
    private final Map<NpcModelProvider, SuggestionCacheEntry> suggestionCache = new ConcurrentHashMap<>();
    private final NamespacedKey mythicMarker;
    private final NamespacedKey modelAnchorMarker;

    ModelIntegrationManager(FancyNpcs plugin) {
        this.plugin = plugin;
        this.mythicMarker = new NamespacedKey(plugin, "managed_mythic_npc");
        this.modelAnchorMarker = new NamespacedKey(plugin, "model_anchor");
    }

    void afterCreate(Npc npc) {
        if (npc.getData().isRuntimeView()) {
            return;
        }

        close(npc);
        NpcModelProvider provider = npc.getData().getModelProvider();
        if (provider == NpcModelProvider.VANILLA) {
            return;
        }

        if (!isProviderEnabled(provider)) {
            warnUnavailable(provider);
            return;
        }

        try {
            ModelHandle handle = switch (provider) {
                case BETTERMODEL -> createBetterModel(npc);
                case MODELENGINE -> createModelEngine(npc);
                case MYTHICMOBS -> createMythicMob(npc);
                case VANILLA -> null;
            };
            if (handle != null) {
                handles.put(npc.getData().getId(), handle);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    handle.hide(player);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            plugin.getFancyLogger().error(
                    "Could not apply " + provider + " model '" + npc.getData().getModelId()
                            + "' to NPC '" + npc.getData().getName() + "': " + exception.getMessage()
            );
        }
    }

    boolean hasModel(Npc npc) {
        return handles.containsKey(npc.getData().getId());
    }

    Entity getModelEntity(Npc npc) {
        ModelHandle handle = handles.get(npc.getData().getId());
        return handle == null ? null : handle.entity();
    }

    void show(Npc npc, Player player) {
        ModelHandle handle = handles.get(npc.getData().getId());
        if (handle != null) {
            handle.show(player);
        }
    }

    void hide(Npc npc, Player player) {
        ModelHandle handle = handles.get(npc.getData().getId());
        if (handle != null) {
            handle.hide(player);
        }
    }

    void teleport(Npc npc, Location location) {
        ModelHandle handle = handles.get(npc.getData().getId());
        if (handle != null) {
            handle.teleport(location);
        }
    }

    void lookAt(Npc npc, Location location) {
        ModelHandle handle = handles.get(npc.getData().getId());
        if (handle != null) {
            handle.lookAt(location);
        }
    }

    void close(Npc npc) {
        ModelHandle handle = handles.remove(npc.getData().getId());
        if (handle != null) {
            handle.close();
        }
    }

    void shutdown() {
        for (ModelHandle handle : handles.values()) {
            handle.close();
        }
        handles.clear();
        suggestionCache.clear();
    }

    Collection<ModelHandle> handles() {
        return handles.values();
    }

    List<String> suggestions(NpcModelProvider provider, String input) {
        if (provider == null || provider == NpcModelProvider.VANILLA || !isProviderEnabled(provider)) {
            return List.of();
        }

        long now = System.nanoTime();
        SuggestionCacheEntry cached = suggestionCache.get(provider);
        if (cached == null || now - cached.createdAtNanos() >= SUGGESTION_CACHE_NANOS) {
            cached = refreshSuggestions(provider, now, cached);
        }

        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return cached.modelIds().stream()
                .filter(modelId -> prefix.isEmpty() || modelId.toLowerCase(Locale.ROOT).startsWith(prefix))
                .limit(MAX_RETURNED_SUGGESTIONS)
                .toList();
    }

    private synchronized SuggestionCacheEntry refreshSuggestions(
            NpcModelProvider provider,
            long now,
            SuggestionCacheEntry previous
    ) {
        SuggestionCacheEntry current = suggestionCache.get(provider);
        if (current != null && current != previous
                && now - current.createdAtNanos() < SUGGESTION_CACHE_NANOS) {
            return current;
        }

        try {
            Object rawModelIds = switch (provider) {
                case BETTERMODEL -> {
                    Class<?> api = ReflectionCalls.loadFromPlugin("BetterModel", "kr.toxicity.model.api.BetterModel");
                    yield ReflectionCalls.invokeStatic(api, "modelKeys");
                }
                case MODELENGINE -> {
                    Class<?> api = ReflectionCalls.loadFromPlugin("ModelEngine", "com.ticxo.modelengine.api.ModelEngineAPI");
                    Object modelEngine = ReflectionCalls.invokeStatic(api, "getAPI");
                    Object registry = ReflectionCalls.invoke(modelEngine, "getModelRegistry");
                    yield ReflectionCalls.invoke(registry, "getOrderedId");
                }
                case MYTHICMOBS -> {
                    Class<?> api = ReflectionCalls.loadFromPlugin("MythicMobs", "io.lumine.mythic.bukkit.MythicBukkit");
                    Object mythic = ReflectionCalls.invokeStatic(api, "inst");
                    Object mobManager = ReflectionCalls.invoke(mythic, "getMobManager");
                    yield ReflectionCalls.invoke(mobManager, "getMobNames");
                }
                case VANILLA -> List.of();
            };

            if (!(rawModelIds instanceof Iterable<?> iterable)) {
                return cacheSuggestions(provider, List.of(), now);
            }

            List<String> modelIds = new java.util.ArrayList<>();
            Set<String> seenModelIds = new HashSet<>();
            for (Object rawModelId : iterable) {
                if (modelIds.size() >= MAX_CACHED_MODEL_IDS) {
                    break;
                }
                if (rawModelId instanceof String modelId
                        && SAFE_MODEL_ID.matcher(modelId).matches()
                        && seenModelIds.add(modelId)) {
                    modelIds.add(modelId);
                }
            }
            modelIds.sort(Comparator.comparing((String value) -> value.toLowerCase(Locale.ROOT))
                    .thenComparing(Comparator.naturalOrder()));
            return cacheSuggestions(provider, List.copyOf(modelIds), now);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            SuggestionCacheEntry fallback = previous == null
                    ? new SuggestionCacheEntry(List.of(), now)
                    : new SuggestionCacheEntry(previous.modelIds(), now);
            suggestionCache.put(provider, fallback);
            return fallback;
        }
    }

    private SuggestionCacheEntry cacheSuggestions(NpcModelProvider provider, List<String> modelIds, long now) {
        SuggestionCacheEntry entry = new SuggestionCacheEntry(modelIds, now);
        suggestionCache.put(provider, entry);
        return entry;
    }

    private boolean isProviderEnabled(NpcModelProvider provider) {
        return switch (provider) {
            case VANILLA -> true;
            case BETTERMODEL -> Bukkit.getPluginManager().isPluginEnabled("BetterModel");
            case MODELENGINE -> Bukkit.getPluginManager().isPluginEnabled("ModelEngine");
            case MYTHICMOBS -> Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
        };
    }

    private void warnUnavailable(NpcModelProvider provider) {
        if (warnedUnavailable.putIfAbsent(provider, true) == null) {
            plugin.getFancyLogger().warn(provider + " integration is configured but its plugin is not enabled.");
        }
    }

    private ModelHandle createBetterModel(Npc npc) throws ReflectiveOperationException {
        Class<?> betterModel = ReflectionCalls.loadFromPlugin("BetterModel", "kr.toxicity.model.api.BetterModel");
        Object optionalModel = ReflectionCalls.invokeStatic(betterModel, "model", npc.getData().getModelId());
        Object model = unwrapOptional(optionalModel);
        if (model == null) {
            throw new IllegalArgumentException("BetterModel model does not exist");
        }

        Class<?> adapter = ReflectionCalls.loadFromPlugin("BetterModel", "kr.toxicity.model.api.bukkit.platform.BukkitAdapter");
        Interaction anchor = createModelAnchor(npc);
        final Object tracker;
        final Object registry;
        try {
            Object adaptedEntity = ReflectionCalls.invokeStatic(adapter, "adapt", anchor);
            tracker = ReflectionCalls.invoke(model, "getOrCreate", adaptedEntity);
            registry = ReflectionCalls.invoke(tracker, "registry");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            anchor.remove();
            throw exception;
        }

        return new ModelHandle() {
            @Override
            public Entity entity() {
                return anchor;
            }

            @Override
            public void show(Player player) {
                try {
                    player.showEntity(plugin, anchor);
                    Object adaptedPlayer = ReflectionCalls.invokeStatic(adapter, "adapt", player);
                    ReflectionCalls.invoke(registry, "spawn", adaptedPlayer);
                } catch (ReflectiveOperationException exception) {
                    plugin.getFancyLogger().warn("Could not show BetterModel to " + player.getName());
                }
            }

            @Override
            public void hide(Player player) {
                try {
                    Object adaptedPlayer = ReflectionCalls.invokeStatic(adapter, "adapt", player);
                    ReflectionCalls.invokeIfPresent(registry, "remove", adaptedPlayer);
                } catch (ReflectiveOperationException ignored) {
                } finally {
                    player.hideEntity(plugin, anchor);
                }
            }

            @Override
            public void teleport(Location location) {
                anchor.teleportAsync(location);
            }

            @Override
            public void lookAt(Location location) {
                anchor.setRotation(location.getYaw(), location.getPitch());
            }

            @Override
            public void close() {
                ReflectionCalls.invokeIfPresent(tracker, "close");
                if (anchor.isValid()) {
                    anchor.remove();
                }
            }
        };
    }

    private ModelHandle createModelEngine(Npc npc) throws ReflectiveOperationException {
        Class<?> api = ReflectionCalls.loadFromPlugin("ModelEngine", "com.ticxo.modelengine.api.ModelEngineAPI");
        Object activeModel = ReflectionCalls.invokeStatic(api, "createActiveModel", npc.getData().getModelId());
        if (activeModel == null) {
            throw new IllegalArgumentException("ModelEngine model does not exist");
        }
        Interaction anchor = createModelAnchor(npc);
        final Object modeledEntity;
        final Object entityHandler;
        final Object baseEntity;
        try {
            modeledEntity = ReflectionCalls.invokeStatic(api, "createModeledEntity", anchor);
            ReflectionCalls.invoke(modeledEntity, "addModel", activeModel, true);
            ReflectionCalls.invokeIfPresent(modeledEntity, "setBaseEntityVisible", false);
            entityHandler = ReflectionCalls.invokeStatic(api, "getEntityHandler");
            baseEntity = ReflectionCalls.invoke(modeledEntity, "getBase");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            anchor.remove();
            throw exception;
        }

        return new ModelHandle() {
            @Override
            public Entity entity() {
                return anchor;
            }

            @Override
            public void show(Player player) {
                player.showEntity(plugin, anchor);
                ReflectionCalls.invokeIfPresent(entityHandler, "forceSpawn", baseEntity, player);
            }

            @Override
            public void hide(Player player) {
                ReflectionCalls.invokeIfPresent(entityHandler, "forceDespawn", baseEntity, player);
                player.hideEntity(plugin, anchor);
            }

            @Override
            public void teleport(Location location) {
                anchor.teleportAsync(location);
            }

            @Override
            public void lookAt(Location location) {
                anchor.setRotation(location.getYaw(), location.getPitch());
            }

            @Override
            public void close() {
                ReflectionCalls.invokeIfPresent(modeledEntity, "removeModel", npc.getData().getModelId());
                ReflectionCalls.invokeIfPresent(modeledEntity, "destroy");
                if (anchor.isValid()) {
                    anchor.remove();
                }
            }
        };
    }

    private ModelHandle createMythicMob(Npc npc) throws ReflectiveOperationException {
        Class<?> mythicBukkit = ReflectionCalls.loadFromPlugin("MythicMobs", "io.lumine.mythic.bukkit.MythicBukkit");
        Object mythic = ReflectionCalls.invokeStatic(mythicBukkit, "inst");
        Object mobManager = ReflectionCalls.invoke(mythic, "getMobManager");
        Object optionalMob = ReflectionCalls.invoke(mobManager, "getMythicMob", npc.getData().getModelId());
        Object mythicMob = unwrapOptional(optionalMob);
        if (mythicMob == null) {
            throw new IllegalArgumentException("MythicMob does not exist");
        }

        Class<?> adapter = ReflectionCalls.loadFromPlugin("MythicMobs", "io.lumine.mythic.bukkit.BukkitAdapter");
        Object adaptedLocation = ReflectionCalls.invokeStatic(adapter, "adapt", npc.getData().getLocation());
        Object activeMob = ReflectionCalls.invoke(mythicMob, "spawn", adaptedLocation, 1.0D);
        final Entity entity;
        try {
            Object abstractEntity = ReflectionCalls.invoke(activeMob, "getEntity");
            entity = (Entity) ReflectionCalls.invoke(abstractEntity, "getBukkitEntity");
            secureMythicEntity(entity, npc);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            ReflectionCalls.invokeIfPresent(activeMob, "despawn");
            throw exception;
        }

        return new ModelHandle() {
            private final Set<UUID> visibleViewers = ConcurrentHashMap.newKeySet();
            private final AtomicBoolean closed = new AtomicBoolean();

            @Override
            public Entity entity() {
                return entity;
            }

            @Override
            public void show(Player player) {
                visibleViewers.add(player.getUniqueId());
                player.showEntity(plugin, entity);
                syncAttachedBetterModel(entity, player, true);
                scheduleAttachedModelSync(entity, player, visibleViewers, closed);
            }

            @Override
            public void hide(Player player) {
                visibleViewers.remove(player.getUniqueId());
                syncAttachedBetterModel(entity, player, false);
                player.hideEntity(plugin, entity);
                scheduleAttachedModelSync(entity, player, visibleViewers, closed);
            }

            @Override
            public void teleport(Location location) {
                entity.teleportAsync(location);
            }

            @Override
            public void lookAt(Location location) {
                entity.setRotation(location.getYaw(), location.getPitch());
            }

            @Override
            public void close() {
                closed.set(true);
                visibleViewers.clear();
                ReflectionCalls.invokeIfPresent(activeMob, "despawn");
                if (entity.isValid()) {
                    entity.remove();
                }
            }
        };
    }

    private void secureMythicEntity(Entity entity, Npc npc) {
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(false);
        entity.setGlowing(npc.getData().isGlowing());
        entity.getPersistentDataContainer().set(mythicMarker, PersistentDataType.STRING, npc.getData().getId());

        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.setCollidable(false);
            livingEntity.setRemoveWhenFarAway(false);
        }
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
            mob.setCanPickupItems(false);
        }
    }

    private Interaction createModelAnchor(Npc npc) {
        Location location = npc.getData().getLocation();
        if (location == null || location.getWorld() == null) {
            throw new IllegalStateException("NPC location is not in a loaded world");
        }

        float scale = Math.max(0.1F, Math.min(16.0F, npc.getData().getScale()));
        return location.getWorld().spawn(location, Interaction.class, anchor -> {
            anchor.setInteractionWidth(scale);
            anchor.setInteractionHeight(scale * 2.0F);
            anchor.setResponsive(true);
            anchor.setInvulnerable(true);
            anchor.setGravity(false);
            anchor.setPersistent(false);
            anchor.setSilent(true);
            anchor.getPersistentDataContainer().set(modelAnchorMarker, PersistentDataType.STRING, npc.getData().getId());
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideEntity(plugin, anchor);
            }
        });
    }

    private void scheduleAttachedModelSync(
            Entity entity,
            Player player,
            Set<UUID> visibleViewers,
            AtomicBoolean closed
    ) {
        plugin.getScheduler().runTaskLater(entity.getLocation(), 10L, () -> {
            if (closed.get() || !entity.isValid() || !player.isOnline()) {
                return;
            }
            syncAttachedBetterModel(entity, player, visibleViewers.contains(player.getUniqueId()));
        });
    }

    private void syncAttachedBetterModel(Entity entity, Player player, boolean visible) {
        if (!Bukkit.getPluginManager().isPluginEnabled("BetterModel")) {
            return;
        }

        try {
            Class<?> betterModel = ReflectionCalls.loadFromPlugin("BetterModel", "kr.toxicity.model.api.BetterModel");
            Class<?> adapter = ReflectionCalls.loadFromPlugin("BetterModel", "kr.toxicity.model.api.bukkit.platform.BukkitAdapter");
            Object adaptedEntity = ReflectionCalls.invokeStatic(adapter, "adapt", entity);
            Object registry = unwrapOptional(ReflectionCalls.invokeStatic(betterModel, "registry", adaptedEntity));
            if (registry == null) {
                return;
            }
            Object adaptedPlayer = ReflectionCalls.invokeStatic(adapter, "adapt", player);
            ReflectionCalls.invokeIfPresent(registry, visible ? "spawn" : "remove", adaptedPlayer);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    private Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }

    interface ModelHandle {
        default Entity entity() {
            return null;
        }

        default void show(Player player) {
        }

        default void hide(Player player) {
        }

        default void teleport(Location location) {
        }

        default void lookAt(Location location) {
        }

        void close();
    }

    private record SuggestionCacheEntry(List<String> modelIds, long createdAtNanos) {
    }
}
