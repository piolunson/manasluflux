# ManasluFlux

A [Meteor Client](https://meteorclient.com) addon for **Minecraft 1.21.11 (Fabric)** by **piolunson**.

🌐 **Website & downloads:** [github.com/piolunson/ManasluFluxMeteorWebsite](https://github.com/piolunson/ManasluFluxMeteorWebsite)

## About

ManasluFlux adds **69 modules**, **30 commands** and **7 HUD elements** on top of Meteor Client, organized in seven ClickGUI categories:

- **Manaslu Flux Combat** (10) — crystal aura, silent aura, trigger bot, bed aura, surround, kill say, auto totem, instant TNT, clicker, TNT placer
- **Manaslu Flux Movement** (6) — universal/elytra/boat flight, elytra bounce, firework elytra, auto walk
- **Manaslu Flux Player** (9) — eating, fishing, login/logout, respawn, inventory keeper, xcarry, fast use
- **Manaslu Flux World** (5) — placer, block replacer, wither builder, map art, speed mine
- **Manaslu Flux Client Side** (14) — purely client-side/visual modules, including an FOV modifier and a custom ESP
- **Manaslu Flux Utility** (15) — chat, logs and tools
- **Manaslu Flux Bypasses** (10) — modules that beat a server/anticheat check instead of changing what you do, mostly named **Universal** because they do not target one anticheat: cobweb bypass, click TP, pearl phase, jump pearl, VClip PhaseFill, flight, wool replace, silent timer switch

Highlights:

- **Auto Crystal** — full port of Homovore's AutoCrystal: base place (place the obsidian under the target yourself), anti-chinese (only crystal or break next to a burrow block), damage sync, 1.75 s crystal tracking, instant break off the spawn packet, and anti-surround that discounts blocks Speed Mine is already breaking. Damage is the vanilla explosion model with Homovore's exposure rules; placement runs through the ported rate-limited `PlacementManager`, and `look` turns the aura to the exact spot before every place and break (`silent` keeps that turn server-side, so your camera does not move).
- **Silent Timer Switch** — switches your held slot to the item you pick on a timer and switches it back: `item`, `every` (how often, ticks) and `hold` (how long, ticks). In `Silent` mode the switch is one bare selected-slot packet, so the server believes you hold the item while your client keeps the slot it was on — your hotbar never moves; `Visible` moves your real slot for the hold time.
- **Kill Say** — port of Sydney's KillSay: a message when you kill someone (random line, brand-swapped), optional `2 pops Bob!` announcements, `delay` so a streak does not spam chat, `clientside`, `green-text`, `kill-streak`, `suicide-ignore` and your own message lists in `Custom` mode.
- **FOV Modifier** — port of Sydney's FOVModifier: force the FOV you want (default 120) instead of the vanilla slider, with Sydney's 70° guard so held items keep their fixed FOV unless you opt in.
- **Surround** — port of Sydney's Surround: wraps your feet (the block you stand in, its four sides, optional floor and entity extension) with the hardest block in your hotbar, `limit` per group with `delay` between groups, strict-direction clicking, support-then-retry placement, crystal destruction, and a `Sequential` timing that rebuilds a spot the moment a crystal spawns into it.
- **Trigger Bot** — the aura only while you aim: `entities` picks the entity types (players by default), `aura` is a module list (Silent Aura by default, or Meteor's Kill Aura), and `range` sets how far its own crosshair ray reaches. Looking away switches off only what it switched on, an aura you enabled yourself is left alone, and switching the trigger off releases everything it started.
- **Silent Aura** — attacks the nearest player with server-side-only aim (your camera never moves) and a silent weapon switch.- **Instant TNT** — ignites TNT by looking at it, including behind walls (click-through-walls); falls back to placing a redstone block when you have no igniter (and mines that block straight back during the fuse), and last of all shoots the TNT with a Flame bow.
- **Bed Aura** — one module for both explosion blocks: beds in the Nether and End, charged respawn anchors in the Overworld and End, picked automatically per dimension with a fallback to whichever item you actually carry, silent switching, glowstone charging and anti-suicide gating.
- **Elytra Bounce** — Meteor Client's `ElytraFly` Bounce mode copied unchanged (start-gliding packet from the jump key, `yaw-lock` None / Smart / Simple, `pitch-lock` at 85°, `auto-jump`, `sprint-constantly`, rubberband `restart`), plus one addition: when a block stops the bounce, Baritone is sent a few blocks forward (`baritone-nudge`, `nudge-blocks`, `blocked-ticks`) and the bounce resumes.
- **Pearl Phase** — full port of BlackOut's Auto Pearl: pearl at your own block to phase into walls, with their CC bypass, four switch modes and instant rotation.
- **VClip PhaseFill** — vclip a few blocks, seal the space you left behind and let the server rubberband you; if it doesn't, you are vclipped back.
- **Cobweb Bypass** — no-slow in webs: the cobweb movement multiplier is replaced client-side, with per-anticheat profiles (vanilla, NCP, Themis, Grim), a per-axis speed cap, a lagback watcher with a `lagback-limit` rule that restarts the web clock and/or drops sprint once the server has pulled you back more than that many times in the same web, sprint tweaks that release sprint before a web and put it back on after a set time inside one, and a web-only Timer with a constant mode or a graph mode that follows your own formula of the time spent in the web (default: fast at first, then a gentle plateau), the web clock restarts when you walk into a different web — even a touching one — and an optional `verbose` mode narrates what it enabled and what it is doing.
- **No Force Rotation** — rewrites the server's rotation packet before the client applies it, so the acknowledgement carries your angle instead of the forced one; per-axis toggles and a teleport escape.
- **XCarry** — four extra storage slots in your 2x2 crafting grid: the inventory close packet for your own player handler is held back while the grid holds anything, so the server keeps those slots instead of returning them, and pressing E again shows them with the items still in them. Chests and other containers close normally, an empty grid closes like vanilla, and switching the module off sends the real close so the items come back to your inventory.
- **Self Mine Out** — when a phase leaves you inside blocks, mines the block in your head and/or your feet with Speed Mine; it holds the best tool for the block itself (`tool-switch`: silent by default, so the server sees the pickaxe while your hotbar stays put), plus separate toggles, collision only, no guessing.
- **Totem Pop Pearl Phase** — after your own totem pops it throws one pearl: aimed around a frame/painting if one is inside you, plain own-block aim if not. Delay, stable-cam and chat feedback; repeats on every pop.
- **Custom ESP** — pick the entities like normal ESP (players by default, any entity type you want) and each one is outlined as its **own model** (`style: Model`, the entity's real posed quads, so heads, arms and wings are shaped right) rather than a box around it: fill whose alpha comes from the side colour, and a glow halo of layered shells that grow and weaken outward. Fades in when an entity appears and out when it dies or leaves; `style: Box` gives the hitbox version with real edge thickness.
- **Block Highlight** — port of Homovore's Block Highlight: the box on the block you look at morphs between blocks over 60 ms and fades in and out, with a filled side colour whose alpha is the side alpha, real edge thickness (`line-thickness`, drawn as thin boxes since Meteor's lines are one pixel) and a layered glow halo.
- **Wool Replace** — an obsidian placement is sent as a wool placement with no wool and no hotbar swap: the server's view of your hand is spoofed to wool for that one interaction and put back immediately after, while your client keeps predicting obsidian (glitch block). No checks, no settings.
- **Packet Filter** — a packets-per-second budget that drops the least useful packet first: crystal places, totem swaps and swings are never dropped, movement only goes once the budget is stretched, and chat/sprint noise goes first. `.mfpacketlimit test` measures the server's own limit by flooding it until it kicks you, `.mfpacketlimit check` reports the numbers.
- **Update detector** — every 30 seconds it asks the site whether a newer ManasluFlux exists. When one does, a window opens with the installed and latest version and a single **Update & restart** button (also `.mfupdate`): it downloads the new jar, refuses anything whose `fabric.mod.json` is not this addon at that version, disables the old jar as `.disabled`, swaps the new one in and restarts the game with the same command line. If you never press it, the update is forced 10 minutes after the version was first seen, with the countdown in the window.
- **British Chat** — tidies what you send: a capital at the start, a full stop at the end, and optional grammar fixes for the words everyone mistypes in a fight.
- **Auto Login** — sends `/login <password>` automatically after joining.
- **Image / GIF HUD** — display images and animated GIFs directly on your HUD.
- **Server Info / Session Info / Kill Counter HUD** — server address + live ping, in-game day/time/weather/biome, and a session combat tracker.

The full, always up-to-date feature list lives in [FEATURES.md](FEATURES.md).

## Modules

<details>
<summary>Full module list (68)</summary>

**Manaslu Flux Combat (10):** `auto-crystal`, `kill-say`, `mf-auto-totem`, `clicker`, `instant-tnt`, `mf-bed-aura`, `mf-surround`, `mf-trigger-bot`, `silent-aura`, `tnt-placer`

**Manaslu Flux Movement (6):** `auto-walk-hold`, `boat-flight`, `elytra-flight`, `firework-elytra-fly`, `mf-elytra-bounce`, `place-travel`

**Manaslu Flux Player (9):** `mf-auto-eat`, `mf-auto-fish`, `mf-auto-log`, `auto-login`, `mf-auto-respawn`, `inventory-keeper`, `xcarry`, `no-force-rotation`, `whitelist-fast-use`

**Manaslu Flux World (5):** `auto-map-part`, `auto-wither`, `block-replacer`, `placer`, `mf-speed-mine`

**Manaslu Flux Client Side (14):** `anti-drop-lagback`, `block-highlight`, `break-reminder`, `client-block-entities`, `client-side-night-vision`, `custom-esp`, `fov-modifier`, `packet-filter`, `packet-limiter`, `ping-spoofer`, `sound-muter`, `toggle-tab`, `universal-colored-chat`, `VOM`

**Manaslu Flux Utility (15):** `auto-chat-clear`, `auto-responder`, `british-chat`, `chat-logger`, `death-commands`, `death-coords`, `discord-rpc`, `world-origin`, `mute`, `packet-stealer`, `path`, `screenshot`, `tab-complete-privacy`, `tab-logger`, `waypoint`

**Manaslu Flux Bypasses (10):** `totempoppearlphase`, `mf-silent-switch`, `universal-self-mine-out`, `universal-jump-pearl`, `universal-wool-replace`, `universal-click-tp`, `universal-cobweb-bypass`, `universal-flight`, `universal-pearl-phase`, `universal-vclip-phasefill`

</details>

## Building

Requirements: **JDK 21+** (built with JDK 25) and internet access for Gradle dependencies.

```bash
./gradlew build
```

The jar appears in `build/libs/manasluflux-<version>.jar`.

## Bundled Baritone

ManasluFlux ships with **Baritone** built in — the unmodified official Fabric build (`baritone-meteor 1.21.11-SNAPSHOT`) for Minecraft 1.21.11, bundled jar-in-jar under `META-INF/jars/`. It loads automatically with the addon: no separate download needed. All of Baritone's `#` commands work in chat (`#goto x y z`, `#mine diamond_ore`, `#follow player`, …) and Meteor's Baritone integration detects it.

Source: [cabaletta/baritone](https://github.com/cabaletta/baritone) — licensed under **LGPL-3.0**, bundled unmodified with attribution.

> On Windows with an older system JDK, point Gradle at a newer one:
> `JAVA_HOME=<path-to-jdk-21+> ./gradlew build`

## Vendored BlackOut sources

[`blackout/`](blackout/README.md) holds unmodified copies of the BlackOut files this addon ports — upstream [KassuK1/BlackOut](https://github.com/KassuK1/BlackOut) at commit `4c18af3f`, **GPL-3.0** (same as this project), with the original `LICENSE` alongside. Currently `AutoPearl.java` (→ `universal-pearl-phase`) and `AutoCrystalPlus.java` (the aura this port replaced, kept for reference). It is reference material only, not compiled.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Drop [Meteor Client](https://meteorclient.com) and `manasluflux-<version>.jar` into your `mods` folder.
3. Launch the game — the new categories appear in Meteor's ClickGUI (Right Shift).

## Version history

See [FEATURES.md](FEATURES.md) for the complete changelog — the addon version is bumped on every change, and the website is rebuilt and published with every release.

## License

This project is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE), consistent with Meteor Client's licensing.

Some modules are rewritten takes on ideas from other open-source clients (Meteor Client, BlackOut, RyanWare). Where a module is a port rather than a rewrite, the upstream file is vendored unmodified in [`blackout/`](blackout/README.md) (currently BlackOut's `AutoPearl.java` and `AutoCrystalPlus.java`, GPL-3.0, for `universal-pearl-phase` and `auto-crystal`, plus Homovore's `SpeedMineModule.java`, AGPL-3.0, for `mf-speed-mine`), and the port's javadoc names the original author. The bundled Baritone jar is the unmodified official release and remains under its LGPL-3.0 license.

## Disclaimer

Use of this addon on servers you don't own may violate their rules. Use responsibly and at your own risk.
