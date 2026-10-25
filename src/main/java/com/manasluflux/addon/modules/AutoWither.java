// Ported from Trouser-Streak (etianl) - who skidded it from Germanminer with permission!
// Original: https://github.com/Germanminer/MeteorServerUtils/blob/master/src/main/java/com/example/addon/modules/AutoWither.java
// Trouser-Streak source: https://github.com/etianl/Trouser-Streak/blob/master/src/main/java/pwn/noobs/trouserstreak/modules/AutoWither.java
package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
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
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.text.Text;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class AutoWither extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNametag = settings.createGroup("Nametag");
    private final SettingGroup sgVisuals = settings.createGroup("Visuals");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Tick delay between placing blocks.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away you can build: the aimed spot must be within this distance. Server reach is ~4.5 blocks in survival.")
        .defaultValue(4.5)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );
    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allow air placing.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> placeCenterSkull = sgGeneral.add(new BoolSetting.Builder()
        .name("center-skull")
        .description("Place the skull in the center of the wither.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> lockRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-rotation")
        .description("Locks the rotation of the wither.")
        .defaultValue(false)
        .build()
    );
    private final Setting<CardinalDirection> chosenDirection = sgGeneral.add(new EnumSetting.Builder<CardinalDirection>()
        .name("direction")
        .description("The direction to be locked to (North means the wither's arms will be west-east).")
        .defaultValue(CardinalDirection.North)
        .visible(lockRotation::get)
        .build()
    );

    private final Setting<Boolean> nametagIt = sgNametag.add(new BoolSetting.Builder()
        .name("nametag")
        .description("Right after the wither spawns, silently applies a name tag to it.")
        .defaultValue(true)
        .build()
    );
    private final Setting<String> witherName = sgNametag.add(new StringSetting.Builder()
        .name("wither-name")
        .description("Name written on the used name tag (creative mode renames the tag directly; survival uses an already-renamed tag).")
        .defaultValue("Wither")
        .visible(nametagIt::get)
        .build()
    );
    private final Setting<Integer> nametagDelay = sgNametag.add(new IntSetting.Builder()
        .name("nametag-delay")
        .description("Ticks to wait after the wither spawns before the name tag is applied. 0 = instant.")
        .defaultValue(5)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .visible(nametagIt::get)
        .build()
    );

    private final Setting<Boolean> renderPreview = sgVisuals.add(new BoolSetting.Builder()
        .name("preview")
        .description("If the blocks should be rendered")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> swingHand = sgVisuals.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing hand.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> previewColor = sgColors.add(new ColorSetting.Builder()
        .name("preview-color")
        .description("fill color for the preview.")
        .defaultValue(new SettingColor(51, 207, 255, 50))
        .build()
    );
    private final Setting<SettingColor> previewOutlineColor = sgColors.add(new ColorSetting.Builder()
        .name("preview-outline-color")
        .description("outline color for the preview.")
        .defaultValue(new SettingColor(112, 136, 255, 255))
        .build()
    );

    public AutoWither() {
        super(AddonTemplate.WORLD_CATEGORY, "auto-wither", "Automatically builds a wither where you look (right-click to place the projected skeleton) and optionally nametags it. Ported from Trouser-Streak.");
    }

    // ===== Nametag tracking =====
    private enum State { IDLE, NAMETAG_WAIT, NAMETAG, DONE }

    private State nametagState = State.IDLE;
    private WitherEntity pendingWither;
    private int nametagTicks;
    private boolean building = false;
    private boolean hasMaterials = false;
    private BlockPos previewPos;
    private final List<BlockPos> pendingBlocks = new ArrayList<>();
    private final List<BlockPos> pendingSkulls = new ArrayList<>();
    private int tickDelay = 0;

    // ===== Materials =====
    @Override
    public void onActivate() {
        info("Aim at a spot and press the use key (right-click) to build a wither.");
        hasMaterials = hasWitherMaterials();
        int withers = countWithersAvailable();
        info(withers > 0 ? "You can build " + withers + " withers." : "You don't have the required materials for a wither.");
        building = false;
        nametagState = State.IDLE;
        pendingWither = null;
    }

    private int countWithersAvailable() {
        int soul = 0, skull = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (item == Items.SOUL_SAND || item == Items.SOUL_SOIL) soul += stack.getCount();
            else if (item == Items.WITHER_SKELETON_SKULL) skull += stack.getCount();
        }
        return Math.min(soul / 4, skull / 3);
    }

    private boolean hasWitherMaterials() {
        int soul = 0, skull = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (item == Items.SOUL_SAND || item == Items.SOUL_SOIL) soul += stack.getCount();
            else if (item == Items.WITHER_SKELETON_SKULL) skull += stack.getCount();
            if (mc.player.isInCreativeMode()) {
                if (soul >= 1 && skull >= 1) return true;
            } else {
                if (soul >= 4 && skull >= 3) return true;
            }
        }
        return false;
    }

    @Override
    public void onDeactivate() {
        building = false;
        nametagState = State.IDLE;
        pendingWither = null;
    }

    // ===== Input =====
    @EventHandler
    private void onMouseButton(MouseClickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return; // do nothing inside GUIs
        if (event.click.button() != 1) return; // right mouse button only
        if (event.action != KeyAction.Press) return;
        if (building) return;

        if (!hasMaterials) {
            info("You don't have the required materials to build a wither.");
            return;
        }
        if (previewPos != null) {
            event.setCancelled(true);
            building = true;
            placeWither(previewPos);
            nametagState = State.NAMETAG_WAIT;
            nametagTicks = 0;
        }
    }

    // ===== Placement loop =====
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        hasMaterials = hasWitherMaterials();

        if (!building) return;

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        int originalSlot = mc.player.getInventory().getSelectedSlot();

        if (delay.get() == 0) {
            // Instant: dump everything in one tick.
            while (!pendingBlocks.isEmpty()) {
                BlockPos pos = pendingBlocks.remove(0);
                placeSoulBlock(pos);
            }
            while (!pendingSkulls.isEmpty()) {
                BlockPos pos = pendingSkulls.remove(0);
                placeBlock(pos, Items.WITHER_SKELETON_SKULL);
            }
            mc.player.getInventory().setSelectedSlot(originalSlot);
            building = false;
        } else {
            if (!pendingBlocks.isEmpty()) {
                BlockPos pos = pendingBlocks.remove(0);
                placeSoulBlock(pos);
                mc.player.getInventory().setSelectedSlot(originalSlot);
                tickDelay = delay.get();
            } else if (!pendingSkulls.isEmpty()) {
                BlockPos pos = pendingSkulls.remove(0);
                placeBlock(pos, Items.WITHER_SKELETON_SKULL);
                mc.player.getInventory().setSelectedSlot(originalSlot);
                tickDelay = delay.get();
            } else {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                building = false;
            }
        }
    }

    // ===== Nametag =====
    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // update preview position from the crosshair.
        updatePreviewPos();
        if (!nametagIt.get() || nametagState == State.IDLE) return;

        if (nametagState == State.NAMETAG_WAIT) {
            WitherEntity wither = findBuiltWither();
            if (wither != null) {
                pendingWither = wither;
                if (nametagDelay.get() > 0 && nametagTicks < nametagDelay.get()) {
                    nametagTicks++;
                    return;
                }
                nametagState = State.NAMETAG;
            } else {
                nametagTicks++;
                if (nametagTicks > 200) { // give up after ~10s
                    nametagState = State.IDLE;
                }
            }
        }

        if (nametagState == State.NAMETAG) {
            applyNametag();
            nametagState = State.DONE;
        }
    }

    private void updatePreviewPos() {
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            // Out of configured range: no preview, nothing can be built there.
            if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > range.get() * range.get()) {
                previewPos = null;
                return;
            }
            BlockState state = mc.world.getBlockState(pos);
            if (airPlace.get()) {
                if (state.isSolidBlock(mc.world, pos)) {
                    previewPos = pos.up();
                } else {
                    previewPos = pos;
                }
            } else {
                if (state.isSolidBlock(mc.world, pos)) {
                    previewPos = pos.up();
                } else {
                    previewPos = null;
                }
            }
        } else {
            previewPos = null;
        }
    }

    private WitherEntity findBuiltWither() {
        if (previewPos == null) return null;
        Vec3d center = Vec3d.ofCenter(previewPos);
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof WitherEntity w && w.getEntityPos().distanceTo(center) < 12) return w;
        }
        return null;
    }

    private void applyNametag() {
        if (pendingWither == null) return;
        int tagSlot = findHotbarSlot(Items.NAME_TAG);
        if (tagSlot == -1) {
            warning("No name tag in the hotbar to name the wither.");
            pendingWither = null;
            return;
        }

        // Creative: write the configured name onto the tag (client inventory is authoritative).
        if (mc.player.isInCreativeMode() && !witherName.get().isEmpty()) {
            ItemStack tagStack = mc.player.getInventory().getStack(tagSlot);
            tagStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(witherName.get()));
            mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(tagSlot, tagStack));
        }

        int originalSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(tagSlot));
        mc.interactionManager.interactEntityAtLocation(mc.player, pendingWither,
            new EntityHitResult(pendingWither), Hand.MAIN_HAND);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
        mc.player.getInventory().setSelectedSlot(originalSlot);
        mc.player.swingHand(Hand.MAIN_HAND);

        pendingWither = null;
    }

    // ===== Build plan =====
    private void placeWither(BlockPos basePos) {
        if (!checkWitherBlocks(basePos)) {
            info("Obstruction prevented the wither from being placed.");
            nametagState = State.IDLE;
            return;
        }

        pendingBlocks.clear();
        pendingSkulls.clear();

        Direction direction = lockRotation.get() ? chosenDirection.get().toDirection() : mc.player.getHorizontalFacing();

        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            pendingBlocks.add(basePos.up().west());
            pendingBlocks.add(basePos.up().east());
            pendingSkulls.add(basePos.up().west());
            pendingSkulls.add(basePos.up().east());
        } else {
            pendingBlocks.add(basePos.up().south());
            pendingBlocks.add(basePos.up().north());
            pendingSkulls.add(basePos.up().south());
            pendingSkulls.add(basePos.up().north());
        }
        pendingBlocks.add(basePos);
        pendingBlocks.add(basePos.up());
        if (placeCenterSkull.get()) {
            pendingSkulls.add(basePos.up());
        }

        tickDelay = 0;
    }

    private boolean checkWitherBlocks(BlockPos basePos) {
        List<BlockPos> blockPositions = new ArrayList<>();
        Direction direction = lockRotation.get() ? chosenDirection.get().toDirection() : mc.player.getHorizontalFacing();

        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            blockPositions.add(basePos.up().west());
            blockPositions.add(basePos.up().east());
            blockPositions.add(basePos.up(2).west());
            blockPositions.add(basePos.up(2).east());
            blockPositions.add(basePos.west());
            blockPositions.add(basePos.east());
        } else {
            blockPositions.add(basePos.up().south());
            blockPositions.add(basePos.up().north());
            blockPositions.add(basePos.up(2).south());
            blockPositions.add(basePos.up(2).north());
            blockPositions.add(basePos.south());
            blockPositions.add(basePos.north());
        }
        blockPositions.add(basePos);
        blockPositions.add(basePos.up());
        if (placeCenterSkull.get()) {
            blockPositions.add(basePos.up(2));
        }

        for (BlockPos pos : blockPositions) {
            if (!mc.world.getBlockState(pos).isAir()) return false;
        }
        blockPositions.remove(4);
        blockPositions.remove(5);
        for (BlockPos pos : blockPositions) {
            if (!checkBlockForEntity(pos)) return false;
        }
        return true;
    }

    private boolean checkBlockForEntity(BlockPos pos) {
        List<Entity> entities = mc.world.getEntitiesByType(TypeFilter.instanceOf(Entity.class),
            new Box(pos), e -> !(e instanceof ItemEntity || e instanceof ExperienceOrbEntity));
        return entities.isEmpty();
    }

    // ===== Actual block placing =====
    private void placeSoulBlock(BlockPos pos) {
        Item soulBlock = hasSoulSandHotbar() ? Items.SOUL_SAND : Items.SOUL_SOIL;
        placeBlock(pos, soulBlock);
    }

    private void placeBlock(BlockPos pos, Item item) {
        int slot = findHotbarSlot(item);
        if (slot == -1) return;
        mc.player.getInventory().setSelectedSlot(slot);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
            new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
        if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findHotbarSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private boolean hasSoulSandHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.SOUL_SAND) return true;
        }
        return false;
    }

    // ===== Render =====
    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (!renderPreview.get() || !hasMaterials || previewPos == null) return;

        Direction direction = lockRotation.get() ? chosenDirection.get().toDirection() : mc.player.getHorizontalFacing();

        event.renderer.box(previewPos, previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
        event.renderer.box(previewPos.up(), previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            event.renderer.box(previewPos.up().west(), previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
            event.renderer.box(previewPos.up().east(), previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
            renderSkull(event, previewPos.up(2).west());
            renderSkull(event, previewPos.up(2).east());
        } else {
            event.renderer.box(previewPos.up().south(), previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
            event.renderer.box(previewPos.up().north(), previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
            renderSkull(event, previewPos.up(2).south());
            renderSkull(event, previewPos.up(2).north());
        }
        if (placeCenterSkull.get()) {
            renderSkull(event, previewPos.up(2));
        }
    }

    private void renderSkull(Render3DEvent event, BlockPos pos) {
        double shrink = 0.23; // how small the skull appears
        event.renderer.box(
            pos.getX() + shrink,
            pos.getY(),
            pos.getZ() + shrink,
            pos.getX() + 1 - shrink,
            pos.getY() + 0.5, // shorter than a full block
            pos.getZ() + 1 - shrink,
            previewColor.get(),
            previewOutlineColor.get(),
            ShapeMode.Both,
            0
        );
    }

    private enum CardinalDirection {
        North(Direction.NORTH),
        South(Direction.SOUTH),
        East(Direction.EAST),
        West(Direction.WEST);

        private final Direction mcDirection;

        CardinalDirection(Direction mcDirection) {
            this.mcDirection = mcDirection;
        }

        public Direction toDirection() {
            return mcDirection;
        }
    }

    @Override
    public String getInfoString() {
        return building ? "building" : (nametagState == State.NAMETAG_WAIT || nametagState == State.NAMETAG ? "tagging" : null);
    }
}
