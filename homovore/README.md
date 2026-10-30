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
| [modules/AutoCrystalModule.java](modules/AutoCrystalModule.java) | `src/main/java/dev/leonetic/features/modules/combat/AutoCrystalModule.java` | `com.manasluflux.addon.modules.AutoCrystal` (`auto-crystal`) | Ported in 1.12.0 |
| [modules/PlacementManager.java](modules/PlacementManager.java) | `src/main/java/dev/leonetic/manager/PlacementManager.java` | `com.manasluflux.addon.homovore.PlacementManager` | Ported in 1.12.0 |
| [modules/BlockHighlightModule.java](modules/BlockHighlightModule.java) | `src/main/java/dev/leonetic/features/modules/render/BlockHighlightModule.java` | `com.manasluflux.addon.modules.BlockHighlight` (`block-highlight`) | Ported in 1.12.1, with a glow added |

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

### Crystal aura (1.12.0)

| Homovore | Here |
|---|---|
| `Homovore.rotationManager.submit(RotationRequest)` (`SILENT`) | Meteor's `Rotations.rotate(yaw, pitch, priority, callback)` - the break packet is the callback, so it follows the look packet |
| `Homovore.rotationManager.getServerYaw()/getServerPitch()` | `Rotations.serverYaw` / `Rotations.serverPitch` |
| `Homovore.swapManager.withSwap(..., SILENT)` in the placement manager | `InvUtils.swap(slot, true)` ... `InvUtils.swapBack()` around the whole batch |
| `ClientLevelAccessor.homovore$getBlockStatePredictionHandler()` sequence | Meteor's `BlockUtils.interact(...)`, which sends the sequenced use packet |
| Homovore's hand-rolled damage model (`MathUtil` + `CombatRules` + exposure raycast) | Meteor's `DamageUtils.crystalDamage(...)`, the same vanilla explosion formula, plus a custom `RaycastFactory` that keeps Homovore's rules: only blocks with blast resistance >= 600 shadow, blocks Speed Mine is partway through do not, and a phantom base counts as the obsidian it is about to become |
| `TargetsModule` (a separate module there) | settings on the aura itself (players / hostiles / neutrals / passives / ignore-friends) |
| `AutoSwordModule` mace-ready defer | dropped: this addon has no sword module, so there is nothing to defer to |
| Damage-sync particle-colour probe (`DamageSyncTracker`) | `timeUntilRegen` plus the full-armour / turtle-master check |
| 5 render styles on Homovore's gradient pipeline | `Renderer3D.box(...)` with `None` / `Box` (smoothed) / `Pulse` |
| Batching in `PlacementManager` (queue, 9 per 300 ms window, 50 ms per block) | identical, in `com.manasluflux.addon.homovore.PlacementManager` |

### Block highlight (1.12.1)

| Homovore | Here |
|---|---|
| `RenderBlockOutlineEvent` (cancelled, so the vanilla outline is hidden) | `mc.gameRenderer.setBlockOutlineEnabled(false)` on activate, back to `true` on deactivate |
| `RenderUtil.drawBox(matrix, box, color, lineWidth)` | `Renderer3D.boxLines(...)`; Meteor's 3D lines have a fixed width, so `line-width` is not carried over |
| the 60 ms `FadingIn` / `Visible` / `Shifting` / `FadingOut` / `Idle` state machine, smoothstep easing, 20% pinch while shifting | identical, ported line for line |
| `mc.hitResult` / `AABB` / `VoxelShape.bounds()` | `mc.crosshairTarget` / `Box` / `VoxelShape.getBoundingBox().offset(pos)` |
| — | **added:** `glow`, `glow-layers`, `glow-spread`, `glow-strength` - translucent shells outside the box, drawn with `boxSides` + `boxLines` and animated with the box |
