package de.oliver.fancynpcs.commands.npc;

import de.oliver.fancylib.translations.Translator;
import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcModifyEvent;
import de.oliver.fancynpcs.api.model.NpcModelProvider;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public enum ModelCMD {
    INSTANCE;

    private final Translator translator = FancyNpcs.getInstance().getTranslator();

    @Command("npc model <npc> <provider> [model]")
    @Permission("twinpcs.command.npc.model")
    public void onModel(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final @NotNull NpcModelProvider provider,
            final @Nullable @Argument(suggestions = "ModelCMD/model") String model
    ) {
        if (provider != NpcModelProvider.VANILLA && (model == null || model.isBlank())) {
            translator.translate("npc_model_id_required").withPrefix().send(sender);
            return;
        }

        String requiredPlugin = switch (provider) {
            case VANILLA -> null;
            case BETTERMODEL -> "BetterModel";
            case MODELENGINE -> "ModelEngine";
            case MYTHICMOBS -> "MythicMobs";
        };
        if (requiredPlugin != null && !Bukkit.getPluginManager().isPluginEnabled(requiredPlugin)) {
            translator.translate("npc_model_provider_unavailable")
                    .withPrefix()
                    .replace("provider", requiredPlugin)
                    .send(sender);
            return;
        }

        String normalizedModel = model == null ? null : model.trim();
        if (!new NpcModifyEvent(npc, NpcModifyEvent.NpcModification.MODEL, provider.name() + ":" + normalizedModel, sender).callEvent()) {
            translator.translate("command_npc_modification_cancelled").withPrefix().send(sender);
            return;
        }

        try {
            npc.getData().setModel(provider, normalizedModel);
        } catch (IllegalArgumentException exception) {
            translator.translate("npc_model_invalid_id").withPrefix().send(sender);
            return;
        }

        NpcRefresh.recreate(npc);
        translator.translate(provider == NpcModelProvider.VANILLA ? "npc_model_cleared" : "npc_model_set")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("provider", provider.name().toLowerCase())
                .replace("model", normalizedModel == null ? "vanilla" : normalizedModel)
                .send(sender);
    }

    @Suggestions("ModelCMD/model")
    public List<String> suggestModels(
            final CommandContext<CommandSender> context,
            final CommandInput input
    ) {
        if (!context.hasPermission("twinpcs.command.npc.model")) {
            return List.of();
        }

        NpcModelProvider provider = context.getOrDefault("provider", null);
        if (provider == null || provider == NpcModelProvider.VANILLA) {
            return List.of();
        }

        String prefix = input.lastRemainingToken();
        if (prefix.length() > 128) {
            return List.of();
        }
        return FancyNpcs.getInstance().getNpcRuntime().getModelSuggestions(provider, prefix);
    }
}
