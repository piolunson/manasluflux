# ManasluFlux — Feature List

A Meteor Client addon for Minecraft 1.21.11 (Fabric) by piolunson.

> **This document is the single source of truth for everything the addon adds.**
> It is updated every time the addon is rebuilt. Last updated: **v0.11.0 (Minecraft 1.21.11)**.

> Also updated on every build: the website in [`../manaslu/`](../manaslu/index.html) — hero version chip, download button/jar link (fresh jar copied into `manaslu/downloads/`), command list and changelog.

Current version: see [`gradle/libs.versions.toml`](gradle/libs.versions.toml) (`mod-version`).
Build output: `build/libs/manasluflux-<version>.jar`.

---

## Modules (46)

### Category: Manaslu Flux Combat (8)

| Module | ID | What it does |
|---|---|---|
| AC PacketFlood | `ac-pcktflood` | **Crystal aura on the packet timeline** — place and break are raw packets fired by a millisecond scheduler instead of the 50 ms game tick. **Place** (`place`, `place-delay-ms` 1 ms = ~1000 place packets/s, 0 = burst to the cap, `place-retries` 0, `place-retry-delay-ms` 2, `bases-per-tick`, `place-range`, `place-walls-range`, `support-blocks`), **Break** (`break`, `break-delay-ms` 1 ms = ~1000 attacks/s, 0 = burst to the cap, `break-retries` 0, `break-retry-delay-ms` 2, `break-range`, `break-walls-range`), **General** (`target-range`, `predict-movement`, `min-damage`, `max-self-damage`, `anti-suicide`, `rotate`, `face-place` + health/durability/missing-armor), **Packet** (`max-packets-per-second` 60, `debug-mode`). Retries are dropped the moment the server's spawn/destroy packet confirms the action, so with `place-delay-ms` 1 and `place-retries` 2 the timeline is `1ms:packet 3ms:retry 5ms:retry … 6ms:packet`. |
| Auto Totem | `auto-totem` | Instantly refills a totem of undying into your offhand whenever it is used or pops. Settings: `delay`; **Packet** group — `packet-level` (refill through raw inventory click packets and detect the pop from the entity status packet, removing the inventory/tick delay), `packet-retries`, `packet-retry-delay`, `max-packets-per-second`. |
| Clicker | `clicker` | Left/right auto clicker with Nothing/Hold/Click modes and per-action tick delays. |
| Crystal Aura | `crystal-aura` | **Port of Meteor Client's CrystalAura** — simulates the crystal explosion for every obsidian/bedrock base in range and places/pops only the most damaging one. Settings: target-range, predict-movement, min-damage, max-self-damage, anti-suicide, silent rotate; **Place** (place, place-delay, place-range, place-walls-range, support-blocks + support-delay); **Face Place** (face-place, health, durability, missing-armor); **Break** (break, break-delay, break-range, break-walls-range, attack-frequency); **Pause** (pause-on-mine, pause-on-eat); **Packet** (packet-level, packet-retries, packet-retry-delay, max-packets-per-second — raw place/break packets, crystal spawn/destroy read straight from the packet stream so a crystal is popped the moment the server confirms it, unconfirmed packets resent, every send capped per second). All silent: server-side hotbar swaps and look packets — no visible rotation or hotbar movement. |
| Instant TNT | `instant-tnt` | Automatically ignites TNT just by looking at it — silent server-side switch to flint & steel/fire charge. **Click Through Walls**: scans in reach and ignites TNT behind walls and around corners with a synthesized use packet. **Redstone Block** (default on): when no igniter is in the inventory, silently places a redstone block next to the TNT instead — redstone power primes TNT. **Mine Redstone Block** (default on): the placed redstone block is packet-mined again during the TNT's fuse with a silent hotbar-pickaxe swap, so the dust lands back in your inventory. **Bow Fallback** (default on): last resort when there is no igniter and no redstone block either — silently swaps to a bow, draws and releases after 10 ticks, and shoots the TNT; only fires when the TNT is in your crosshair (arrows fly where you look) and only with a **Flame** bow (or any bow in creative) plus an arrow, since flaming arrows ignite TNT on contact. |
| Pearl Phase | `pearl-phase` | Ported from BlackOut's Auto Pearl — one-shot pearl throw at your own block (yaw +180°, steep pitch) to clip inside walls. Tick-based: rotates, throws on the next tick so the server sees the rotation, restores your view and hotbar, auto-toggles off. Settings: pitch, switch mode (Normal/Silent hotbar swap), instant rotation (direct rotation packet), keep rotation. |
| Silent Aura | `silent-aura` | Attacks the nearest player without rotating or swinging your visible hand — **silent aim** (server-side-only look rotation at the target, body or head) plus a silent weapon switch. **Pause** group: `pause-on-mine` and `pause-on-eat` temporarily stop the aura while you mine or eat/drink. |
| TNT Placer | `tnt-placer` | Traps the nearest player in (crying) obsidian, buries them in TNT and ignites it — non-stop. |

### Category: Manaslu Flux Movement (5)

