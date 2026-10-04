# ManasluFlux — Feature List

A Meteor Client addon for Minecraft 26.2 (Fabric) by piolunson.

> **This document is the single source of truth for everything the addon adds.**
> It is updated every time the addon is rebuilt. Last updated: **v0.4.1**.

> Also updated on every build: the website in [`../manaslu/`](../manaslu/index.html) — hero version chip, download button/jar link (fresh jar copied into `manaslu/downloads/`), command list and changelog.

Current version: see [`gradle/libs.versions.toml`](gradle/libs.versions.toml) (`mod-version`).
Build output: `build/libs/manasluflux-<version>.jar`.

---

## Modules (37)

### Category: Manaslu Flux (main)

| Module | ID | What it does |
|---|---|---|
| Auto Eat | `auto-eat` | Automatically eats food from your hotbar when hungry. |
| Auto Fish | `auto-fish` | Automatically throws the rod, reels in fish, looks up and recasts. |
| Auto Log | `auto-log` | Automatically disconnects when your health drops below a threshold. |
| Auto Walk Hold | `auto-walk-hold` | Holds a configurable key (default **2**) down while enabled — keeps it pressed even after screens open or window focus changes. |
| Block Replacer | `block-replacer` | Places back the same block when one of your placed blocks gets mined or removed. |
| Boat Flight | `boat-flight` | Fly while riding a boat — horizontal and vertical speed settings, jump = up, sneak = down. |
| Death Coords | `death-coords` | Prints (and optionally copies) your coordinates the moment you die. |
| Elytra Flight | `elytra-flight` | Controlled elytra flight without fireworks — horizontal and vertical speed settings. **Auto-forward** toggle (default off): on = constant thrust in your look direction, off = you thrust only while holding the forward key. Jump = up, sneak = down work anytime. |
| Instant TNT | `instant-tnt` | Automatically ignites TNT just by looking at it — silent server-side switch to flint & steel/fire charge. **Click Through Walls**: scans in reach and ignites TNT behind walls and around corners with a synthesized use packet. **Redstone Block** (default on): when no igniter is in the inventory, silently places a redstone block next to the TNT instead — redstone power primes TNT. **Mine Redstone Block** (default on): the placed redstone block is packet-mined again during the TNT's fuse with a silent hotbar-pickaxe swap, so the dust lands back in your inventory. **Bow Fallback** (default on): last resort when there is no igniter and no redstone block either — silently swaps to a bow, draws and releases after 10 ticks, and shoots the TNT; only fires when the TNT is in your crosshair (arrows fly where you look) and only with a **Flame** bow (or any bow in creative) plus an arrow, since flaming arrows ignite TNT on contact. |
| Mute | `mute` | Hides chat messages from muted players or containing muted phrases (client-side). |
| Path | `path` | Draws a line from you to a target set with `.mfpath`. (Doesn't work yet.) |
| Pearl Phase | `pearl-phase` | Ported from BlackOut's Auto Pearl — one-shot pearl throw at your own block (yaw +180°, steep pitch) to clip inside walls. Tick-based: rotates, throws on the next tick so the server sees the rotation, restores your view and hotbar, auto-toggles off. Settings: pitch, switch mode (Normal/Silent hotbar swap), instant rotation (direct rotation packet), keep rotation. |
| Placer | `placer` | Places whitelisted blocks everywhere around you — fills nearby air with the selected blocks straight from your inventory (nearest first). Settings: block whitelist, radius (1–6), blocks-per-tick, silent rotation, check-entities. |
| Ping Spoofer | `ping-spoofer` | Changes your ping with three modes: **Real** (no change), **More** (genuinely adds latency by delaying keep-alive/pong replies, so the server sees real extra ms) or **Spoof** (replies sent instantly, only the tab-list ping is patched). Amount, jitter and a debug readout are configurable. |
| Universal Flight | `universal-flight` | Enables flight — vanilla creative flight with an adjustable speed. **always-fly** ON forces flight permanently; OFF only grants the flight permission so you can toggle flying with a double-jump exactly like creative mode. |
| Waypoint | `waypoint` | Stores waypoints added with `.mfwaypoint` and renders beacon-like beams to them. |
| Whitelist Fast Use | `whitelist-fast-use` | Toggles Meteor's FastUse automatically based on the item you are holding. |
| World Origin | `world-origin` | Example module that highlights the center of the world. |

### Category: Manaslu Flux Client Side

| Module | ID | What it does |
|---|---|---|
| Add Text | `add-text` | Local-only chat lines added with `.mfaddtext`, optionally kept across relogs. |
| Auto Login | `auto-login` | Sends `/<command> <password>` (default `/login 12345678`) automatically after joining a server — or when chat shows a trigger word like "register". Settings: command, password, mode (Always/Trigger), triggers, delay, repeat-on-trigger, cooldown. The password is stored in plain text in the Meteor config — don't reuse an important one. |
| Client-Side Night Vision | `client-side-night-vision` | Night vision potion effect client-side only — the server never sees the effect. |
| Toggle Tab | `toggle-tab` | Keeps the player list (tab) open by making the game think you are holding Tab. |
| Universal Colored Chat | `universal-colored-chat` | Replaces every `&` in chat with the color code sign. |

### Category: Manaslu Flux Combat

| Module | ID | What it does |
|---|---|---|
| Auto Totem | `auto-totem` | Instantly refills a totem of undying into your offhand whenever it is used or pops. |
| Crystal Aura | `crystal-aura` | **Port of Meteor Client's CrystalAura** — simulates the crystal explosion for every obsidian/bedrock base in range and places/pops only the most damaging one. Settings: target-range, predict-movement, min-damage, max-self-damage, anti-suicide, silent rotate; **Place** (place, place-delay, place-range, place-walls-range, support-blocks + support-delay); **Face Place** (face-place, health, durability, missing-armor); **Break** (break, break-delay, break-range, break-walls-range, attack-frequency); **Pause** (pause-on-mine, pause-on-eat). All silent: server-side hotbar swaps and look packets — no visible rotation or hotbar movement. |
| Silent Aura | `silent-aura` | Attacks the nearest player without rotating or swinging your visible hand — **silent aim** (server-side-only look rotation at the target, body or head) plus a silent weapon switch. **Pause** group: `pause-on-mine` and `pause-on-eat` temporarily stop the aura while you mine or eat/drink. |
| TNT Placer | `tnt-placer` | Traps the nearest player in (crying) obsidian, buries them in TNT and ignites it — non-stop. |

### Category: Manaslu Flux Rewrite

Meteor built-ins rewritten for ManasluFlux (MC 26.2, own ClickGUI category). Note: module IDs that collide with Meteor's built-ins are prefixed/renamed (`mf-auto-respawn`, `mf-click-tp`, `sound-muter`) — an ID collision makes Meteor's internal mixin crash the game.

| Module | ID | What it does |
|---|---|---|
| Auto Respawn | `mf-auto-respawn` | Auto-clicks respawn on the death screen, then optionally runs commands/chat lines (one per tick) after respawning. |
| Auto Responder | `auto-responder` | Replies to chat automatically via trigger=response pairs (random pick on multiple matches), with an anti-loop cooldown. |
| Chat Logger | `chat-logger` | Logs incoming and outgoing chat (including commands) to a file, new log per game join, path with %date%/%time%/%player%/%server% placeholders. |
| Click TP | `mf-click-tp` | Hold use while looking at a block to teleport there in configurable steps, with max distance, step size, delay and an optional safe-landing check. |
| Clicker | `clicker` | Left/right auto clicker with Nothing/Hold/Click modes and per-action tick delays. |
| Death Commands | `death-commands` | Sends a random message/command from a list when you die, with delay range and chance settings. |
| Packet Limiter | `packet-limiter` | Caps outgoing packets per tick (keep-alive/pong always allowed) so laggy modules can't get you kicked for flooding, with a debug counter. |
| Sound Muter | `sound-muter` | Mutes specific sounds picked from a sound list, client-side. |
| Tab Complete Privacy | `tab-complete-privacy` | Cancels tab-complete suggestion packets that would leak private commands: block-all, blocked prefixes, blocked words or symbols. |
| Tab Logger | `tab-logger` | Keeps a per-server history file of every tab-list player (uuid, name, ping history), written on a background thread. |

---

## HUD Elements (4)

All registered in the **"Manaslu Flux"** HUD group (HUD editor → add element).

| Element | ID | What it does |
|---|---|---|
| Image | `image` | Displays an image file (png, jpg, bmp, first frame of a gif) from an absolute path or relative to `.minecraft`. Settings: file path, scale, opacity, smooth/nearest filtering. Shows a "Missing image" placeholder in the HUD editor. |
| GIF | `gif` | Displays an animated GIF file — decodes all frames, respects each frame's real delay, animates in sync with render delta, only re-uploads a frame when it changes. Same settings as Image. |
| Example | `example` | Template example: renders "ManasluFlux" text on a gray quad. |
| Player List | `player-list` | Dark box with all the other players on the server, sorted by name or ping. Settings: custom title (default "Other Players"), sort mode, reverse order, show-self, color-coded ping (green < 100ms, yellow < 250ms, red above), max rows (0 = everyone), background on/off. Drag and scale it in the HUD editor. |

---

## Commands (24)

All prefixed with `mf` (no slash needed inside Meteor's `.command` system: `.mfcoords` etc.).

| Command | What it does |
|---|---|
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

- **Manaslu Flux** — general/utility modules (main category, nether star icon)
- **Manaslu Flux Client Side** — purely client-side/visual modules (oak sign icon)
- **Manaslu Flux Combat** — combat modules (end crystal icon)
- **Manaslu Flux Rewrite** — Meteor built-ins rewritten for ManasluFlux (enchanted book icon)
- **Manaslu Flux** HUD group — HUD elements

## Bundled Baritone

ManasluFlux ships with **Baritone v1.19.0** built in — the unmodified official Fabric build for Minecraft 26.2, bundled jar-in-jar (`META-INF/jars/`) and declared in `fabric.mod.json`. It loads automatically with the addon: no separate download needed.

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
