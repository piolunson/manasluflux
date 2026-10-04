# ManasluFlux

A [Meteor Client](https://meteorclient.com) addon for **Minecraft 26.2 (Fabric)** by **piolunson**.

🌐 **Website & downloads:** [github.com/piolunson/ManasluFluxMeteorWebsite](https://github.com/piolunson/ManasluFluxMeteorWebsite)

## About

ManasluFlux adds **38 modules**, **24 commands** and **4 HUD elements** on top of Meteor Client, organized in four ClickGUI categories:

- **Manaslu Flux** — general/utility modules
- **Manaslu Flux Client Side** — purely client-side/visual modules
- **Manaslu Flux Combat** — combat modules
- **Manaslu Flux Rewrite** — Meteor built-ins rewritten for ManasluFlux

Highlights:

- **Crystal Aura** — full explosion damage simulation: every obsidian/bedrock base in range is evaluated with `min-damage` / `max-self-damage` / `anti-suicide` gating, face place, obsidian support blocks, silent server-side rotations and hotbar swaps.
- **Silent Aura** — attacks the nearest player with server-side-only aim (your camera never moves) and a silent weapon switch.
- **Instant TNT** — ignites TNT by looking at it, including behind walls (click-through-walls); falls back to placing a redstone block when you have no igniter (and mines that block straight back during the fuse), and last of all shoots the TNT with a Flame bow.
- **Pearl Phase** — one-shot pearl throw at your own block to phase into walls.
- **Auto Login** — sends `/login <password>` automatically after joining.
- **Image / GIF HUD** — display images and animated GIFs directly on your HUD.

The full, always up-to-date feature list lives in [FEATURES.md](FEATURES.md).

## Modules

<details>
<summary>Full module list (38)</summary>

**Manaslu Flux:** Auto Eat, Auto Fish, Auto Log, Auto Walk Hold, Block Replacer, Boat Flight, Death Coords, Elytra Flight, Instant TNT, Mute, Path, Pearl Phase, Ping Spoofer, Placer, Universal Flight, Waypoint, Whitelist Fast Use, World Origin

**Manaslu Flux Client Side:** Add Text, Auto Login, Client-Side Night Vision, Crystal Optimizer, Toggle Tab, Universal Colored Chat

**Manaslu Flux Combat:** Auto Totem, Crystal Aura, Silent Aura, TNT Placer

**Manaslu Flux Rewrite:** Auto Respawn, Auto Responder, Chat Logger, Click TP, Clicker, Death Commands, Packet Limiter, Sound Muter, Tab Complete Privacy, Tab Logger

</details>

## Building

Requirements: **JDK 21+** (built with JDK 25) and internet access for Gradle dependencies.

```bash
./gradlew build
```

The jar appears in `build/libs/manasluflux-<version>.jar`.

## Bundled Baritone

ManasluFlux ships with **Baritone v1.19.0** built in — the unmodified official Fabric build for Minecraft 26.2, bundled jar-in-jar under `META-INF/jars/`. It loads automatically with the addon: no separate download needed. All of Baritone's `#` commands work in chat (`#goto x y z`, `#mine diamond_ore`, `#follow player`, …) and Meteor's Baritone integration detects it.

Source: [cabaletta/baritone](https://github.com/cabaletta/baritone) — licensed under **LGPL-3.0**, bundled unmodified with attribution.

> On Windows with an older system JDK, point Gradle at a newer one:
> `JAVA_HOME=<path-to-jdk-21+> ./gradlew build`

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2.
2. Drop [Meteor Client](https://meteorclient.com) and `manasluflux-<version>.jar` into your `mods` folder.
3. Launch the game — the new categories appear in Meteor's ClickGUI (Right Shift).

## Version history

See [FEATURES.md](FEATURES.md) for the complete changelog — the addon version is bumped on every change, and the website is rebuilt and published with every release.

## License

This project is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE), consistent with Meteor Client's licensing.

Some modules are rewritten takes on ideas from other open-source clients (Meteor Client, BlackOut, RyanWare); all code in this repository is a clean-room rewrite for Minecraft 26.2 and this addon's conventions. The bundled Baritone jar is the unmodified official release and remains under its LGPL-3.0 license.

## Disclaimer

Use of this addon on servers you don't own may violate their rules. Use responsibly and at your own risk.