| Module | ID | What it does |
|---|---|---|
| Auto Walk Hold | `auto-walk-hold` | Holds a configurable key (default **2**) down while enabled — keeps it pressed even after screens open or window focus changes. |
| Boat Flight | `boat-flight` | Fly while riding a boat — horizontal and vertical speed settings, jump = up, sneak = down. |
| Click TP | `mf-click-tp` | Hold use while looking at a block to teleport there in configurable steps, with max distance, step size, delay and an optional safe-landing check. |
| Elytra Flight | `elytra-flight` | Controlled elytra flight without fireworks — horizontal and vertical speed settings. **Auto-forward** toggle (default off): on = constant thrust in your look direction, off = you thrust only while holding the forward key. Jump = up, sneak = down work anytime. |
| Universal Flight | `universal-flight` | Enables flight — vanilla creative flight with an adjustable speed. **always-fly** ON forces flight permanently; OFF only grants the flight permission so you can toggle flying with a double-jump exactly like creative mode. |

### Category: Manaslu Flux Player (7)

| Module | ID | What it does |
|---|---|---|
| Auto Eat | `auto-eat` | Automatically eats food from your hotbar when hungry. |
| Auto Fish | `auto-fish` | Automatically throws the rod, reels in fish, looks up and recasts. |
| Auto Log | `auto-log` | Automatically disconnects when your health drops below a threshold. |
| Auto Login | `auto-login` | Sends `/<command> <password>` (default `/login 12345678`) automatically after joining a server — or when chat shows a trigger word like "register". Settings: command, password, mode (Always/Trigger), triggers, delay, repeat-on-trigger, cooldown. The password is stored in plain text in the Meteor config — don't reuse an important one. |
| Auto Respawn | `mf-auto-respawn` | Auto-clicks respawn on the death screen, then optionally runs commands/chat lines (one per tick) after respawning. |
| Inventory Keeper | `inventory-keeper` | Keeps your inventory in the layout you saved. Press the **Save layout** button on the module screen and the occupied slots become the reference; every `refresh-rate` ms the module re-reads the inventory and swaps anything that drifted back into place — the item that was sitting in a saved slot is swapped out to where the wanted item came from, so nothing is dropped and nothing leaves the inventory. `only-hotbar` restricts the kept slots to the nine hotbar slots. Items that were used up, dropped or left in a container are skipped. Checks only run with no screen open and an empty cursor, so it never fights a drag, and at most 8 swaps happen per check (a badly scrambled inventory settles over a few refreshes). The layout is saved in the Meteor config and survives restarts. |
| Whitelist Fast Use | `whitelist-fast-use` | Toggles Meteor's FastUse automatically based on the item you are holding. |

### Category: Manaslu Flux World (4)

| Module | ID | What it does |
|---|---|---|
| Auto Map Part | `auto-map-part` | When you place an item frame, it automatically fills it with a randomly chosen filled map from your hotbar — map-art building made instant. **Instant** by default: the map goes in the same tick the frame spawns; a configurable **Delay** (0–20 ticks, slider) slows it down if wanted (vanilla click-feel is 3). **Silent** (default on): the map is taken with a server-side hotbar swap, so your visible hotbar slot never changes. Remembers already-filled frames so nothing is placed twice, and shows how many frames it has filled in the HUD. |
| Auto Wither | `auto-wither` | Aim anywhere and **right-click to build a full wither** at the aimed spot — 2 soul sand columns plus the middle block, 3 wither skulls on top (center skull optional), placed via silent hotbar swaps. **Range** (default 4.5, up to 20): how far away the aimed build spot is allowed to be. **Air place** supported, **rotation lock** to a cardinal direction, live ghost preview with shrunk skull boxes and custom colors, **delay** (ticks between blocks, 0 = all in one tick). Optionally **nametags** the spawned wither right after it appears: creative mode writes your configured name onto the tag client-side, survival uses an already-renamed tag; **nametag-delay** is configurable (0 = instant). Ported from Trouser-Streak (which skidded it from Germanminer). |
| Block Replacer | `block-replacer` | Places back the same block when one of your placed blocks gets mined or removed. |
| Placer | `placer` | Places whitelisted blocks everywhere around you — fills nearby air with the selected blocks straight from your inventory (nearest first). Settings: block whitelist, radius (1–6), blocks-per-tick, silent rotation, check-entities. |

### Category: Manaslu Flux Client Side (9)

