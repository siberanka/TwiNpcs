package de.oliver.fancynpcs.commands;

import de.oliver.fancylib.translations.Translator;
import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.skins.mineskin.MineSkinQueue;
import de.oliver.fancynpcs.skins.mojang.MojangQueue;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

public final class FancyNpcsDebugCMD {

    public static final FancyNpcsDebugCMD INSTANCE = new FancyNpcsDebugCMD();
    private final Translator translator = FancyNpcs.getInstance().getTranslator();
    private FancyNpcsDebugCMD() {
    }

    @Command("twinpcs skin_system restart_schedulers")
    @Permission("twinpcs.command.twinpcs.skin_system.restart_schedulers")
    public void onSkinSchedulerRestart(final Player player) {
        MineSkinQueue.get().getScheduler().cancel(true);
        MojangQueue.get().getScheduler().cancel(true);

        MineSkinQueue.get().run();
        MojangQueue.get().run();

        translator.translate("fancynpcs_skin_system_restart_schedulers_success").withPrefix().send(player);
    }

    @Command("twinpcs skin_system scheduler_status")
    @Permission("twinpcs.command.twinpcs.skin_system.scheduler_status")
    public void onSkinSchedulerStatus(final Player player) {
        String mineSkinStatus = MineSkinQueue.get().getScheduler().toString();
        FancyNpcs.getInstance().getFancyLogger().info("MineSkinAPI Status: " + mineSkinStatus);
        translator.translate("fancynpcs_skin_system_scheduler_status")
                .withPrefix()
                .replace("scheduler", "MineSkinAPI")
                .replace("status", mineSkinStatus)
                .send(player);

        String mojangStatus = MojangQueue.get().getScheduler().toString();
        FancyNpcs.getInstance().getFancyLogger().info("MojangAPI Status: " + mojangStatus);
        translator.translate("fancynpcs_skin_system_scheduler_status")
                .withPrefix()
                .replace("scheduler", "MojangAPI")
                .replace("status", mojangStatus)
                .send(player);
    }

    @Command("twinpcs skin_system clear_queues")
    @Permission("twinpcs.command.twinpcs.skin_system.clear_queues")
    public void onClearSkinQueues(final Player player) {
        MineSkinQueue.get().clear();
        MojangQueue.get().clear();

        translator.translate("fancynpcs_skin_system_clear_queues_success").withPrefix().send(player);
    }

    @Command("twinpcs skin_system clear_cache")
    @Permission("twinpcs.command.twinpcs.skin_system.clear_cache")
    public void onInvalidateCache(final Player player) {
        FancyNpcs.getInstance().getSkinManagerImpl().getMemCache().clear();
        FancyNpcs.getInstance().getSkinManagerImpl().getFileCache().clear();

        translator.translate("fancynpcs_skin_system_clear_cache_success").withPrefix().send(player);
    }

    @Command("twinpcs skin_system clear_uuid_cache")
    @Permission("twinpcs.command.twinpcs.skin_system.clear_uuid_cache")
    public void onInvalidateUUidCache(final Player player) {
        FancyNpcs.getInstance().getSkinManagerImpl().getUuidCache().clearCache();
        translator.translate("fancynpcs_skin_system_clear_uuid_cache_success").withPrefix().send(player);
    }
}
