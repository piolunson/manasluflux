# Vendored BlackOut sources

Original files kept here **unmodified** so a port can always be checked against its source.
Nothing in this folder is compiled — Gradle only builds `src/main/java`.

- Upstream: [KassuK1/BlackOut](https://github.com/KassuK1/BlackOut)
- Pinned at commit `4c18af3f58040e4ff4337b81cf627daacbdfba14` (2025-01-11)
- License: **GNU GPL-3.0** — see [LICENSE](LICENSE). This addon is GPL-3.0 too, so ports of
  these files are license-compatible; every port keeps the attribution in its class javadoc.

## What is here

| File | Upstream path | Ported as | Status |
|---|---|---|---|
| [modules/AutoPearl.java](modules/AutoPearl.java) | `src/main/java/kassuk/addon/blackout/modules/AutoPearl.java` | `com.manasluflux.addon.modules.PearlPhase` (`pearl-phase`) | Ported in 1.4.0 |
| [modules/AutoCrystalPlus.java](modules/AutoCrystalPlus.java) | `src/main/java/kassuk/addon/blackout/modules/AutoCrystalPlus.java` | `com.manasluflux.addon.modules.CrystalAura` (`crystal-aura`) | Ported in 1.5.0 |

## Porting notes

BlackOut runs on Minecraft **1.21.1 / Meteor 0.5.8**; this addon is **1.21.11 / Meteor 1.21.11**,
so ports are adaptations, not copies. BlackOut's helper layer (`BlackOutModule`, `Managers`,
`SettingUtils`, `RotationUtils`, `BOInvUtils`, `SwingHand`) is not vendored — the ports use the
Meteor equivalents instead:

| BlackOut | Here |
|---|---|
| `Managers.ROTATION.start(...)` / `.end(...)` | plain client rotation (`setYaw`/`setPitch`) plus a look packet for `instant-rotation` |
| `BOInvUtils.pickSwitch` / `invSwitch` | `InvUtils.quickSwap()` into the selected hotbar slot |
| `BOInvUtils.pickSwapBack` / `swapBack` | the same `quickSwap()` again |
| `SwingHand` render option | a plain `swing` toggle |
| `BODamageUtils.crystalDamage` | `DamageUtils.crystalDamage(target, pos, box, explosion, factory)` with an `ignore-terrain` raycast factory |
| `ExtrapolationUtils.extrapolateMap` | per-player position history, averaged over `extrapolation-smoothness` ticks |
| `SettingUtils` ranges | explicit `place-range` / `place-walls-range` / `break-range` / `break-walls-range` settings |
| `IInteractEntityC2SPacket` id override (ID Predict) | not ported |
| `BlackOutModule.priority` | not needed (no competing rotation manager here) |

## Fetching another file later

```bash
git clone --depth 1 https://github.com/KassuK1/BlackOut /tmp/blackout
cp /tmp/blackout/src/main/java/kassuk/addon/blackout/modules/<Name>.java blackout/modules/
```

Copy the **whole** file (including its imports and javadoc `@author`), then adapt it in
`src/main/java/com/manasluflux/addon/modules/` — never edit the vendored copy.
