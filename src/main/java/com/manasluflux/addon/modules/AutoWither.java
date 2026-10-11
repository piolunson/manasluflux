package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a wither where you are aiming: projects the 3x3 skeleton as ghost blocks, then
 * places it block by block out of your inventory (6 soul sand + 3 wither skulls, exactly
 * the vanilla spawn structure). When the server spawns the wither it is nametagged -
 * in creative mode the name tag is renamed to the configured name client-side (creative
 * inventory is authoritative); in survival an already anvil-renamed name tag from the
 * hotbar is used. Block delay, layer delay and nametag delay are each configurable,
 * with 0 = as fast as the server accepts.
 */
public class AutoWither extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ---- Settings ----
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away the wither can be built (distance to the block you aim at).")
        .defaultValue(4.5)
        .min(1.0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Integer> blockDelay = sgGeneral.add(new IntSetting.Builder()
        .name("block-delay")
        .description("Ticks between placing each block of the wither. 0 = every tick.")
        .defaultValue(1)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> layerDelay = sgGeneral.add(new IntSetting.Builder()
        .name("layer-delay")
        .description("Extra ticks added when moving up to the next layer. 0 = none.")
        .defaultValue(3)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> nametag = sgGeneral.add(new BoolSetting.Builder()
        .name("nametag")
        .description("Applies a name tag to the spawned wither.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> nametagDelay = sgGeneral.add(new IntSetting.Builder()
        .name("nametag-delay")
        .description("Ticks to wait after the wither spawns before the name tag is applied. 0 = instant.")
        .defaultValue(5)
        .min(0)
        .sliderMax(100)
        .visible(nametag::get)
        .build()
    );

    private final Setting<String> witherName = sgGeneral.add(new StringSetting.Builder()
        .name("wither-name")
        .description("Name given to the wither in creative mode (the renamed tag is applied directly). Leave empty to keep the tag's own name.")
        .defaultValue("Wither")
        .visible(nametag::get)
        .build()
    );

    private final Setting<Boolean> silentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-swap")
        .description("Places blocks and applies the name tag with server-side hotbar swaps so your visible slot never changes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Silently rotates to each placement, like vanilla block placement.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> rearmDelay = sgGeneral.add(new IntSetting.Builder()
        .name("rearm-delay")
        .description("Ticks to wait after a finished wither before aiming somewhere new builds another one.")
        .defaultValue(40)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> project = sgRender.add(new BoolSetting.Builder()
        .name("project")
        .description("Shows the ghost outline of the wither at the aim spot before/while building.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color of the projected blocks.")
        .defaultValue(new SettingColor(255, 255, 255, 25))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color of the projected blocks.")
        .defaultValue(new SettingColor(255, 255, 255, 140))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Fill and/or outline for the projection.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    // ---- Build plan (vanilla wither structure, 3 wide x 3 tall) ----
    // Indices 0..5 = soul sand (bottom row, middle row), 6..8 = wither skulls on top.
    private static final int PLAN_SIZE = 9;
    private static final int SKULL_START = 6;
    private final BlockPos[] plan = new BlockPos[PLAN_SIZE];

    // ---- State machine ----
    private enum State { IDLE, BUILDING, NAMETAG_WAIT, NAMETAG, DONE }

    private State state = State.IDLE;
    private BlockPos base;            // center of the middle layer
    private Direction facing;         // horizontal direction the wither faces
    private int planIndex;            // next plan slot to place
    private int timer;                // universal countdown for the current pause
    private int waitTicks;            // nametag wait / rearm countdown
    private WitherBoss pendingWither;
    private boolean warnedNoBlocks;
    private boolean warnedNoTag;

    public AutoWither() {
        super(AddonTemplate.CATEGORY, "auto-wither", "Projects a wither at your aim spot, then places it piece by piece (6 soul sand + 3 wither skulls) and automatically nametags the spawned wither. Block, layer and nametag delays are all configurable with 0 = instant.");
    }

    @Override
    public void onActivate() {
        resetBuild();
    }

    @Override
    public void onDeactivate() {
        resetBuild();
    }

    private void resetBuild() {
        state = State.IDLE;
        base = null;
        facing = null;
        planIndex = 0;
        timer = 0;
        waitTicks = 0;
        pendingWither = null;
        warnedNoBlocks = false;
        warnedNoTag = false;
    }

    // ---------- Spot finding + plan ----------

    private boolean findSpot() {
        // Anchor = the block you are looking at.
        var hit = mc.player.pick(range.get() + 4, 0, false);
        if (!(hit instanceof BlockHitResult bhr)) return false;

        BlockPos anchor = bhr.getBlockPos();
        // Pick the dominant horizontal axis of the look vector.
        Vec3 look = mc.player.getLookAngle();
        Direction dir;
        if (Math.abs(look.x) >= Math.abs(look.z)) {
            dir = look.x >= 0 ? Direction.EAST : Direction.WEST;
        } else {
            dir = look.z >= 0 ? Direction.SOUTH : Direction.NORTH;
        }

        // Base center = one block in front of the anchor, on top of it; wither faces you.
        base = anchor.above().relative(dir);
        facing = dir.getOpposite();
        buildPlan();
        return true;
    }

    private void buildPlan() {
        Direction side = facing.getClockWise();

        // Bottom row (soul sand) sits on top of the anchor's level.
        plan[0] = base.below().relative(side, -1);
        plan[1] = base.below();
        plan[2] = base.below().relative(side, 1);
        // Middle row (soul sand).
        plan[3] = base.relative(side, -1);
        plan[4] = base;
        plan[5] = base.relative(side, 1);
        // Top row (3 wither skulls, one per column, on top of the middle row).
        plan[6] = base.above().relative(side, -1);
        plan[7] = base.above();
        plan[8] = base.above().relative(side, 1);
    }

    private boolean isSkullStep(int i) {
        return i >= SKULL_START;
    }

    // ---------- Tick logic ----------

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        switch (state) {
            case IDLE -> {
                if (timer > 0) {
                    timer--;
                    return;
                }
                if (findSpot() && haveMaterials()) {
                    state = State.BUILDING;
                    planIndex = 0;
                    timer = 0;
                } else {
                    base = null; // try again next tick
                }
            }

            case BUILDING -> {
                if (planIndex >= PLAN_SIZE) {
                    state = State.NAMETAG_WAIT;
                    waitTicks = 0;
                    return;
                }
                if (timer > 0) {
                    timer--;
                    return;
                }

                boolean placed = placeStep();
                if (placed) {
                    planIndex++;
                    // Extra pause when moving up a layer.
                    boolean layerSwitch = planIndex < PLAN_SIZE
                        && plan[planIndex].getY() != plan[planIndex - 1].getY();
                    timer = blockDelay.get() + (layerSwitch ? layerDelay.get() : 0);
                } else {
                    timer = 5; // out of blocks etc. - retry shortly
                }
            }

            case NAMETAG_WAIT -> {
                WitherBoss wither = findBuiltWither();
                if (wither != null) {
                    pendingWither = wither;
                    if (!nametag.get()) {
                        finish();
                        return;
                    }
                    if (nametagDelay.get() > 0) {
                        // Wait out the configured delay first.
                        if (waitTicks < nametagDelay.get()) {
                            waitTicks++;
                            return;
                        }
                    }
                    state = State.NAMETAG;
                } else {
                    // Still spawning - give up after ~10s.
                    waitTicks++;
                    if (waitTicks > 200) finish();
                }
            }

            case NAMETAG -> {
                applyNametag();
                finish();
            }

            case DONE -> {
                if (timer > 0) {
                    timer--;
                    return;
                }
                // Rearm: aiming somewhere new starts the next wither.
                resetBuild();
            }
        }
    }

    private void finish() {
        state = State.DONE;
        timer = rearmDelay.get();
        pendingWither = null;
    }

    private boolean haveMaterials() {
        FindItemResult sand = InvUtils.find(Items.SOUL_SAND);
        FindItemResult skull = InvUtils.find(Items.WITHER_SKELETON_SKULL);
        boolean ok = sand.found() && sand.count() >= 6 && skull.found() && skull.count() >= 3;
        if (!ok && !warnedNoBlocks) {
            warning("Need at least 6 soul sand and 3 wither skulls in the inventory.");
            warnedNoBlocks = true;
        }
        if (ok) warnedNoBlocks = false;
        return ok;
    }

    private boolean placeStep() {
        BlockPos pos = plan[planIndex];
        boolean isSkull = isSkullStep(planIndex);

        // Already there?
        var st = mc.level.getBlockState(pos);
        if (isSkull ? st.is(Blocks.WITHER_SKELETON_SKULL) : st.is(Blocks.SOUL_SAND)) return true;

        FindItemResult item = InvUtils.find(isSkull ? Items.WITHER_SKELETON_SKULL : Items.SOUL_SAND);
        if (!item.found() || !item.isHotbar()) {
            if (!warnedNoBlocks) {
                warning("Ran out of %s.", isSkull ? "wither skulls" : "soul sand");
                warnedNoBlocks = true;
            }
            return false;
        }

        return BlockUtils.place(pos, item, rotate.get(), 50);
    }

    // ---------- Nametag ----------

    private WitherBoss findBuiltWither() {
        if (base == null) return null;
        Vec3 center = Vec3.atCenterOf(base);
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof WitherBoss w && w.position().distanceTo(center) < 6) return w;
        }
        return null;
    }

    private void applyNametag() {
        if (pendingWither == null) return;

        FindItemResult tag = InvUtils.find(Items.NAME_TAG);
        if (!tag.found() || !tag.isHotbar()) {
            if (!warnedNoTag) {
                warning("No name tag in the hotbar to name the wither.");
                warnedNoTag = true;
            }
            return;
        }
        warnedNoTag = false;

        // In creative the client inventory is authoritative: rename the tag directly
        // and push the edited stack to the server.
        if (mc.player.isCreative() && !witherName.get().isEmpty()) {
            var stack = mc.player.getInventory().getItem(tag.slot());
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(witherName.get()));
            mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(tag.slot(), stack));
        }
        // Survival: the tag must already have been renamed in an anvil; use it as-is.

        int realSlot = mc.player.getInventory().getSelectedSlot();
        if (silentSwap.get()) {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(tag.slot()));
            mc.gameMode.interact(mc.player, pendingWither, new EntityHitResult(pendingWither), InteractionHand.MAIN_HAND);
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(realSlot));
        } else {
            InvUtils.swap(tag.slot(), false);
            mc.gameMode.interact(mc.player, pendingWither, new EntityHitResult(pendingWither), InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    // ---------- Projection render ----------

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!project.get() || base == null) return;

        for (int i = 0; i < PLAN_SIZE; i++) {
            BlockPos pos = plan[i];
            var st = mc.level.getBlockState(pos);
            boolean placed = isSkullStep(i) ? st.is(Blocks.WITHER_SKELETON_SKULL) : st.is(Blocks.SOUL_SAND);
            if (placed) continue;

            boolean next = state == State.BUILDING && i == planIndex;
            SettingColor side = next ? new SettingColor(80, 220, 80, 40) : sideColor.get();
            SettingColor line = next ? new SettingColor(80, 220, 80, 180) : lineColor.get();

            event.renderer.box(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                side, line, shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() {
        return switch (state) {
            case BUILDING -> planIndex + "/" + PLAN_SIZE;
            case NAMETAG_WAIT, NAMETAG -> "tagging";
            case DONE -> "done";
            default -> null;
        };
    }
}
