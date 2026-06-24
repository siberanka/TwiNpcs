package de.oliver.fancynpcs.commands.npc;

import de.oliver.fancylib.translations.Translator;
import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcModifyEvent;
import de.oliver.fancynpcs.api.skins.SkinData;
import de.oliver.fancynpcs.api.skins.SkinLoadException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum BedrockCMD {
    INSTANCE;

    private final Translator translator = FancyNpcs.getInstance().getTranslator();

    @Command("npc bedrock <npc> type <type>")
    @Permission("twinpcs.command.npc.bedrock")
    public void onType(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final @NotNull String type
    ) {
        EntityType fallbackType;
        try {
            fallbackType = EntityType.valueOf(type.toUpperCase(Locale.ROOT));
            if (!fallbackType.isAlive() && fallbackType != EntityType.PLAYER) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            translator.translate("npc_bedrock_invalid_type").withPrefix().replaceStripped("type", type).send(sender);
            return;
        }

        if (!new NpcModifyEvent(npc, NpcModifyEvent.NpcModification.BEDROCK_VIEW, fallbackType, sender).callEvent()) {
            translator.translate("command_npc_modification_cancelled").withPrefix().send(sender);
            return;
        }

        npc.getData().setBedrockFallbackType(fallbackType);
        NpcRefresh.recreate(npc);
        translator.translate("npc_bedrock_type_set")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("type", fallbackType.name().toLowerCase(Locale.ROOT))
                .send(sender);
    }

    @Command("npc bedrock <npc> skin <skin>")
    @Permission("twinpcs.command.npc.bedrock")
    public void onSkin(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final @NotNull @Argument(suggestions = "SkinCMD/skin") String skin,
            final @Flag("slim") boolean slim
    ) {
        if (npc.getData().getBedrockFallbackType() != EntityType.PLAYER) {
            translator.translate("npc_bedrock_skin_requires_player").withPrefix().send(sender);
            return;
        }

        if (skin.equalsIgnoreCase("@none")) {
            if (!modifyBedrockSkin(sender, npc, null, false)) {
                return;
            }
            translator.translate("npc_bedrock_skin_cleared")
                    .withPrefix().replace("npc", npc.getData().getName()).send(sender);
            return;
        }

        if (skin.equalsIgnoreCase("@mirror")) {
            if (!modifyBedrockSkin(sender, npc, null, true)) {
                return;
            }
            translator.translate("npc_bedrock_skin_mirror")
                    .withPrefix().replace("npc", npc.getData().getName()).send(sender);
            return;
        }

        try {
            SkinData.SkinVariant variant = slim ? SkinData.SkinVariant.SLIM : SkinData.SkinVariant.AUTO;
            SkinData skinData = FancyNpcs.getInstance().getSkinManagerImpl().getByIdentifier(skin, variant);
            skinData.setIdentifier(skin);
            if (!modifyBedrockSkin(sender, npc, skinData, false)) {
                return;
            }
            translator.translate("npc_bedrock_skin_set")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .replace("skin", skinData.getIdentifier())
                    .send(sender);
            if (!skinData.hasTexture()) {
                translator.translate("npc_skin_set_later")
                        .withPrefix().replace("npc", npc.getData().getName()).send(sender);
            }
        } catch (SkinLoadException exception) {
            switch (exception.getReason()) {
                case INVALID_URL -> translator.translate("npc_skin_failure_invalid_url").withPrefix().send(sender);
                case INVALID_FILE -> translator.translate("npc_skin_failure_invalid_file").withPrefix().send(sender);
                case INVALID_USERNAME -> translator.translate("npc_skin_failure_invalid_username").withPrefix().send(sender);
                case INVALID_PLACEHOLDER -> translator.translate("npc_skin_failure_invalid_placeholder").withPrefix().send(sender);
            }
        }
    }

    @Command("npc bedrock <npc> offset <x> <y> <z>")
    @Permission("twinpcs.command.npc.bedrock")
    public void onOffset(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final double x,
            final double y,
            final double z
    ) {
        if (!new NpcModifyEvent(npc, NpcModifyEvent.NpcModification.BEDROCK_VIEW, new double[]{x, y, z}, sender).callEvent()) {
            translator.translate("command_npc_modification_cancelled").withPrefix().send(sender);
            return;
        }

        try {
            npc.getData().setBedrockOffset(x, y, z);
        } catch (IllegalArgumentException exception) {
            translator.translate("npc_bedrock_invalid_offset").withPrefix().send(sender);
            return;
        }

        NpcRefresh.recreate(npc);
        translator.translate("npc_bedrock_offset_set")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("x", String.valueOf(x))
                .replace("y", String.valueOf(y))
                .replace("z", String.valueOf(z))
                .send(sender);
    }

    @Command("npc bedrock <npc> clear")
    @Permission("twinpcs.command.npc.bedrock")
    public void onClear(final @NotNull CommandSender sender, final @NotNull Npc npc) {
        if (!new NpcModifyEvent(npc, NpcModifyEvent.NpcModification.BEDROCK_VIEW, "clear", sender).callEvent()) {
            translator.translate("command_npc_modification_cancelled").withPrefix().send(sender);
            return;
        }

        npc.getData()
                .setBedrockFallbackType(null)
                .setBedrockSkinData(null)
                .setBedrockMirrorSkin(false)
                .setBedrockOffset(0, 0, 0);
        NpcRefresh.recreate(npc);
        translator.translate("npc_bedrock_cleared")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .send(sender);
    }

    @Command("npc bedrock <npc> interactions <enabled>")
    @Permission("twinpcs.command.npc.bedrock")
    public void onInteractions(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final boolean enabled
    ) {
        if (!new NpcModifyEvent(npc, NpcModifyEvent.NpcModification.BEDROCK_VIEW, enabled, sender).callEvent()) {
            translator.translate("command_npc_modification_cancelled").withPrefix().send(sender);
            return;
        }

        npc.getData().setBedrockInteractionForwarding(enabled);
        NpcRefresh.recreate(npc);
        translator.translate("npc_bedrock_interactions_set")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("state", enabled ? "enabled" : "disabled")
                .send(sender);
    }

    private boolean modifyBedrockSkin(
            CommandSender sender,
            Npc npc,
            SkinData skinData,
            boolean mirror
    ) {
        Object newValue = mirror ? "mirror" : skinData;
        if (!new NpcModifyEvent(npc, NpcModifyEvent.NpcModification.BEDROCK_VIEW, newValue, sender).callEvent()) {
            translator.translate("command_npc_modification_cancelled").withPrefix().send(sender);
            return false;
        }

        npc.getData().setBedrockSkinData(skinData).setBedrockMirrorSkin(mirror);
        NpcRefresh.recreate(npc);
        return true;
    }
}
