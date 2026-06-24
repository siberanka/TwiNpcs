package de.oliver.fancynpcs;

import de.oliver.fancyanalytics.logger.ExtendedFancyLogger;
import de.oliver.fancyanalytics.logger.properties.ThrowableProperty;
import de.oliver.fancylib.serverSoftware.ServerSoftware;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.NpcManager;
import de.oliver.fancynpcs.api.actions.ActionTrigger;
import de.oliver.fancynpcs.api.actions.NpcAction;
import de.oliver.fancynpcs.api.actions.types.UnknownActionAction;
import de.oliver.fancynpcs.api.data.property.NpcVisibility;
import de.oliver.fancynpcs.api.events.NpcsLoadedEvent;
import de.oliver.fancynpcs.api.skins.SkinData;
import de.oliver.fancynpcs.api.skins.SkinLoadException;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import de.oliver.fancynpcs.listeners.NpcViewRefresh;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class NpcManagerImpl implements NpcManager {

    private final JavaPlugin plugin;
    private final ExtendedFancyLogger logger;
    private final Function<NpcData, Npc> npcAdapter;
    private final File npcConfigFile;
    private final Map<String, Npc> npcs; // npc id -> npc
    private boolean isLoaded;

    public NpcManagerImpl(JavaPlugin plugin, Function<NpcData, Npc> npcAdapter) {
        this.plugin = plugin;
        this.logger = FancyNpcs.getInstance().getFancyLogger();
        this.npcAdapter = npcAdapter;
        npcs = new ConcurrentHashMap<>();
        npcConfigFile = new File(plugin.getDataFolder(), "npcs.yml");
        migrateLegacyNpcFile();
        isLoaded = false;
    }

    private void migrateLegacyNpcFile() {
        File legacyFile = new File("plugins" + File.separator + "FancyNpcs" + File.separator + "npcs.yml");
        if (npcConfigFile.exists() || !legacyFile.isFile()) {
            return;
        }
        try {
            Files.createDirectories(npcConfigFile.toPath().getParent());
            Files.copy(legacyFile.toPath(), npcConfigFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            logger.info("Migrated legacy FancyNpcs NPC storage to TwiNpcs.");
        } catch (IOException exception) {
            logger.warn("Could not migrate legacy FancyNpcs NPC storage: " + exception.getMessage());
        }
    }

    public void registerNpc(Npc npc) {
        if (!FancyNpcs.PLAYER_NPCS_FEATURE_FLAG.isEnabled() && getAllNpcs().stream().anyMatch(npc1 -> npc1.getData().getName().equals(npc.getData().getName()))) {
            throw new IllegalStateException("An NPC with this name already exists");
        } else {
            npcs.put(npc.getData().getId(), npc);
        }
    }

    public void removeNpc(Npc npc) {
        FancyNpcs.getInstance().getNpcRuntime().close(npc);
        if (FancyNpcs.getInstance().getVisibilityTracker() != null) {
            FancyNpcs.getInstance().getVisibilityTracker().removeNpc(npc.getData().getId());
        }
        npcs.remove(npc.getData().getId());

        YamlConfiguration npcConfig = YamlConfiguration.loadConfiguration(npcConfigFile);
        npcConfig.set("npcs." + npc.getData().getId(), null);
        try {
            npcConfig.save(npcConfigFile);
        } catch (IOException e) {
            logger.error("Could not save npc config file", ThrowableProperty.of(e));
        }
    }

    @ApiStatus.Internal
    @Override
    public Npc getNpc(int entityId) {
        Npc interactionAlias = FancyNpcs.getInstance().getNpcRuntime().resolveInteractionNpc(entityId);
        if (interactionAlias != null) {
            return interactionAlias;
        }
        for (Npc npc : getAllNpcs()) {
            if (npc.getEntityId() == entityId) {
                return npc;
            }
        }

        return null;
    }

    @Override
    public Npc getNpc(String name) {
        for (Npc npc : getAllNpcs()) {
            if (npc.getData().getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }

        return null;
    }

    @Override
    public Npc getNpcById(String id) {
        for (Npc npc : getAllNpcs()) {
            if (npc.getData().getId().equals(id)) {
                return npc;
            }
        }

        return null;
    }

    @Override
    public Npc getNpc(String name, UUID creator) {
        for (Npc npc : getAllNpcs()) {
            if (npc.getData().getCreator().equals(creator) && npc.getData().getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }

        return null;
    }

    public Collection<Npc> getAllNpcs() {
        return new ArrayList<>(npcs.values());
    }

    public void saveNpcs(boolean force) {
        if (!isLoaded) {
            return;
        }

        if (!npcConfigFile.exists()) {
            try {
                Files.createDirectories(npcConfigFile.toPath().getParent());
                npcConfigFile.createNewFile();
            } catch (IOException e) {
                logger.error("Could not create npc config file", ThrowableProperty.of(e));
                return;
            }
        }

        YamlConfiguration npcConfig = YamlConfiguration.loadConfiguration(npcConfigFile);

        for (Npc npc : getAllNpcs()) {
            if (!npc.isSaveToFile()) {
                continue;
            }

            boolean shouldSave = force || npc.isDirty();
            if (!shouldSave) {
                continue;
            }

            NpcData data = npc.getData();

            npcConfig.set("npcs." + data.getId() + ".name", data.getName());
            npcConfig.set("npcs." + data.getId() + ".creator", data.getCreator().toString());
            npcConfig.set("npcs." + data.getId() + ".displayName", data.getDisplayName());
            npcConfig.set("npcs." + data.getId() + ".type", data.getType().name());
            npcConfig.set("npcs." + data.getId() + ".location.world", data.getLocation().getWorld().getName());
            npcConfig.set("npcs." + data.getId() + ".location.x", data.getLocation().getX());
            npcConfig.set("npcs." + data.getId() + ".location.y", data.getLocation().getY());
            npcConfig.set("npcs." + data.getId() + ".location.z", data.getLocation().getZ());
            npcConfig.set("npcs." + data.getId() + ".location.yaw", data.getLocation().getYaw());
            npcConfig.set("npcs." + data.getId() + ".location.pitch", data.getLocation().getPitch());
            npcConfig.set("npcs." + data.getId() + ".showInTab", data.isShowInTab());
            npcConfig.set("npcs." + data.getId() + ".spawnEntity", data.isSpawnEntity());
            npcConfig.set("npcs." + data.getId() + ".collidable", data.isCollidable());
            npcConfig.set("npcs." + data.getId() + ".glowing", data.isGlowing());
            npcConfig.set("npcs." + data.getId() + ".glowingColor", data.getGlowingColor().toString());
            npcConfig.set("npcs." + data.getId() + ".turnToPlayer", data.isTurnToPlayer());
            npcConfig.set("npcs." + data.getId() + ".turnToPlayerDistance", data.getTurnToPlayerDistance());
            npcConfig.set("npcs." + data.getId() + ".messages", null);
            npcConfig.set("npcs." + data.getId() + ".playerCommands", null);
            npcConfig.set("npcs." + data.getId() + ".serverCommands", null);
            npcConfig.set("npcs." + data.getId() + ".sendMessagesRandomly", null);
            npcConfig.set("npcs." + data.getId() + ".interactionCooldown", data.getInteractionCooldown());
            npcConfig.set("npcs." + data.getId() + ".scale", data.getScale());
            npcConfig.set("npcs." + data.getId() + ".visibility_distance", data.getVisibilityDistance());
            npcConfig.set("npcs." + data.getId() + ".visibility", data.getVisibility().name());
            npcConfig.set("npcs." + data.getId() + ".model.provider", data.getModelProvider().name());
            npcConfig.set("npcs." + data.getId() + ".model.id", data.getModelId());
            npcConfig.set("npcs." + data.getId() + ".bedrock.type", data.getBedrockFallbackType() == null ? null : data.getBedrockFallbackType().name());
            npcConfig.set("npcs." + data.getId() + ".bedrock.offset.x", data.getBedrockOffsetX());
            npcConfig.set("npcs." + data.getId() + ".bedrock.offset.y", data.getBedrockOffsetY());
            npcConfig.set("npcs." + data.getId() + ".bedrock.offset.z", data.getBedrockOffsetZ());
            npcConfig.set("npcs." + data.getId() + ".bedrock.forward_interactions", data.isBedrockInteractionForwarding());
            if (data.getBedrockSkinData() != null) {
                npcConfig.set("npcs." + data.getId() + ".bedrock.skin.identifier", data.getBedrockSkinData().getIdentifier());
                npcConfig.set("npcs." + data.getId() + ".bedrock.skin.variant", data.getBedrockSkinData().getVariant().name());
            } else {
                npcConfig.set("npcs." + data.getId() + ".bedrock.skin.identifier", null);
                npcConfig.set("npcs." + data.getId() + ".bedrock.skin.variant", null);
            }
            npcConfig.set("npcs." + data.getId() + ".bedrock.skin.mirror", data.isBedrockMirrorSkin());

            if (data.getSkinData() != null) {
                npcConfig.set("npcs." + data.getId() + ".skin.identifier", data.getSkinData().getIdentifier());
                npcConfig.set("npcs." + data.getId() + ".skin.variant", data.getSkinData().getVariant().name());
            } else {
                npcConfig.set("npcs." + data.getId() + ".skin.identifier", null);
            }
            npcConfig.set("npcs." + data.getId() + ".skin.mirrorSkin", data.isMirrorSkin());

            if (data.getEquipment() != null) {
                for (Map.Entry<NpcEquipmentSlot, ItemStack> entry : data.getEquipment().entrySet()) {
                    npcConfig.set("npcs." + data.getId() + ".equipment." + entry.getKey().name(), entry.getValue());
                }
            }

            for (NpcAttribute attribute : FancyNpcs.getInstance().getAttributeManager().getAllAttributesForEntityType(data.getType())) {
                String value = data.getAttributes().getOrDefault(attribute, null);
                npcConfig.set("npcs." + data.getId() + ".attributes." + attribute.getName(), value);
            }

            npcConfig.set("npcs." + data.getId() + ".actions", null);
            for (Map.Entry<ActionTrigger, List<NpcAction.NpcActionData>> entry : npc.getData().getActions().entrySet()) {
                for (NpcAction.NpcActionData actionData : entry.getValue()) {
                    if (actionData == null) {
                        continue;
                    }

                    if (actionData.action() instanceof UnknownActionAction unknownActionAction) {
                        npcConfig.set("npcs." + data.getId() + ".actions." + entry.getKey().name() + "." + actionData.order() + ".action", unknownActionAction.getUnknownActionName());
                        npcConfig.set("npcs." + data.getId() + ".actions." + entry.getKey().name() + "." + actionData.order() + ".value", unknownActionAction.getUnknownActionValue());
                    } else {
                        npcConfig.set("npcs." + data.getId() + ".actions." + entry.getKey().name() + "." + actionData.order() + ".action", actionData.action().getName());
                        npcConfig.set("npcs." + data.getId() + ".actions." + entry.getKey().name() + "." + actionData.order() + ".value", actionData.value());
                    }
                }
            }

            npc.setDirty(false);
        }

        try {
            npcConfig.save(npcConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadNpcs() {
        npcs.clear();
        YamlConfiguration npcConfig = YamlConfiguration.loadConfiguration(npcConfigFile);

        if (!npcConfig.isConfigurationSection("npcs")) {
            this.setLoaded();
            return;
        }

        for (String id : npcConfig.getConfigurationSection("npcs").getKeys(false)) {
            String name = npcConfig.getString("npcs." + id + ".name");
            if (name == null) name = id;

            String creatorStr = npcConfig.getString("npcs." + id + ".creator");
            UUID creator = null;
            if (creatorStr != null) {
                try {
                    creator = UUID.fromString(creatorStr);
                } catch (IllegalArgumentException exception) {
                    logger.warn("Invalid creator UUID for NPC '" + name + "', using server owner");
                }
            }

            String displayName = npcConfig.getString("npcs." + id + ".displayName", "<empty>");
            EntityType type;
            try {
                type = EntityType.valueOf(npcConfig.getString("npcs." + id + ".type", "PLAYER").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                logger.warn("Invalid entity type for NPC '" + name + "', using PLAYER");
                type = EntityType.PLAYER;
            }

            Location location = null;

            try {
                location = npcConfig.getLocation("npcs." + id + ".location");
            } catch (Exception ignored) {
                logger.warn("Could not load location for npc '" + id + "'");
            }

            if (location == null) {
                String worldName = npcConfig.getString("npcs." + id + ".location.world");
                if (worldName == null || worldName.isBlank()) {
                    logger.warn("Could not load NPC '" + id + "', because its world is missing");
                    continue;
                }
                World world = Bukkit.getWorld(worldName);

                if (world == null) {
                    File worldFolder = new File(worldName);
                    if (worldFolder.exists() && worldFolder.isDirectory()) {
                        world = (!ServerSoftware.isFolia()) ? new WorldCreator(worldName).createWorld() : null;
                    }
                }

                if (world == null) {
                    logger.info("Could not load npc '" + id + "', because the world '" + worldName + "' is not loaded");
                    continue;
                }

                double x = npcConfig.getDouble("npcs." + id + ".location.x");
                double y = npcConfig.getDouble("npcs." + id + ".location.y");
                double z = npcConfig.getDouble("npcs." + id + ".location.z");
                float yaw = (float) npcConfig.getDouble("npcs." + id + ".location.yaw");
                float pitch = (float) npcConfig.getDouble("npcs." + id + ".location.pitch");

                location = new Location(world, x, y, z, yaw, pitch);
            }

            SkinData skin = null;
            String skinIdentifier = npcConfig.getString("npcs." + id + ".skin.identifier", npcConfig.getString("npcs." + id + ".skin.uuid", ""));
            String skinVariantStr = npcConfig.getString("npcs." + id + ".skin.variant", SkinData.SkinVariant.AUTO.name());
            SkinData.SkinVariant skinVariant;
            try {
                skinVariant = SkinData.SkinVariant.valueOf(skinVariantStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                skinVariant = SkinData.SkinVariant.AUTO;
                logger.warn("Invalid skin variant for NPC '" + name + "', using AUTO");
            }
            if (!skinIdentifier.isEmpty()) {
                try {
                    skin = FancyNpcs.getInstance().getSkinManagerImpl().getByIdentifier(skinIdentifier, skinVariant);
                    skin.setIdentifier(skinIdentifier);
                } catch (final SkinLoadException e) {
                    logger.error("NPC named '" + name + "' identified by '" + id + "' could not have their skin loaded.");
                    logger.error("  " + e.getReason() + " " + e.getMessage());
                }
            }


            if (npcConfig.isSet("npcs." + id + ".skin.value") && npcConfig.isSet("npcs." + id + ".skin.signature")) {
                // using old skin system --> take backup
                takeBackup(npcConfig);

                String value = npcConfig.getString("npcs." + id + ".skin.value");
                String signature = npcConfig.getString("npcs." + id + ".skin.signature");

                if (value != null && !value.isEmpty() && signature != null && !signature.isEmpty()) {
                    SkinData oldSkin = new SkinData(skinIdentifier, SkinData.SkinVariant.AUTO, value, signature);
                    FancyNpcs.getInstance().getSkinManagerImpl().getFileCache().addSkin(oldSkin);
                    FancyNpcs.getInstance().getSkinManagerImpl().getMemCache().addSkin(oldSkin);
                }
            }

            boolean mirrorSkin = npcConfig.getBoolean("npcs." + id + ".skin.mirrorSkin");

            boolean showInTab = npcConfig.getBoolean("npcs." + id + ".showInTab");
            boolean spawnEntity = npcConfig.getBoolean("npcs." + id + ".spawnEntity");
            boolean collidable = npcConfig.getBoolean("npcs." + id + ".collidable", true);
            boolean glowing = npcConfig.getBoolean("npcs." + id + ".glowing");
            NamedTextColor glowingColor = NamedTextColor.NAMES.value(npcConfig.getString("npcs." + id + ".glowingColor", "white"));
            if (glowingColor == null) {
                glowingColor = NamedTextColor.WHITE;
            }
            boolean turnToPlayer = npcConfig.getBoolean("npcs." + id + ".turnToPlayer");
            int turnToPlayerDistance = npcConfig.getInt("npcs." + id + ".turnToPlayerDistance", -1);

            Map<ActionTrigger, List<NpcAction.NpcActionData>> actions = new ConcurrentHashMap<>();

            ConfigurationSection actiontriggerSection = npcConfig.getConfigurationSection("npcs." + id + ".actions");
            if (actiontriggerSection != null) {
                actiontriggerSection.getKeys(false).forEach(trigger -> {
                    ActionTrigger actionTrigger = ActionTrigger.getByName(trigger);
                    if (actionTrigger == null) {
                        logger.warn("Could not find action trigger: " + trigger);
                        return;
                    }

                    List<NpcAction.NpcActionData> actionList = new ArrayList<>();
                    ConfigurationSection actionsSection = npcConfig.getConfigurationSection("npcs." + id + ".actions." + trigger);
                    if (actionsSection != null) {
                        actionsSection.getKeys(false).forEach(order -> {
                            String actionName = npcConfig.getString("npcs." + id + ".actions." + trigger + "." + order + ".action");
                            String value = npcConfig.getString("npcs." + id + ".actions." + trigger + "." + order + ".value");
                            try {
                                int parsedOrder = Integer.parseInt(order);
                                NpcAction action = FancyNpcs.getInstance().getActionManager().getActionByName(actionName);
                                if (action == null) {
                                    logger.warn("Could not find action: " + actionName);
                                    action = new UnknownActionAction(actionTrigger, actionName, value, parsedOrder);
                                }
                                actionList.add(new NpcAction.NpcActionData(parsedOrder, action, value));
                            } catch (NumberFormatException e) {
                                logger.warn("Could not parse order: " + order);
                            }
                        });

                        actions.put(actionTrigger, actionList);
                    }
                });
            }

            float interactionCooldown = (float) npcConfig.getDouble("npcs." + id + ".interactionCooldown", 0);
            float scale = (float) npcConfig.getDouble("npcs." + id + ".scale", 1);
            int visibilityDistance = npcConfig.getInt("npcs." + id + ".visibility_distance", -1);
            String visibilityStr = npcConfig.getString("npcs." + id + ".visibility", "ALL");
            NpcVisibility visibility = NpcVisibility.byString(visibilityStr).orElse(NpcVisibility.ALL);
            de.oliver.fancynpcs.api.model.NpcModelProvider modelProvider;
            try {
                modelProvider = de.oliver.fancynpcs.api.model.NpcModelProvider.valueOf(
                        npcConfig.getString("npcs." + id + ".model.provider", "VANILLA").toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException exception) {
                modelProvider = de.oliver.fancynpcs.api.model.NpcModelProvider.VANILLA;
                logger.warn("Invalid model provider for NPC '" + name + "', using vanilla");
            }
            String modelId = npcConfig.getString("npcs." + id + ".model.id");
            EntityType bedrockType = null;
            String bedrockTypeName = npcConfig.getString("npcs." + id + ".bedrock.type");
            if (bedrockTypeName != null) {
                try {
                    EntityType parsedType = EntityType.valueOf(bedrockTypeName.toUpperCase(Locale.ROOT));
                    if (parsedType.isAlive() || parsedType == EntityType.PLAYER) {
                        bedrockType = parsedType;
                    }
                } catch (IllegalArgumentException exception) {
                    logger.warn("Invalid Bedrock fallback type for NPC '" + name + "'");
                }
            }

            SkinData bedrockSkin = null;
            String bedrockSkinIdentifier = npcConfig.getString("npcs." + id + ".bedrock.skin.identifier", "");
            String bedrockSkinVariantName = npcConfig.getString(
                    "npcs." + id + ".bedrock.skin.variant",
                    SkinData.SkinVariant.AUTO.name()
            );
            if (bedrockSkinIdentifier != null && !bedrockSkinIdentifier.isBlank()) {
                SkinData.SkinVariant bedrockSkinVariant;
                try {
                    bedrockSkinVariant = SkinData.SkinVariant.valueOf(bedrockSkinVariantName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    bedrockSkinVariant = SkinData.SkinVariant.AUTO;
                    logger.warn("Invalid Bedrock skin variant for NPC '" + name + "', using AUTO");
                }
                try {
                    bedrockSkin = FancyNpcs.getInstance().getSkinManagerImpl()
                            .getByIdentifier(bedrockSkinIdentifier, bedrockSkinVariant);
                    bedrockSkin.setIdentifier(bedrockSkinIdentifier);
                } catch (SkinLoadException exception) {
                    logger.warn("Could not load Bedrock skin for NPC '" + name + "': " + exception.getReason());
                }
            }

            Map<NpcAttribute, String> attributes = new HashMap<>();
            if (npcConfig.isConfigurationSection("npcs." + id + ".attributes")) {
                for (String attrName : npcConfig.getConfigurationSection("npcs." + id + ".attributes").getKeys(false)) {
                    NpcAttribute attribute = FancyNpcs.getInstance().getAttributeManager().getAttributeByName(type, attrName);
                    if (attribute == null) {
                        logger.warn("Could not find attribute: " + attrName);
                        continue;
                    }

                    String value = npcConfig.getString("npcs." + id + ".attributes." + attrName);
                    if (!attribute.isValidValue(value)) {
                        logger.warn("Invalid value for attribute: " + attrName);
                        continue;
                    }

                    attributes.put(attribute, value);
                }
            }

            NpcData data = new NpcData(
                    id,
                    name,
                    creator,
                    displayName,
                    skin,
                    location,
                    showInTab,
                    spawnEntity,
                    collidable,
                    glowing,
                    glowingColor,
                    type,
                    new HashMap<>(),
                    turnToPlayer,
                    turnToPlayerDistance,
                    null,
                    actions,
                    interactionCooldown,
                    scale,
                    visibilityDistance,
                    attributes,
                    mirrorSkin
            );
            try {
                data.setModel(modelProvider, modelId);
            } catch (IllegalArgumentException exception) {
                data.setModel(de.oliver.fancynpcs.api.model.NpcModelProvider.VANILLA, null);
                logger.warn("Invalid model id for NPC '" + name + "', using vanilla");
            }
            data.setBedrockFallbackType(bedrockType);
            data.setBedrockSkinData(bedrockSkin);
            data.setBedrockMirrorSkin(npcConfig.getBoolean("npcs." + id + ".bedrock.skin.mirror", false));
            data.setBedrockInteractionForwarding(
                    npcConfig.getBoolean("npcs." + id + ".bedrock.forward_interactions", true)
            );
            try {
                data.setBedrockOffset(
                        npcConfig.getDouble("npcs." + id + ".bedrock.offset.x"),
                        npcConfig.getDouble("npcs." + id + ".bedrock.offset.y"),
                        npcConfig.getDouble("npcs." + id + ".bedrock.offset.z")
                );
            } catch (IllegalArgumentException exception) {
                data.setBedrockOffset(0, 0, 0);
                logger.warn("Invalid Bedrock offset for NPC '" + name + "', using zero offset");
            }
            Npc npc = npcAdapter.apply(data);

            if (npcConfig.isConfigurationSection("npcs." + id + ".equipment")) {
                for (String equipmentSlotStr : npcConfig.getConfigurationSection("npcs." + id + ".equipment").getKeys(false)) {
                    NpcEquipmentSlot equipmentSlot = NpcEquipmentSlot.parse(equipmentSlotStr);
                    ItemStack item = npcConfig.getItemStack("npcs." + id + ".equipment." + equipmentSlotStr);
                    npc.getData().addEquipment(equipmentSlot, item);
                }
            }

            npc.getData().setVisibility(visibility);
            npc.create();
            registerNpc(npc);
        }
        this.setLoaded();
    }

    @Override
    public boolean isLoaded() {
        return isLoaded;
    }

    private void setLoaded() {
        isLoaded = true;
        new NpcsLoadedEvent().callEvent();
        refreshOnlineJavaViewersAfterLoad();
    }

    private void refreshOnlineJavaViewersAfterLoad() {
        FancyNpcs.getInstance().getScheduler().runTaskLater(null, 20L, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                NpcViewRefresh.refreshJavaView(player);
            }
        });
    }

    public void reloadNpcs() {
        Collection<Npc> npcCopy = new ArrayList<>(getAllNpcs());
        npcs.clear();
        for (Npc npc : npcCopy) {
            npc.removeForAll();
            FancyNpcs.getInstance().getNpcRuntime().close(npc);
        }

        loadNpcs();
    }

    private void takeBackup(YamlConfiguration npcConfig) {
        String folderPath = plugin.getDataFolder() + File.separator + "backups";
        File backupDir = new File(folderPath);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String backupFileName = "npcs-" + formatter.format(now) + ".yml";
        File backupFile = new File(folderPath + File.separator + backupFileName);
        if (backupFile.exists()) {
            backupFile.delete();
        }

        try {
            backupFile.createNewFile();
        } catch (IOException e) {
            logger.error("Could not create backup file for NPCs");
        }

        try {
            npcConfig.save(backupFile);
        } catch (IOException e) {
            logger.error("Could not save backup file for NPCs");
        }
    }
}
