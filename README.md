# TwiNpcs

TwiNpcs is a packet-based Minecraft NPC plugin forked from
[FancyNpcs](https://github.com/FancyMcPlugins/FancyNpcs).

Maintainers and authors include the original FancyNpcs authors and `siberanka`.

## Features

- Vanilla player and mob NPCs
- Optional BetterModel, ModelEngine, and MythicMobs visuals
- Per-NPC vanilla mob fallback for Geyser/Floodgate players
- Optional Bedrock-only position offset
- Persistent NPC configuration and packet-based visibility
- Paper and Folia support across the included Minecraft implementations

## Commands

```text
/twinpcs version
/twinpcs reload
/twinpcs save

/npc model <npc> <vanilla|bettermodel|modelengine|mythicmobs> [model]
/npc bedrock <npc> type <vanilla_mob|player>
/npc bedrock <npc> skin <player|uuid|url|file|@none|@mirror> [--slim]
/npc bedrock <npc> offset <x> <y> <z>
/npc bedrock <npc> interactions <true|false>
/npc bedrock <npc> clear
```

BetterModel, ModelEngine, MythicMobs, Floodgate, and Geyser are optional.

## Build

Java 21 is used for Minecraft 1.21.x implementations and Java 25 for
Minecraft 26.x and the final plugin:

```powershell
.\gradlew.bat :plugins:twinpcs:shadowJar
```

The output is written to `plugins/twinpcs/build/libs/`.

## License

This fork remains under the repository's MIT license and retains attribution
to FancyNpcs and its contributors.
