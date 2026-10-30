# Vendored Sydney sources

Original files kept here **unmodified** so a port can always be checked against its source.
Nothing in this folder is compiled — Gradle only builds `src/main/java`.

- Upstream: [sydney-client/Sydney-Legacy](https://github.com/sydney-client/Sydney-Legacy)
- Pinned at commit `1b058031e8c2b29f0c49bb48cb250b955b90730e` (2025-06-12)
- Upstream is Minecraft **1.21.4 / Yarn** (`1.21.4+build.2`), this addon is **1.21.11**
- License: **GNU GPL-3.0** — see [LICENSE](LICENSE). This addon is GPL-3.0 too, so ports of these
  files are license-compatible; the port keeps the attribution in its class javadoc.

## What is here

| File | Upstream path | Ported as | Status |
|---|---|---|---|
| [modules/SurroundModule.java](modules/SurroundModule.java) | `src/main/java/me/aidan/sydney/modules/impl/combat/SurroundModule.java` | `com.manasluflux.addon.modules.Surround` (`mf-surround`) | Ported in 1.13.0 |
| [utils/HoleUtils.java](utils/HoleUtils.java) | `src/main/java/me/aidan/sydney/utils/minecraft/HoleUtils.java` | `Surround#calculateTargets` (only `getFeetPositions`) | Ported in 1.13.0 |
| [modules/KillSayModule.java](modules/KillSayModule.java) | `src/main/java/me/aidan/sydney/modules/impl/combat/KillSayModule.java` | `com.manasluflux.addon.modules.KillSay` (`kill-say`) | Ported in 1.14.0 |
| [modules/FOVModifierModule.java](modules/FOVModifierModule.java) | `src/main/java/me/aidan/sydney/modules/impl/miscellaneous/FOVModifierModule.java` | `com.manasluflux.addon.modules.FovModifier` (`fov-modifier`) | Ported in 1.14.0 |

The helper behaviour Sydney spreads over `WorldUtils`, `InventoryUtils`, `PositionUtils` and
`RotationUtils` is reproduced inside `Surround` as private methods, because each one is a handful of
lines and copying the whole utility classes would drag in their GUI, manager and event plumbing:

| Sydney source | Used for | In the port |
|---|---|---|
| `WorldUtils#isPlaceable(BlockPos, boolean)` | can this spot take a block | `Surround#isPlaceable` |
| `WorldUtils#getDirection(BlockPos, List, boolean)` + `getStrictDirections` | which side to click | `Surround#getDirection` / `#getStrictDirections` |
| `WorldUtils#destroyCrystals(BlockPos)` | clear a crystal out of the spot | `Surround#destroyCrystals` |
| `WorldUtils#placeBlock(...)` | the click itself | `Surround#placeBlock` |
| `InventoryUtils#findHardestBlock(int, int)` | which block item to use | `Surround#findHardestBlock` |
| `InventoryUtils#switchSlot/switchBack` | getting it in the mainhand | `InvUtils.swap` / `swapBack` |
| `PositionUtils#getFlooredPosition(Entity)` | the block you stand in | `Surround#flooredPosition` |
| `RotationUtils#getRotations(double, double, double)` | the look packet | `Surround#rotationsTo` (same ±2° jitter) |

## Porting notes

**Kept exactly.** The feet-position geometry (standing block + four sides, `extension` adding every
column an entity is standing in, `floor` adding the block below each), the `range` and
`isPlaceable` filters, hardest-block selection, `limit` blocks per group with `delay` ticks between
groups, the support-then-retry placement (place the block below, retry the target on the next tick),
`strict-direction`, `center`, `crystal-destruction`, `while-eating`, `self-disable`, `jump-disable`,
`item-disable`, `step-toggle`, `render`, the `Surround/8` info string, and both `timing` modes.

**Kill Say and FOV Modifier (1.14.0).** Both keep Sydney's behaviour and change only what Meteor
provides instead of Sydney's own plumbing. `FOVModifierModule` is a settings holder: Sydney forces the
value inside `GameRendererMixin#getFov`, while the port overwrites Meteor's `GetFovEvent` from the
same place, so it needs no mixin and its `info.getReturnValue() == 70` guard (the fixed-FOV passes for
held items) becomes a 70.0 check behind `items`. `KillSayModule` keeps its four messages, random pick
without repeating the last one, `delay`-spaced queue, `clientside`, `green-text`, `kill-streak`,
`suicide-ignore` and `/kill` bookkeeping; its `TargetDeathEvent` maps onto the entity-removal death
credit the addon's Kill Counter already uses, its `PlayerPopEvent` onto the totem status packet, and
`killfile.txt` / `popfile.txt` onto `StringListSetting`s so the messages live in the config instead of
a file next to the jar.

**Changed for Meteor.** `PlayerUpdateEvent` → `TickEvent.Pre`, `PacketReceiveEvent` →
`PacketEvent.Receive`, `Sydney.MODULE_MANAGER` → `Modules.get()`, the swap manager →
`InvUtils.swap(slot, silent)` with `swapBack()`, `ROTATION_MANAGER.packetRotate` →
`Rotations.rotate(yaw, pitch, 50, …)`, `NetworkUtils.sendSequencedPacket` →
`BlockUtils.interact` (Meteor's sequenced `useItemOn`, which also swings).

**Not reproduced, and why.**

- `HitboxDesync` — Sydney filters its own entity out of `isPlaceable` and trims two spots when that
  module is on. Meteor has no HitboxDesync, so the port always behaves as if it were off.
- `switch` modes `AltPickup` / `AltSwap` — these are Sydney's inventory-click swaps. The port offers
  `None` / `Silent` / `Normal`, the three Meteor's `InvUtils` gives.
- `speed-toggle` — Meteor ships no Speed module (1.21.11 has `Sprint`, not a speed changer), so
  there is nothing to turn off. `step-toggle` stays and drives Meteor's `Step`.
- The sprint-stop and shift-press packets Sydney wraps around an interactable block
  (`ClientCommandC2SPacket` `STOP_SPRINTING` / `PRESS_SHIFT_KEY`). 1.21.11 removed the shift-press
  mode (sneaking became an input flag sent in `PlayerInputC2SPacket`), so the port does not fake it.
- **One spot per tick instead of up to `limit` per tick.** Meteor's `Rotations` drains a single
  rotation per movement packet and sends a `PlayerMoveC2SPacket` for every rotation left in the
  queue, so Sydney's four-per-tick burst turns into four look packets a tick and, at `delay 0`, a
  packet-rate kick (`You are sending too many packets`). The port fills the same spots in the same
  order, one per tick, and skips a spot for 100 ms after placing into it until the server confirms.
  The swap also has to happen inside the rotation callback: the callback runs when the packet is
  sent, so swapping before `Rotations.rotate` means the placement goes out with the wrong item.
- `timing = Sequential` runs one tick later than Sydney's. Sydney fires on the raw packet in the
  netty thread and builds a throwaway `EndCrystalEntity` to attack, whose id the server does not
  know — that is exactly the "invalid entity" kick this addon already ate once. The port records the
  spawn and attacks the real entity by id on the next tick, which also means an id the client has
  dropped is never sent.
