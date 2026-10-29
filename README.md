# ManasluFlux

A [Meteor Client](https://meteorclient.com) addon for **Minecraft 1.21.11 (Fabric)** by **piolunson**.

🌐 **Website & downloads:** [github.com/piolunson/ManasluFluxMeteorWebsite](https://github.com/piolunson/ManasluFluxMeteorWebsite)

## About

ManasluFlux adds **46 modules**, **27 commands** and **7 HUD elements** on top of Meteor Client, organized in seven ClickGUI categories:

- **Manaslu Flux Combat** (6) — crystal aura, silent aura, auto totem, instant TNT, clicker, TNT placer
- **Manaslu Flux Movement** (4) — universal/elytra/boat flight, auto walk
- **Manaslu Flux Player** (7) — eating, fishing, login/logout, respawn, inventory keeper, fast use
- **Manaslu Flux World** (4) — placer, block replacer, wither builder, map art
- **Manaslu Flux Client Side** (8) — purely client-side/visual modules
- **Manaslu Flux Utility** (12) — chat, logs and tools
- **Manaslu Flux Bypasses** (5) — modules that beat a server/anticheat check instead of changing what you do: cobweb bypass, click TP, pearl phase, VClip PhaseFill, universal flight

Highlights:

- **Crystal Aura** — full port of BlackOut's Auto Crystal+: enemy / friend / self damage model with min–max ratios, force pop and anti-pop, their place and explode speed models, six switch modes, existed/inhibit/set-dead gates and extrapolated hitboxes.
- **Silent Aura** — attacks the nearest player with server-side-only aim (your camera never moves) and a silent weapon switch.
- **Instant TNT** — ignites TNT by looking at it, including behind walls (click-through-walls); falls back to placing a redstone block when you have no igniter (and mines that block straight back during the fuse), and last of all shoots the TNT with a Flame bow.
- **Pearl Phase** — full port of BlackOut's Auto Pearl: pearl at your own block to phase into walls, with their CC bypass, four switch modes and instant rotation.
- **VClip PhaseFill** — vclip a few blocks, seal the space you left behind and let the server rubberband you; if it doesn't, you are vclipped back.
- **Cobweb Bypass** — no-slow in webs: the cobweb movement multiplier is replaced client-side, with per-anticheat profiles (vanilla, NCP, Themis, Grim), a per-axis speed cap, a lagback watcher, sprint tweaks that release sprint before a web and put it back on after a set time inside one, and a Timer that only runs while you stand in a web.
- **Auto Login** — sends `/login <password>` automatically after joining.
- **Image / GIF HUD** — display images and animated GIFs directly on your HUD.
- **Server Info / Session Info / Kill Counter HUD** — server address + live ping, in-game day/time/weather/biome, and a session combat tracker.

The full, always up-to-date feature list lives in [FEATURES.md](FEATURES.md).

## Modules

<details>
<summary>Full module list (46)</summary>

**Manaslu Flux Combat (6):** `auto-totem`, `clicker`, `crystal-aura`, `instant-tnt`, `silent-aura`, `tnt-placer`

**Manaslu Flux Movement (4):** `auto-walk-hold`, `boat-flight`, `elytra-flight`, `place-travel`

**Manaslu Flux Player (7):** `auto-eat`, `auto-fish`, `auto-log`, `auto-login`, `mf-auto-respawn`, `inventory-keeper`, `whitelist-fast-use`

**Manaslu Flux World (4):** `auto-map-part`, `auto-wither`, `block-replacer`, `placer`

**Manaslu Flux Client Side (8):** `anti-drop-lagback`, `client-block-entities`, `client-side-night-vision`, `packet-limiter`, `ping-spoofer`, `sound-muter`, `toggle-tab`, `universal-colored-chat`

**Manaslu Flux Utility (12):** `auto-chat-clear`, `auto-responder`, `chat-logger`, `death-commands`, `death-coords`, `discord-rpc`, `world-origin`, `mute`, `path`, `tab-complete-privacy`, `tab-logger`, `waypoint`

**Manaslu Flux Bypasses (5):** `cobweb-bypass`, `mf-click-tp`, `pearl-phase`, `universal-flight`, `vclip-phasefill`

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

[`blackout/`](blackout/README.md) holds unmodified copies of the BlackOut files this addon ports — upstream [KassuK1/BlackOut](https://github.com/KassuK1/BlackOut) at commit `4c18af3f`, **GPL-3.0** (same as this project), with the original `LICENSE` alongside. Currently `AutoPearl.java` (→ `pearl-phase`) and `AutoCrystalPlus.java` (→ `crystal-aura`). It is reference material only, not compiled.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Drop [Meteor Client](https://meteorclient.com) and `manasluflux-<version>.jar` into your `mods` folder.
3. Launch the game — the new categories appear in Meteor's ClickGUI (Right Shift).

## Version history

See [FEATURES.md](FEATURES.md) for the complete changelog — the addon version is bumped on every change, and the website is rebuilt and published with every release.

## License

This project is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE), consistent with Meteor Client's licensing.

Some modules are rewritten takes on ideas from other open-source clients (Meteor Client, BlackOut, RyanWare). Where a module is a port rather than a rewrite, the upstream file is vendored unmodified in [`blackout/`](blackout/README.md) (currently BlackOut's `AutoPearl.java`, GPL-3.0, for `pearl-phase`), and the port's javadoc names the original author. The bundled Baritone jar is the unmodified official release and remains under its LGPL-3.0 license.

## Disclaimer

Use of this addon on servers you don't own may violate their rules. Use responsibly and at your own risk.
