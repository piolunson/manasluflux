# ManasluFlux

A [Meteor Client](https://meteorclient.com) addon for **Minecraft 1.21.11 (Fabric)** by **piolunson**.

🌐 **Website & downloads:** [github.com/piolunson/ManasluFluxMeteorWebsite](https://github.com/piolunson/ManasluFluxMeteorWebsite)

## About

ManasluFlux adds **44 modules**, **26 commands** and **7 HUD elements** on top of Meteor Client, organized in six ClickGUI categories:

- **Manaslu Flux Combat** (7) — crystal aura, silent aura, auto totem, instant TNT, pearl phase
- **Manaslu Flux Movement** (6) — universal/elytra/boat flight, auto walk, click TP, Place Travel
- **Manaslu Flux Player** (7) — eating, fishing, login/logout, respawn, inventory keeper, fast use
- **Manaslu Flux World** (4) — placer, block replacer, wither builder, map art
- **Manaslu Flux Client Side** (8) — purely client-side/visual modules
- **Manaslu Flux Utility** (12) — chat, logs and tools

Highlights:

- **Crystal Aura** — full explosion damage simulation: every obsidian/bedrock base in range is evaluated with `min-damage` / `max-self-damage` / `anti-suicide` gating, face place, obsidian support blocks, silent server-side rotations and hotbar swaps.
- **Silent Aura** — attacks the nearest player with server-side-only aim (your camera never moves) and a silent weapon switch.
- **Instant TNT** — ignites TNT by looking at it, including behind walls (click-through-walls); falls back to placing a redstone block when you have no igniter (and mines that block straight back during the fuse), and last of all shoots the TNT with a Flame bow.
- **Pearl Phase** — one-shot pearl throw at your own block to phase into walls.
- **Auto Login** — sends `/login <password>` automatically after joining.
- **Image / GIF HUD** — display images and animated GIFs directly on your HUD.
- **Server Info / Session Info / Kill Counter HUD** — server address + live ping, in-game day/time/weather/biome, and a session combat tracker.

The full, always up-to-date feature list lives in [FEATURES.md](FEATURES.md).

## Modules

<details>
<summary>Full module list (44)</summary>

**Manaslu Flux Combat (7):** `auto-totem`, `clicker`, `crystal-aura`, `instant-tnt`, `pearl-phase`, `silent-aura`, `tnt-placer`

**Manaslu Flux Movement (6):** `auto-walk-hold`, `boat-flight`, `mf-click-tp`, `elytra-flight`, `place-travel`, `universal-flight`

**Manaslu Flux Player (7):** `auto-eat`, `auto-fish`, `auto-log`, `auto-login`, `mf-auto-respawn`, `inventory-keeper`, `whitelist-fast-use`

**Manaslu Flux World (4):** `auto-map-part`, `auto-wither`, `block-replacer`, `placer`

**Manaslu Flux Client Side (8):** `anti-drop-lagback`, `client-block-entities`, `client-side-night-vision`, `packet-limiter`, `ping-spoofer`, `sound-muter`, `toggle-tab`, `universal-colored-chat`

**Manaslu Flux Utility (12):** `auto-chat-clear`, `auto-responder`, `chat-logger`, `death-commands`, `death-coords`, `discord-rpc`, `world-origin`, `mute`, `path`, `tab-complete-privacy`, `tab-logger`, `waypoint`

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

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Drop [Meteor Client](https://meteorclient.com) and `manasluflux-<version>.jar` into your `mods` folder.
3. Launch the game — the new categories appear in Meteor's ClickGUI (Right Shift).

## Version history

See [FEATURES.md](FEATURES.md) for the complete changelog — the addon version is bumped on every change, and the website is rebuilt and published with every release.

## License

This project is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE), consistent with Meteor Client's licensing.

Some modules are rewritten takes on ideas from other open-source clients (Meteor Client, BlackOut, RyanWare); all code in this repository is a clean-room rewrite for Minecraft 1.21.11 and this addon's conventions. The bundled Baritone jar is the unmodified official release and remains under its LGPL-3.0 license.

## Disclaimer

Use of this addon on servers you don't own may violate their rules. Use responsibly and at your own risk.
