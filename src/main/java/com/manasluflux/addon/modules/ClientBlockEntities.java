package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Like Crystal Optimizer, but for any block entity: pick block entity types by
 * id (chest, sign, beacon, spawner, shulker box...) and their block-entity
 * rendering is done client-side only — either replaced by a single static box or
 * hidden entirely. The vanilla block-entity part (items inside a chest, sign
 * text, beacon beam, spawner mob...) is skipped, which is a big FPS boost with
 * many heavy block entities on screen. The block itself (the chest model etc.)
 * still renders normally, and nothing is sent to the server.
 */
public class ClientBlockEntities extends Module {
    private static final Modules MODULES = Modules.get();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Simple: render a plain static box where the block entity is. Hide: don't render its block-entity part at all (the block itself still shows).")
        .defaultValue(Mode.Simple)
        .build()
    );

    private final Setting<List<String>> entityIds = sgGeneral.add(new StringListSetting.Builder()
        .name("block-entities")
        .description("Block entity type ids to affect, e.g. minecraft:chest, minecraft:sign, minecraft:beacon, minecraft:spawner, minecraft:shulker_box.")
        .defaultValue(new ArrayList<>(List.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:shulker_box", "minecraft:barrel", "minecraft:sign", "minecraft:spawner")))
        .build()
    );

    private final Setting<ShapeMode> boxShape = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("box-shape")
        .description("Fill and/or outline for the simple box.")
        .defaultValue(ShapeMode.Both)
        .visible(() -> mode.get() == Mode.Simple)
        .build()
    );

    private final Setting<SettingColor> boxColor = sgGeneral.add(new ColorSetting.Builder()
        .name("box-color")
        .description("The color of the simple box.")
        .defaultValue(new SettingColor(255, 220, 120, 50))
        .visible(() -> mode.get() == Mode.Simple)
        .build()
    );

    // Resolved BlockEntityType set + the string the cache is for. Rebuilt when the
    // setting changes or a new registry set arrives (dimension / relog).
    private final Set<BlockEntityType<?>> cachedTypes = new HashSet<>();
    private String cachedSource = null;

    public ClientBlockEntities() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "client-block-entities", "Makes chosen block entities (chests, signs, beacons, spawners...) render client-side only - either a single static box or hidden. Does not touch the block itself. Purely client-side.");
    }

    /** Rebuilds the type cache if the id list changed or the registry contents rotated. */
    private Set<BlockEntityType<?>> matchedTypes() {
        String source = entityIds.get().toString();
        if (cachedTypes.isEmpty() && !entityIds.get().isEmpty()) {
            if (!source.equals(cachedSource) || cachedSource == null) {
                cachedTypes.clear();
                Registry<BlockEntityType<?>> registry = BuiltInRegistries.BLOCK_ENTITY_TYPE;
                for (String id : entityIds.get()) {
                    try {
                        BlockEntityType<?> type = registry.getValue(Identifier.parse(id.trim().toLowerCase()));
                        if (type != null) cachedTypes.add(type);
                    } catch (Exception ignored) {}
                }
                cachedSource = source;
            }
        } else if (entityIds.get().isEmpty()) {
            cachedTypes.clear();
            cachedSource = "";
        }
        return cachedTypes;
    }

    private boolean matches(BlockEntity be) {
        return matchedTypes().contains(be.getType());
    }

    /**
     * Called from BlockEntityDispatcherMixin - true if the vanilla block-entity
     * render should be skipped for this type.
     */
    public static boolean shouldSkip(BlockEntityType<?> type) {
        ClientBlockEntities module = MODULES.get(ClientBlockEntities.class);
        if (module == null || !module.isActive()) return false;
        return module.matchedTypes().contains(type);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mode.get() != Mode.Simple) return;
        Set<BlockEntityType<?>> types = matchedTypes();
        if (types.isEmpty()) return;

        int viewDist = mc.options.getEffectiveRenderDistance();
        int pcx = mc.player != null ? mc.player.chunkPosition().x() : 0;
        int pcz = mc.player != null ? mc.player.chunkPosition().z() : 0;

        for (int cx = pcx - viewDist; cx <= pcx + viewDist; cx++) {
            for (int cz = pcz - viewDist; cz <= pcz + viewDist; cz++) {
                LevelChunk chunk = mc.level.getChunk(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!types.contains(be.getType())) continue;

                    BlockPos pos = be.getBlockPos();
                    event.renderer.box(
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                        boxColor.get(), boxColor.get(), boxShape.get(), 0
                    );
                }
            }
        }
    }

    public enum Mode {
        Simple,
        Hide
    }
}
