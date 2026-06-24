package de.oliver.fancynpcs.commands.npc;

import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import org.bukkit.Bukkit;

final class NpcRefresh {

    private NpcRefresh() {
    }

    static void recreate(Npc npc) {
        npc.removeForAll();
        FancyNpcs.getInstance().getNpcRuntime().close(npc);
        npc.create();
        Bukkit.getOnlinePlayers().forEach(npc::checkAndUpdateVisibility);
    }
}