| Module | ID | What it does |
|---|---|---|
| Anti Drop Lagback | `anti-drop-lagback` | Watches the server's position-sync teleports and counts "rubberbands" (corrections that snap you back ≥ `min-distance`, default 1.5 blocks) inside a sliding window (`window-seconds`, default 5 s). Passing the cap (`rubberbands`, default 3) **pauses every other active module** for `pause-ticks` (default 40) so whatever causes the rubberbands stops, then switches everything back on. `chat-notifier` (default on) announces each rubberband and each pause/resume in local chat — purely client-side. |
| Client Block Entities | `client-block-entities` | Pick any block entity types by id (chests, signs, beacons, spawners, shulker boxes…) and their block-entity rendering is done client-side only — **Simple** replaces the block-entity part with a single static box, **Hide** removes it entirely (the block itself still renders normally). Like Crystal Optimizer but for any block entity. Purely client-side (mixin into the block-entity dispatcher), big FPS boost near many heavy block entities. |
| Client-Side Night Vision | `client-side-night-vision` | Night vision potion effect client-side only — the server never sees the effect. |
| Crystal Optimizer | `crystal-optimizer` | Replaces every end crystal's full animated model (outer cube, inner core, bedrock base, beam) with a single static box — or hides crystals entirely — a big FPS boost with lots of crystals on screen. Purely client-side rendering via a tiny renderer mixin: nothing is sent to the server and crystals behave exactly the same. Settings: mode (simple box / hide), box shape (fill/lines), box color. |
| Packet Limiter | `packet-limiter` | Caps outgoing packets per tick (keep-alive/pong always allowed) so laggy modules can't get you kicked for flooding, with a debug counter. |
| Ping Spoofer | `ping-spoofer` | Changes your ping with three modes: **Real** (no change), **More** (genuinely adds latency by delaying keep-alive/pong replies, so the server sees real extra ms) or **Spoof** (replies sent instantly, only the tab-list ping is patched). Amount, jitter and a debug readout are configurable. |
| Sound Muter | `sound-muter` | Mutes specific sounds picked from a sound list, client-side. |
| Toggle Tab | `toggle-tab` | Keeps the player list (tab) open by making the game think you are holding Tab. |
| Universal Colored Chat | `universal-colored-chat` | Replaces every `&` in chat with the color code sign. |

### Category: Manaslu Flux Utility (13)

