# Vendored Homovore sources

Original files kept here **unmodified** so the port can always be checked against its source.
Nothing in this folder is compiled — Gradle only builds `src/main/java`.

- Upstream: [leonetics/homovore-public](https://github.com/leonetics/homovore-public)
- Pinned at commit `72a4151c81d5d070a98815dc43a946fe7c597f4a` (2026-07-27)
- License: **GNU AGPL-3.0** — see [LICENSE](LICENSE).

## License note (read this before shipping a build)

This addon is **GPL-3.0**. Homovore is **AGPL-3.0**. GPL-3.0 §13 allows the two to be combined
into one work, but the combined work must be conveyed **under AGPL-3.0**, not GPL-3.0 — so once a
port of this file is part of the jar you distribute, the addon as a whole is an AGPL-3.0 work and
its source has to be offered under that license. Keep that in mind if the LICENSE file here still
says GPL-3.0.

## What is here

| File | Upstream path | Ported as | Status |
|---|---|---|---|
| [modules/SpeedMineModule.java](modules/SpeedMineModule.java) | `src/main/java/dev/leonetic/features/modules/world/SpeedMineModule.java` | `com.manasluflux.addon.modules.SpeedMine` (`mf-speed-mine`) | Ported in 1.11.7 |
| [modules/MineApi.java](modules/MineApi.java) | `src/main/java/dev/leonetic/features/modules/world/MineApi.java` | — | **Not ported**: it exists so AutoMine/Nuker can request breaks, and this addon has no such module. |

## Porting notes

Homovore is **Mojang-mapped**; this addon is **Yarn-mapped 1.21.11 / Meteor 1.21.11**, so the port
is an adaptation, not a copy. Homovore's helper layer is not vendored either:

| Homovore | Here |
|---|---|
| `AttackBlockEvent` (cancelled, then packet-mined) | Meteor's `StartBreakingBlockEvent`, cancelled the same way |
| `InteractionUtil.getBreakDelta(...)` | Meteor's `BlockUtils.getBreakDelta(slot, state)` |
| `Homovore.swapManager.withSwap(..., SILENT)` / `latch` / `release` | `InvUtils.swap(slot, true)` held for the break, `InvUtils.swapBack()` when it ends |
| `Render3DEvent.getMatrix()` + `RenderUtil.drawBox*` | Meteor's `Render3DEvent` + `Renderer3D.box(...)` with `ShapeMode.Both` |
| `PreTickEvent` | Meteor's `TickEvent.Post` |
| `mc.level`, `mc.getConnection().send(...)` | `mc.world`, `mc.getNetworkHandler().sendPacket(...)` |
| `Mth` / `AABB` / `Vec3` / `VoxelShape.bounds()` | `MathHelper` / `Box` / `Vec3d` / `getOutlineShape(...).getBoundingBox()` |
| `ServerboundPlayerActionPacket` / `ServerboundSwingPacket` | `PlayerActionC2SPacket` / `HandSwingC2SPacket` |

`faceMargin` uses the player's current position only, where Homovore also checks the previous
tick's position; the previous-position fields have no stable Yarn name in 1.21.11, and the current
tick is the stricter of the two checks anyway.
