package de.oliver.fancynpcs.runtime;

import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.NpcRuntime;
import de.oliver.fancynpcs.api.actions.ActionTrigger;
import de.oliver.fancynpcs.api.events.NpcPreInteractEvent;
import de.oliver.fancynpcs.api.model.NpcModelProvider;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TwiNpcRuntime implements NpcRuntime, Listener {

    private final FancyNpcs plugin;
    private final BedrockPlayerDetector bedrockDetector = new BedrockPlayerDetector();
    private final ModelIntegrationManager modelManager;
    private final Map<ViewKey, FallbackView> fallbackViews = new ConcurrentHashMap<>();
    private final Map<InteractionKey, Npc> interactionAliases = new ConcurrentHashMap<>();
    private final Map<UUID, Npc> modelEntities = new ConcurrentHashMap<>();
    private final Map<ViewKey, Long> viewStates = new ConcurrentHashMap<>();
    private final AtomicLong viewStateCounter = new AtomicLong();

    public TwiNpcRuntime(FancyNpcs plugin) {
        this.plugin = plugin;
        this.modelManager = new ModelIntegrationManager(plugin);
    }

    public List<String> getModelSuggestions(NpcModelProvider provider, String input) {
        return modelManager.suggestions(provider, input);
    }

    public boolean isBedrockPlayer(Player player) {
        return bedrockDetector.isBedrock(player);
    }

    @Override
    public void afterCreate(Npc npc) {
        modelEntities.entrySet().removeIf(entry -> entry.getValue().getData().getId().equals(npc.getData().getId()));
        modelManager.afterCreate(npc);
        Entity modelEntity = modelManager.getModelEntity(npc);
        if (modelEntity != null) {
            modelEntities.put(modelEntity.getUniqueId(), npc);
        }
    }

    @Override
    public boolean spawnOverride(Npc npc, Player player) {
        if (npc.getData().isRuntimeView()) {
            return false;
        }

        if (bedrockDetector.isBedrock(player) && npc.getData().getBedrockFallbackType() != null) {
            ViewKey key = new ViewKey(npc.getData().getId(), player.getUniqueId());
            long state = setDesiredView(key, true);
            runAtNpc(npc, () -> {
                if (!isDesiredView(key, state)) {
                    return;
                }
                if (!player.isOnline()) {
                    clearFailedSpawn(npc, player, key, state);
                    return;
                }

                try {
                    modelManager.hide(npc, player);
                    fallback(npc, player).spawn(player);
                } catch (RuntimeException | LinkageError exception) {
                    clearFailedSpawn(npc, player, key, state);
                    logRuntimeFailure("show bedrock fallback", npc, player, exception);
                }
            });
            return true;
        }

        if (modelManager.hasModel(npc)) {
            ViewKey key = new ViewKey(npc.getData().getId(), player.getUniqueId());
            long state = setDesiredView(key, true);
            runAtNpc(npc, () -> {
                if (!isDesiredView(key, state)) {
                    return;
                }
                if (!player.isOnline()) {
                    clearFailedSpawn(npc, player, key, state);
                    return;
                }

                try {
                    modelManager.show(npc, player);
                } catch (RuntimeException | LinkageError exception) {
                    clearFailedSpawn(npc, player, key, state);
                    logRuntimeFailure("show model", npc, player, exception);
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public void afterSpawn(Npc npc, Player player) {
        if (!npc.getData().isRuntimeView()) {
            modelManager.show(npc, player);
        }
    }

    @Override
    public boolean removeOverride(Npc npc, Player player) {
        if (npc.getData().isRuntimeView()) {
            return false;
        }

        ViewKey key = new ViewKey(npc.getData().getId(), player.getUniqueId());
        boolean customView = fallbackViews.containsKey(key) || usesBedrockFallback(npc, player) || modelManager.hasModel(npc);
        if (!customView) {
            return false;
        }

        long state = setDesiredView(key, false);
        runAtNpc(npc, () -> {
            if (!isDesiredView(key, state)) {
                return;
            }

            try {
                FallbackView fallback = fallbackViews.remove(key);
                if (fallback != null) {
                    interactionAliases.remove(new InteractionKey(fallback.npc().getEntityId(), player.getUniqueId()));
                    if (player.isOnline()) {
                        fallback.npc().remove(player);
                    }
                }
                if (player.isOnline()) {
                    modelManager.hide(npc, player);
                }
            } catch (RuntimeException | LinkageError exception) {
                logRuntimeFailure("hide custom view", npc, player, exception);
            } finally {
                viewStates.remove(key, state);
            }
        });
        return true;
    }

    @Override
    public boolean updateOverride(Npc npc, Player player, boolean swingArm) {
        ViewKey key = new ViewKey(npc.getData().getId(), player.getUniqueId());
        if (fallbackViews.containsKey(key) || usesBedrockFallback(npc, player)) {
            runAtNpc(npc, () -> {
                if (!shouldRunVisibleUpdate(key, player)) {
                    return;
                }
                FallbackView fallback = fallbackViews.get(key);
                if (fallback != null) {
                    syncFallback(npc, fallback.npc());
                    fallback.npc().update(player, swingArm);
                }
            });
            return true;
        }
        if (modelManager.hasModel(npc)) {
            Location target = safeClone(npc.getData().getLocation());
            runAtNpc(npc, () -> {
                if (target != null && shouldRunVisibleUpdate(key, player)) {
                    modelManager.teleport(npc, target);
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public boolean moveOverride(Npc npc, Player player, boolean swingArm) {
        ViewKey key = new ViewKey(npc.getData().getId(), player.getUniqueId());
        if (fallbackViews.containsKey(key) || usesBedrockFallback(npc, player)) {
            runAtNpc(npc, () -> {
                if (!shouldRunVisibleUpdate(key, player)) {
                    return;
                }
                FallbackView fallback = fallbackViews.get(key);
                if (fallback != null) {
                    syncFallback(npc, fallback.npc());
                    fallback.npc().move(player, swingArm);
                }
            });
            return true;
        }
        if (modelManager.hasModel(npc)) {
            Location target = safeClone(npc.getData().getLocation());
            runAtNpc(npc, () -> {
                if (target != null && shouldRunVisibleUpdate(key, player)) {
                    modelManager.teleport(npc, target);
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public boolean lookAtOverride(Npc npc, Player player, Location location) {
        ViewKey key = new ViewKey(npc.getData().getId(), player.getUniqueId());
        if (fallbackViews.containsKey(key) || usesBedrockFallback(npc, player)) {
            Location target = safeClone(location);
            runAtNpc(npc, () -> {
                if (target == null || !shouldRunVisibleUpdate(key, player)) {
                    return;
                }
                FallbackView fallback = fallbackViews.get(key);
                if (fallback != null) {
                    Location offsetLocation = target.clone().add(
                            npc.getData().getBedrockOffsetX(),
                            npc.getData().getBedrockOffsetY(),
                            npc.getData().getBedrockOffsetZ()
                    );
                    fallback.npc().lookAt(player, offsetLocation);
                }
            });
            return true;
        }
        if (modelManager.hasModel(npc)) {
            Location target = safeClone(location);
            runAtNpc(npc, () -> {
                if (target != null && shouldRunVisibleUpdate(key, player)) {
                    modelManager.lookAt(npc, target);
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public void close(Npc npc) {
        viewStates.keySet().removeIf(key -> key.npcId().equals(npc.getData().getId()));
        Entity modelEntity = modelManager.getModelEntity(npc);
        if (modelEntity != null) {
            modelEntities.remove(modelEntity.getUniqueId());
        }
        modelManager.close(npc);

        fallbackViews.entrySet().removeIf(entry -> {
            if (!entry.getKey().npcId().equals(npc.getData().getId())) {
                return false;
            }
            interactionAliases.remove(new InteractionKey(entry.getValue().npc().getEntityId(), entry.getKey().viewerId()));
            Player player = Bukkit.getPlayer(entry.getKey().viewerId());
            if (player != null) {
                entry.getValue().npc().remove(player);
            }
            return true;
        });
    }

    @Override
    public Npc resolveInteractionNpc(int entityId) {
        return null;
    }

    @Override
    public Npc resolveInteractionNpc(int entityId, Player player) {
        Npc npc = interactionAliases.get(new InteractionKey(entityId, player.getUniqueId()));
        if (npc == null || !npc.getData().isBedrockInteractionForwarding() || !npc.isShownFor(player)) {
            return null;
        }
        return npc;
    }

    @Override
    public boolean allowDirectInteraction(Npc npc, Player player) {
        return !usesBedrockFallback(npc, player);
    }

    @Override
    public void removePlayer(Player player) {
        bedrockDetector.remove(player.getUniqueId());
        viewStates.keySet().removeIf(key -> key.viewerId().equals(player.getUniqueId()));
        fallbackViews.entrySet().removeIf(entry -> {
            if (!entry.getKey().viewerId().equals(player.getUniqueId())) {
                return false;
            }
            interactionAliases.remove(new InteractionKey(entry.getValue().npc().getEntityId(), player.getUniqueId()));
            entry.getValue().npc().remove(player);
            return true;
        });
    }

    @Override
    public void shutdown() {
        for (Map.Entry<ViewKey, FallbackView> entry : fallbackViews.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey().viewerId());
            if (player != null) {
                entry.getValue().npc().remove(player);
            }
        }
        fallbackViews.clear();
        interactionAliases.clear();
        modelEntities.clear();
        viewStates.clear();
        modelManager.shutdown();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        for (ModelIntegrationManager.ModelHandle handle : modelManager.handles()) {
            if (handle.entity() != null) {
                handle.hide(event.getPlayer());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onModelInteract(PlayerInteractEntityEvent event) {
        Npc npc = modelEntities.get(event.getRightClicked().getUniqueId());
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        if (usesBedrockFallback(npc, event.getPlayer())) {
            return;
        }
        forwardInteraction(npc, event.getPlayer(), ActionTrigger.RIGHT_CLICK);
    }

    @EventHandler(ignoreCancelled = true)
    public void onModelDamage(EntityDamageByEntityEvent event) {
        Npc npc = modelEntities.get(event.getEntity().getUniqueId());
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            if (usesBedrockFallback(npc, player)) {
                return;
            }
            forwardInteraction(npc, player, ActionTrigger.LEFT_CLICK);
        }
    }

    private Npc fallback(Npc original, Player player) {
        ViewKey key = new ViewKey(original.getData().getId(), player.getUniqueId());
        return fallbackViews.computeIfAbsent(key, ignored -> {
            NpcData data = new NpcData(
                    "bedrock_" + original.getData().getId().replace("-", ""),
                    original.getData().getCreator(),
                    original.getData().getBedrockLocation()
            );
            data.setRuntimeView(true);
            Npc fallbackNpc = plugin.getNpcAdapter().apply(data);
            syncFallback(original, fallbackNpc);
            fallbackNpc.create();
            if (original.getData().isBedrockInteractionForwarding()) {
                interactionAliases.put(new InteractionKey(fallbackNpc.getEntityId(), player.getUniqueId()), original);
            }
            return new FallbackView(fallbackNpc);
        }).npc();
    }

    private void forwardInteraction(Npc npc, Player player, ActionTrigger trigger) {
        if (!new NpcPreInteractEvent(npc, player, trigger).callEvent()) {
            return;
        }
        npc.interact(player, trigger);
    }

    private boolean usesBedrockFallback(Npc npc, Player player) {
        return npc.getData().getBedrockFallbackType() != null && bedrockDetector.isBedrock(player);
    }

    private long setDesiredView(ViewKey key, boolean visible) {
        long version = viewStateCounter.incrementAndGet();
        long state = visible ? version : -version;
        viewStates.put(key, state);
        return state;
    }

    private boolean isDesiredView(ViewKey key, long state) {
        return viewStates.getOrDefault(key, 0L) == state;
    }

    private boolean shouldRunVisibleUpdate(ViewKey key, Player player) {
        return player.isOnline() && viewStates.getOrDefault(key, 0L) > 0L;
    }

    private void clearFailedSpawn(Npc npc, Player player, ViewKey key, long state) {
        if (viewStates.remove(key, state)) {
            npc.getIsVisibleForPlayer().put(player.getUniqueId(), false);
        }
    }

    private void runAtNpc(Npc npc, Runnable task) {
        Location location = safeClone(npc.getData().getLocation());
        plugin.getScheduler().runTask(location, task);
    }

    private Location safeClone(Location location) {
        return location == null ? null : location.clone();
    }

    private void logRuntimeFailure(String action, Npc npc, Player player, Throwable throwable) {
        String message = throwable.getMessage();
        plugin.getFancyLogger().warn(
                "Could not " + action + " for NPC '" + npc.getData().getName()
                        + "' and player '" + player.getName() + "': "
                        + throwable.getClass().getSimpleName()
                        + (message == null || message.isBlank() ? "" : " - " + message)
        );
    }

    private void syncFallback(Npc original, Npc fallback) {
        NpcData source = original.getData();
        NpcData target = fallback.getData();
        target.setLocation(source.getBedrockLocation());
        target.setType(source.getBedrockFallbackType());
        target.setDisplayName(source.getDisplayName());
        target.setCollidable(source.isCollidable());
        target.setGlowing(source.isGlowing());
        target.setGlowingColor(source.getGlowingColor());
        target.setScale(source.getScale());
        target.setSpawnEntity(source.isSpawnEntity());
        target.setVisibilityDistance(source.getVisibilityDistance());
        target.setModel(NpcModelProvider.VANILLA, null);
        if (source.getBedrockFallbackType() == org.bukkit.entity.EntityType.PLAYER) {
            target.setSkinData(source.getBedrockSkinData());
            target.setMirrorSkin(source.isBedrockMirrorSkin());
        } else {
            target.setSkinData(null);
            target.setMirrorSkin(false);
        }
        target.clearAttributes();
        target.getEquipment().clear();
        if (source.getEquipment() != null) {
            for (Map.Entry<NpcEquipmentSlot, ItemStack> entry : source.getEquipment().entrySet()) {
                if (entry.getValue() != null) {
                    target.addEquipment(entry.getKey(), entry.getValue().clone());
                }
            }
        }
    }

    private record ViewKey(String npcId, UUID viewerId) {
    }

    private record InteractionKey(int entityId, UUID viewerId) {
    }

    private record FallbackView(Npc npc) {
    }
}