| Module | ID | What it does |
|---|---|---|
| Mute | `mute` | Module backing `.mfmute`: client-side chat filter by player name and/or phrase. |
| Add Text | `add-text` | Local-only chat lines added with `.mfaddtext`, optionally kept across relogs. |
| Auto Chat Clear | `auto-chat-clear` | Automatically does the vanilla **F3+D chat clear** after every N received messages (default 20, 1–100). By default the visible backlog is emptied but scrollback history is preserved; an optional `clear-recent-history` toggle wipes that too. Own messages can be excluded from the count, and an optional chat line confirms each clear. Purely client-side. |
| Auto Responder | `auto-responder` | Replies to chat automatically via trigger=response pairs (random pick on multiple matches), with an anti-loop cooldown. |
| Chat Logger | `chat-logger` | Logs incoming and outgoing chat (including commands) to a file, new log per game join, path with %date%/%time%/%player%/%server% placeholders. |
| Death Commands | `death-commands` | Sends a random message/command from a list when you die, with delay range and chance settings. |
| Death Coords | `death-coords` | Prints (and optionally copies) your coordinates the moment you die. |
| Discord RPC | `discord-rpc` | Fully customizable Discord Rich Presence via Meteor's bundled discord-ipc — **presets** (Crystals, PvP, Mining, Exploring, Building, AFK) one-click fill everything, and **Custom** lets you edit each field yourself: **Line 1** and **Line 2** with placeholders `{player}` `{server}` `{ping}` `{pos}` `{dimension}` `{cps}` `{version}` `{health}`, large **image name & text**, optional small image (name & text), **show timer**, **update interval**, and a **crystals-per-second counter** (counts crystals blown up, appends " | <n> CPS" to line 1 and feeds `{cps}`). Application ID setting: use your own Discord app (discord.com/developers) to change the NAME shown by Discord and upload your own images; default points at Meteor's app. Runs in the main menu too. |
| Path | `path` | Draws a line from you to a target set with `.mfpath`. (Doesn't work yet.) |
| Tab Complete Privacy | `tab-complete-privacy` | Cancels tab-complete suggestion packets that would leak private commands: block-all, blocked prefixes, blocked words or symbols. |
| Tab Logger | `tab-logger` | Keeps a per-server history file of every tab-list player (uuid, name, ping history), written on a background thread. |
| Waypoint | `waypoint` | Stores waypoints added with `.mfwaypoint` and renders beacon-like beams to them. |
| World Origin | `world-origin` | Example module that highlights the center of the world. |

> Modules whose ID would collide with a Meteor built-in are prefixed with `mf-` (`mf-auto-respawn`, `mf-click-tp`) so Meteor's own copy keeps working.
---

## HUD Elements (7)

All registered in the **"Manaslu Flux"** HUD group (HUD editor → add element).

| Element | ID | What it does |
|---|---|---|
| Image | `image` | Displays an image file (png, jpg, bmp, first frame of a gif) from an absolute path or relative to `.minecraft`. Settings: file path, scale, opacity, smooth/nearest filtering. Shows a "Missing image" placeholder in the HUD editor. |
| GIF | `gif` | Displays an animated GIF file — decodes all frames, respects each frame's real delay, animates in sync with render delta, only re-uploads a frame when it changes. Same settings as Image. |
| Example | `example` | Template example: renders "ManasluFlux" text on a gray quad. |
| Player List | `player-list` | Dark box with all the other players on the server, sorted by name or ping. Settings: custom title (default "Other Players"), sort mode, reverse order, show-self, color-coded ping (green < 100ms, yellow < 250ms, red above), max rows (0 = everyone), background on/off. Drag and scale it in the HUD editor. |
| Server Info | `server-info` | Small info box for your session: server name and address ("Singleplayer"/"localhost" alone), online player count, and your live latency color-coded (green < 100ms, yellow < 250ms, red above). Settings: show address, show ping, background. |
| Session Info | `session-info` | World session box: in-game **day number**, **clock time** (HH:MM, same overworld-clock math as `.mfday`), **weather** (Clear/Rain/Thunder) and the **biome** you are standing in. Rows toggle individually, dark background option. |
| Kill Counter | `kill-counter` | Session combat tracker: **kills** (players and/or hostile mobs, friends excluded), **deaths** and computed **K/D**. Resets automatically when you join a different world or dimension. Settings: count players, count mobs, show deaths + K/D, background. |

---

## Commands (26)

All prefixed with `mf` (no slash needed inside Meteor's `.command` system: `.mfcoords` etc.).

| Command | What it does |
|---|---|
| `.mffakeplayer` | Spawns a **fake player the client treats as a real player** — own player-list entry (so tab list, skins and name lookups work) and registered in `world.getPlayers()` like a joining player, so auras, player counts and anything else that only reacts to real players targets it. Subcommands: `add [name]` (spawns where you look), `remove <name>`, `clear`, `list`, `tp` (move to your crosshair), `health <hp>`. `add` also copies your inventory so the damage simulation sees real armor. |
| `.mfhelp` | Built-in tutorial for Discord RPC: prints every `{}` placeholder with a live value, example Line 1 / Line 2 you can paste into the module, and where the Discord name / logo / images come from. |
| `.mfaddtext` | Local chat: `mfaddtext <text>` shows text only for you, nothing is sent to the server. |
| `.mfautoeat` | Configures the AutoEat module: `mfautoeat <food threshold>` \| `mfautoeat off`. |
| `.mfautofish` | Toggles the AutoFish module: `mfautofish on\|off\|status`. |
| `.mfautolog` | Configures the AutoLog module: `mfautolog <health%>` \| `mfautolog off`. |
| `.mfcoords` | Copies your coordinates to the clipboard and prints them. |
| `.mfday` | Displays the current in-game day and time of day. |
| `.mfdurability` | Shows durability of the item in your main hand. |
| `.mfeffects` | Lists your active potion effects. |
| `.mfenchant` | Toggles a fake enchant glow on the held item (visual only). |
| `.mffps` | Shows the current client FPS. |
| `.mfheal` | Shows how much food and health you are missing (client-side info only). |
| `.mfjavascript` | Asks a yes/no question in a vanilla confirmation dialog (`mfjavascript [question]`). **YES** deliberately crashes the game (with a proper crash report), **NO** rains multicolored confetti particles around you for a few seconds, then nothing. |
| `.mfkill` | Kills your own player: `mfkill` warns first, `mfkill confirm` dies. In singleplayer it kills the server-side player instantly (real `/kill` damage); on servers it sends `/suicide` (works wherever self-kill is allowed, e.g. Essentials). |
| `.mfmute` | Client-side chat filter: `mfmute add\|remove\|list\|clear [player]`, `mfmute phrase add\|remove\|list\|clear [phrase]`. |
| `.mfpath` | Points a beacon line to a target position: `mfpath <x> <y> <z>` \| `mfpath off`. |
| `.mfping` | Shows your current latency to the server. |
| `.mfrename` | Renames the item in your hand (client-side only). |
| `.mfserver` | Shows info about the server you are connected to. |
| `.mfskin` | Prints the current skin texture for the local player. |
| `.mfsm` | Situation module switcher: `.mfsm <module> [on\|off\|toggle]` — tab suggestions included. |
| `.mfstat` | Shows a few of your tracked stats (level, XP progress, play time, deaths). |
| `.mftrash` | Confirms dropping your entire inventory. Run `mftrash confirm` to actually drop. |
| `.mfuuid` | Shows your in-game UUID. |
| `.mfwaypoint` | Adds, lists or clears waypoints for the Waypoint module (tracers/render). |

---

## Module categories registered

Modules are grouped by what they do, so nothing shares a grab-bag category any more:

- **Manaslu Flux Combat** (8) — fighting: auras, autocrystal, totem, TNT, pearl phase, clicker (end crystal icon)
- **Manaslu Flux Movement** (5) — getting around: the three flights, auto walk, click TP (feather icon)
- **Manaslu Flux Player** (7) — keeping you alive and stocked: eat, fish, log, respawn, login, fast use, inventory keeper (golden apple icon)
- **Manaslu Flux World** (4) — changing blocks around you: placer, auto wither, map part, block replacer (grass block icon)
- **Manaslu Flux Client Side** (9) — what you see and what the server sees: crystal optimizer, block entities, night vision, sound muter, colored chat, toggle tab, anti drop lagback, ping spoofer, packet limiter (oak sign icon)
- **Manaslu Flux Utility** (13) — chat, logs and tools: waypoint, path, death coords, chat/tab loggers, tab privacy, RPC, and the rewritten chat modules (compass icon)
- **Manaslu Flux** HUD group — HUD elements

## Bundled Baritone

ManasluFlux ships with **Baritone built in** — the unmodified official Fabric build for Minecraft 1.21.11, bundled jar-in-jar (`META-INF/jars/`) and declared in `fabric.mod.json`. It loads automatically with the addon: no separate download needed.

- All of Baritone's `#` commands work in chat: `#goto x y z`, `#mine <block>`, `#follow player`, `#build`, `#farm`, etc.
- Meteor's own Baritone integration detects it automatically.
- Source: [cabaletta/baritone](https://github.com/cabaletta/baritone) — licensed **LGPL-3.0**, bundled unmodified with attribution.

## Chat/GUI features summary

- **Chat features**: local-only chat lines (Add Text), client-side muting by player or phrase (Mute + `.mfmute`), `&` color codes (Universal Colored Chat), death coordinates messages, module feedback messages.
- **GUI features**: all modules/elements appear in Meteor's ClickGUI and HUD editor under the categories above, each with their own settings groups; tab-completion suggestions for `.mfsm`.
- **Render features**: waypoint beams, path line, world origin box highlight.

## Build / versioning conventions

- `mod-version` in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) is bumped on **every** change — one version per build.
- Build with: `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2 ./gradlew build` (Loom 1.17 needs JDK 21+, system JAVA_HOME points to 17).
- Output: `build/libs/manasluflux-<version>.jar`.
- The website folder (`../manaslu/`) is a git repository tracking [`piolunson/ManasluFluxMeteorWebsite`](https://github.com/piolunson/ManasluFluxMeteorWebsite) — after every website sync it is committed and **pushed to `main`** (standing instruction from piolunson; credentials are stored in Windows Credential Manager, and the GitHub MCP server is available for repo API calls).
- This file (`FEATURES.md`) is updated in the same change whenever anything user-facing is added, changed or removed.

## Version history

| Version | Changes |
|---|---|
| 0.11.0 | **New module: Inventory Keeper** (`inventory-keeper`) — a save/restore layout keeper for your inventory. The module screen gets a **Save layout** button: it snapshots which item sits in each of the 36 playable slots (or just the nine hotbar slots with `only-hotbar`). Then every `refresh-rate` ms (default 200, 0 = every tick) it re-reads the inventory and swaps the saved items back into their slots; the item occupying a wrong slot is swapped out to where the wanted one came from, so nothing is dropped, consumed or moved out of the inventory. Items that no longer exist are skipped, checks only run with no screen open and an empty cursor, and at most 8 swaps happen per check so a scrambled inventory settles over a few refreshes instead of one burst. The layout persists in the Meteor config. Counts: 46 modules, Player 7. |
| 0.10.1 | **AC PacketFlood no longer waits for the game tick** — the place/break packets are now fired by a continuous 1 ms loop on the scheduler instead of once per tick, so the configured `place-delay-ms` / `break-delay-ms` really are packets-per-millisecond: `1` is roughly 1000 packets/s per action and `0` bursts until `max-packets-per-second` is reached. Plans (which base, which crystal, damage) are still rebuilt every tick on the game thread, only the sending runs in the loop. Batches are sent whole (a look packet without its attack would be useless), and the loop survives exceptions instead of dying. |
| 0.10.0 | **New command: `.mffakeplayer`** — a fake player the client genuinely believes is real, unlike Meteor's `.fake-player`. It gets its own `PlayerListEntry` (so tab list, nametags, skins and `getPlayerListEntry(uuid)` all resolve), it is added to `world.getPlayers()` exactly like a joining player, and it is a normal `OtherClientPlayerEntity` in the entity list — so auras, target finding, player counts and every "real players only" check pick it up. Subcommands: `add [name]` (spawns where you look, copies your inventory so armor counts in damage), `remove <name>`, `clear`, `list`, `tp`, `health <hp>`. Now 26 commands. |
| 0.9.0 | **New module: AC PacketFlood** (`ac-pcktflood`) — a crystal aura that lives on the packet timeline instead of the game tick. Every place and break is a raw packet (use-block / attack / silent look) fired by a millisecond scheduler, with per-action **delay in ms**, **retries** and **retry delay in ms** (`place-delay-ms` 1, `place-retries` 0, `place-retry-delay-ms` 2, and the same trio for break), `bases-per-tick` so several bases can be hit in one cycle spaced by the delay, all the usual aura settings (target/min/max-self damage, anti-suicide, silent rotate, face place with health/durability/missing armor, place & break ranges and walls ranges, support blocks) and a `max-packets-per-second` cap. Retries are dropped the moment the server's crystal spawn/destroy packet confirms the action, so `1ms:packet 3ms:retry 5ms:retry … 6ms:packet` is exactly what runs. **Modules re-categorised**: the old grab-bag "Manaslu Flux" and "Manaslu Flux Rewrite" categories are gone — modules now sit in Combat (8), Movement (5), Player (6), World (4), Client Side (9) and Utility (13). Counts: 45 modules. |
| 0.8.1 | **Every numeric setting now goes from 0 to 10000** — all 40 int settings and 29 double settings (69 total, across every module and HUD element) declare `range(0, 10000)` + `sliderRange(0, 10000)` instead of their old per-setting limits. A few internals are clamped so a dialled-up value can't freeze the game: Crystal Aura's place scan, Placer's fill scan and Instant TNT's through-walls scan cap their loop radius at 8 blocks (the reach/range checks still use your value), the Discord RPC update interval is guarded against 0, and Auto Fish's `look-up-pitch` is now a plain 0–10000 angle (60 = look 60° up) so it works without negative numbers. |
| 0.8.0 | **Packet level for Crystal Aura and Auto Totem** — new **Packet** settings group in both modules. `packet-level` sends the interactions as raw packets and reads the result from the server's packet stream instead of waiting for game ticks: Crystal Aura sends its own use-block/attack packets (no interaction-manager sequence or client prediction) and reacts to `EntitySpawnS2CPacket`/`EntitiesDestroyS2CPacket` of end crystals, so a crystal is popped the instant the server confirms it; Auto Totem sends the two inventory `ClickSlotC2SPacket`s of the offhand swap itself (both in the same millisecond, no inventory sync wait) and refills the moment `USE_TOTEM_OF_UNDYING` arrives. Both get **packet-retries** (`packet-retry-delay`) that resend until the server visibly reacts, and a **max-packets-per-second** cap so packet level can never flood the connection. Packet level is off by default — turn it on per module. |
| 0.7.2 | **Discord RPC: presets removed, built-in tutorial added** — the preset dropdown is gone (edit Line 1 / Line 2 yourself), and a new command **`.mfhelp`** prints a full placeholder tutorial in chat: every `{}` with live values (`{player}`, `{server}`, `{ping}`, `{pos}`, `{dimension}`, `{cps}`, `{version}`, `{health}`), example lines ready to paste, and where the Discord name/logo/images come from. Now 44 modules and 25 commands. |
| 0.7.1 | **Discord RPC defaults re-pointed to the Manaslu Flux application** (App ID `1563969889968988291`): the name + round logo shown by Discord now come from your own app — upload the [ManasluFlux logo](discord-assets/manasluflux.png) into its Art Assets tab under the key `manasluflux` for the big image too (new default `image-name`). |
| 0.7.0 | **New module: Discord RPC** (`discord-rpc`) — fully customizable Rich Presence with one-click **presets** (Crystals, PvP, Mining, Exploring, Building, AFK), editable **Line 1 / Line 2** with placeholders (`{player}` `{server}` `{ping}` `{pos}` `{dimension}` `{cps}` `{version}` `{health}`), large image name & text, optional small image, elapsed-time timer, update interval, and a built-in **crystals-per-second counter**. Custom **Application ID** setting lets you change the name shown by Discord and use your own Art-Assets images (default: Meteor's app). Counts 44 modules. |
| 0.6.0 | **Full port to Minecraft 1.21.11 + Meteor Client 1.21.11-SNAPSHOT**: every module, command, HUD element and both mixins were re-mapped to the Yarn 1.21.11 mappings (1.21.11+build.3) and Meteor's 1.21.11 API — rendering now uses the resolved Meteor `Renderer2D`/`HudRenderer` calls plus the new blaze3d device texture path behind the Image/GIF HUD elements (async file decoding, linear/nearest filtering), all client packets, enchantment/food data component reads, registry lookups, keybinding and interaction-manager calls were converted to the 1.21.11 names, and the bundled Baritone jar was swapped to the 1.21.11 Fabric build. Settings builder clamp fixes and the HUD texture wrapper were rewritten against the new API. No features were added or removed — beyond 1.21.11 compatibility this is a hardening release: async HUD image loading and per-frame GIF re-upload checks. |
| 0.5.3 | New **Anti Drop Lagback** module (Client Side, Hazey-style): watches the server's position-sync teleports and count "rubberbands" (corrections that snap you back ≥ `min-distance`, default 1.5 blocks) inside a sliding window (`window-seconds`, default 5 s). Passing the cap (`rubberbands`, default 3) **pauses every other active module** for `pause-ticks` (default 40) so whatever causes the rubberbands stops, then switches everything back on. `chat-notifier` (default on) announces each rubberband and each pause/resume in local chat — purely client-side, packets are observed but never modified or cancelled. |
| 0.5.2 | **Crash fix** in the Client Block Entities mixin: the `tryExtractRenderState` injection's parameter was declared as `Object` where vanilla expects `ModelFeatureRenderer$CrumblingOverlay` (and the return as a raw `Object` instead of `BlockEntityRenderState`), so the mixin failed to apply at startup and crashed the game on launch (0.5.0/0.5.1). Now uses the exact descriptor — the compiled handler signature byte-for-byte matches what the mixin injector expects. |
| 0.5.1 | New **Auto Chat Clear** module (Rewrite): automatically does the vanilla **F3+D chat clear** after every N received messages (default 20, configurable 1–100). By default it behaves exactly like F3+D — the visible backlog is emptied but scrollback history is preserved — and an optional `clear-recent-history` toggle wipes that too. Own messages can be excluded from the count, and an optional chat line confirms each clear. Purely client-side, nothing is sent to the server. |
| 0.5.0 | New **Client Block Entities** module (Client Side): pick any block entity types by id (chests, signs, beacons, spawners, shulker boxes...) and their block-entity rendering is done client-side only — **Simple** replaces the block-entity part with a single static box, **Hide** removes it entirely (the block itself still renders normally). Like Crystal Optimizer but for any block entity. Purely client-side (mixin into `BlockEntityRenderDispatcher`), big FPS boost near many heavy block entities. |
| 0.4.10 | **Crash fix** on Minecraft 26.2 + Meteor 26.2-31: opening the Meteor GUI (Modules screen) in the main menu crashed with `NullPointerException: Components not bound yet`. The category icon lambdas now build their ItemStacks the same way Meteor's own categories do (`DisplayItemUtils.toStack`, a direct holder with pre-bound components), which is safe before the vanilla item registry binds. | 
| 0.4.9 | Two new top-bar tabs in the Meteor ClickGUI, next to Modules/Config/GUI/HUD: **Notepad** (multi-page free-text notes, pages added/removed with +/−, auto-saved) and **To Do** (checkbox task list — type a task, press +, tick to complete (done items turn green and sink to the bottom), − removes; footer shows "n/m done"). Both persist across restarts in an NBT file (`manasluflux-gui-notes.nbt`) inside the Meteor folder, and both work anywhere: main menu, in-game, inside other Meteor screens. |
| 0.4.8 | Three new HUD elements in the "Manaslu Flux" HUD group, all not covered by Meteor's built-ins: **Server Info** (`server-info` — server name/address, online players, color-coded live ping), **Session Info** (`session-info` — in-game day, HH:MM clock, weather, current biome) and **Kill Counter** (`kill-counter` — session kills/deaths/K-D with mob/player selection, friend-safe, auto-reset on world change). HUD count goes 4 → 7. | Auto Wither: new **range** setting (default 4.5, up to 20) — the aimed build spot must be within this distance from you, otherwise no preview appears and right-click does nothing. |
| 0.4.6 | Auto Wither **reworked as a straight port from Trouser-Streak**: aim anywhere and right-click to build (2 soul sand columns + 3 skulls, center skull optional), air place, rotation lock, live ghost preview with colors, silent hotbar swaps for placement, delay between blocks (0 = one tick), optional automatic nametag of the spawned wither (creative renames the tag client-side). |
| 0.4.5 | New **Auto Wither** module (Manaslu Flux), **ported from Trouser-Streak** (etianl, originally by Germanminer): aim at a spot, see the live ghost preview of the wither skeleton, right-click and the full wither is placed — 2 soul sand columns plus the middle block with 3 skulls on top (center skull optional), placement goes through silent hotbar swaps so your visible slot is untouched. **Air place** works, rotation can be **locked** to a cardinal direction, and delay between blocks is configurable (0 = everything in one tick). Optionally **nametags** the spawned wither automatically: creative mode renames the tag client-side to your configured name, survival uses an already anvil-renamed tag; nametag delay configurable (0 = instant). Replaces the previous auto-wither implementation with the proven Trouser-Streak one. |
| 0.4.4 | Auto Map Part: new **delay** setting (0–20 ticks, default **0 = instant**) — the map is placed into the frame the same tick it spawns; frames detected instantly are claimed before anything else can fill them, delayed frames count down per-tick and are dropped if they despawn or get filled meanwhile. Silent hotbar swap unchanged. |
| 0.4.3 | New **Auto Map Part** module (Manaslu Flux): when you place an item frame, it automatically fills it with a randomly chosen filled map from your hotbar. **Silent** (default on): the map is taken with a server-side carried-item swap so your visible hotbar slot never changes. Freshly-spawned empty frames only (≤2 ticks old), already-filled frames are never touched again, removed-frame memory is cleaned up each tick, and the filled-frame count shows in the HUD info string. |
| 0.4.2 | New **Crystal Optimizer** module (Client Side): replaces every end crystal's full animated model (outer cube, inner core, bedrock base, beam) with a single static box — or hides crystals entirely — for a big FPS boost with lots of crystals on screen. Purely client-side rendering, implemented with the addon's first mixin (`EndCrystalRendererMixin` cancels the vanilla crystal render, a `Render3DEvent` draws the box): nothing is sent to the server, crystals behave exactly the same. |
| 0.4.1 | Instant TNT gained a **bow fallback** (`bow-fallback`, default on): with no flint & steel, fire charge AND no redstone block in the inventory, it silently swaps to a bow, draws it server-side and releases after 10 ticks — a **Flame**-enchanted bow (or any bow in creative mode) shoots flaming arrows that ignite TNT on contact. The shot only fires when the TNT is actually in your crosshair (arrows fly where you look, so click-through-walls aiming can't guide them), needs an arrow in the inventory, and has a ~1s shot cooldown. |
| 0.4.0 | Instant TNT's redstone-block ignition now **mines the redstone block straight back** (new `mine-redstone-block` setting, default on): right after igniting, the module silently swaps to a hotbar pickaxe server-side and packet-mines the placed redstone block (START/STOP destroy packets timed to vanilla break speed with periodic swing packets), so the dust is back in your inventory before the TNT blows — no redstone block consumed per ignition. |
| 0.3.16 | New **Kill** command (`.mfkill`) with a confirm step: in singleplayer it kills you instantly with real `/kill` damage (through the integrated server); on servers it sends `/suicide`, which works wherever self-kill is allowed. **Instant TNT** gained a **redstone-block fallback** (default on): with no flint & steel or fire charge in the inventory, it silently places a redstone block next to the TNT to prime it. Also added a compile-only Fabric API resource-loader dependency so addon code can safely touch the integrated server on MC 26.2. |
| 0.3.15 | New **Ping Spoofer** module (Manaslu Flux) with three modes: **Real** (nothing changed), **More** (your keep-alive/pong replies are actually delayed, so the server-side latency genuinely grows by the configured amount, with random jitter) and **Spoof** (replies stay instant, the tab-list ping is patched to the fake value). Includes a debug readout of real vs displayed ping. |
| 0.3.14 | **Baritone is now built in**: the official Baritone v1.19.0 Fabric build for Minecraft 26.2 is bundled jar-in-jar and loads automatically — every `#` command (`#goto`, `#mine`, `#follow`, …) and Meteor's Baritone integration work with no separate download. Addon code unchanged. |
| 0.3.13 | New **Placer** module (Manaslu Flux): places whitelisted blocks everywhere around you — fills nearby air with the selected blocks from your inventory, nearest first. Radius, blocks-per-tick, silent rotation and entity checks are configurable. |
| 0.3.12 | New **Player List** HUD element: a box with every other player on the server, sorted by name or ping — color-coded ping (green < 100ms, yellow < 250ms, red above), reverse sort, custom title, show-self toggle and a max-rows cap. Drag/scale it in the HUD editor like any element. |
| 0.3.11 | All four ManasluFlux categories now have **item icons in Meteor's GUI**: nether star for Manaslu Flux, oak sign for Manaslu Flux Client Side, end crystal for Manaslu Flux Combat and enchanted book for Manaslu Flux Rewrite — using Meteor 26.2's `Category(name, icon)` constructor. |
| 0.3.10 | Elytra Flight gained a toggleable **auto-forward** option (default OFF). With it on, the module constantly thrusts forward in your look direction as before; with it off, you only thrust while holding the forward key (W), so you can glide and steer yourself — jump = up and sneak = down still work either way. |
| 0.3.9 | Universal Flight gained the **always-fly** option (default ON = previous behavior). With it OFF the module only grants the flight permission + speed and lets vanilla's double-jump toggle flying on and off, exactly like creative mode. |
| 0.3.8 | **Auto Login** module added to Manaslu Flux Client Side: automatically sends `/login <password>` (both configurable, default `/login 12345678`) after joining, or on chat triggers like "register" — with delay, cooldown and repeat options. |
| 0.3.7 | **New module category "Manaslu Flux Rewrite"** for the 10 rewritten Meteor-style modules (Auto Respawn, Auto Responder, Death Commands, Chat Logger, Tab Logger, Tab Complete Privacy, Click TP, Clicker, Sound Muter, Packet Limiter). **Crash fix**: `sound-blocker` collided with Meteor's built-in SoundBlocker module ID, which NPE'd Meteor's SoundEngine mixin at startup — renamed to `sound-muter` (`click-tp` and `auto-respawn` also collided and are now `mf-click-tp` / `mf-auto-respawn`). |
| 0.3.6 | **10 modules ported from RyanWare (by SmilerRyan)** into the Manaslu Flux category: Auto Respawn, Auto Responder, Death Commands, Chat Logger, Tab Logger, Tab Complete Privacy, Click TP, Clicker, Sound Blocker and Packet Limiter — all rewritten for MC 26.2 mappings and the addon's conventions. |
| 0.3.5 | Website: background image switched from `assets/bg.png` (4.9 MB) to `assets/bg.webp` (89 KB, 55× smaller) for much faster page loads; site is now also auto-pushed to the GitHub website repo on every build. Addon code unchanged. |
| 0.3.4 | Crystal Aura completely reworked as a port of Meteor Client's CrystalAura: real explosion damage simulation (`min-damage` / `max-self-damage` / `anti-suicide`), face place (low health, broken armor, missing armor), optional obsidian support blocks in air, separate place/break ranges, walls ranges, delays and an attack frequency limit, silent server-side rotation before placing/breaking. Old crude settings (single range/delay, self-damage-guard, min-self-distance) replaced. |
| 0.3.3 | Auto-pause added to Crystal Aura and Silent Aura: new **Pause** settings group with `pause-on-mine` (stops the aura while you are mining a block) and `pause-on-eat` (stops it while you are eating or drinking) — both on by default. |
| 0.3.2 | Website (`manaslu/`) synced to current state: v0.3.2 chip and download jar, `.mfjavascript` in the command list, updated Pearl Phase description, changelog entries for 0.3.0–0.3.2. Website is now updated on every build. |
| 0.3.0 | Pearl Phase completely reworked to actually function: tick-based rotation-then-throw with server-visible rotation, clean hotbar swap (no raw carried-item packets — fixes eaten blocks and hotbar desync), rotation restore, clearer errors when no pearl is in the hotbar. CC bypass removed (was broken). |
| 0.2.9 | Pearl Phase rewritten as a port of BlackOut's Auto Pearl (own-block clip, CC bypass, switch modes, instant rotation); Silent Aura gained silent aim (server-side look rotation, body/head toggle); Instant TNT gained click-through-walls ignition. |
| 0.2.8 | Pearl Phase module added (initial implementation, later replaced in 0.2.9). |
| 0.2.7 | Version bump of the Image/GIF/Auto Walk build. |
| 0.2.6 | Image + GIF HUD elements and Auto Walk Hold module added. |
| ≤ 0.2.5 | Base addon: modules, commands and HUD listed above (see git history for details). |
