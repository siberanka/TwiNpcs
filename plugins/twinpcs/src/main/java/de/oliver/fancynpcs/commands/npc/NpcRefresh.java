package de.oliver.fancynpcs.commands.npc;

import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;

final class NpcRefresh {

    private NpcRefresh() {
    }

    static void recreate(Npc npc) {
        FancyNpcs.getInstance().getNpcRuntime().refreshNpc(npc);
    }
}
