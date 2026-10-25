package com.manasluflux.addon.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Owns every fake player spawned with {@code .mffakeplayer}.
 */
public final class MfFakePlayer {
    private static final List<MfFakePlayerEntity> ENTITIES = new ArrayList<>();
    private static final String[] DEFAULT_NAMES = {"ManasluBot", "FluxBot", "TestPlayer"};

    private static ClientWorld lastWorld;

    private MfFakePlayer() {}

    public static MfFakePlayerEntity add(String name, float health, boolean copyInv) {
        if (mc.player == null || mc.world == null) return null;

        checkWorld();

        String resolved = name == null || name.isBlank() ? DEFAULT_NAMES[ENTITIES.size() % DEFAULT_NAMES.length] : name;

        MfFakePlayerEntity fakePlayer = new MfFakePlayerEntity(mc.world, profile(resolved), mc.player, health, copyInv);
        fakePlayer.setPosition(spawnPosition());
        fakePlayer.spawn();

        ENTITIES.add(fakePlayer);
        return fakePlayer;
    }

    public static int remove(String name) {
        checkWorld();

        int removed = 0;
        for (MfFakePlayerEntity fake : new ArrayList<>(ENTITIES)) {
            if (!fake.getGameProfile().name().equalsIgnoreCase(name)) continue;

            fake.despawn();
            ENTITIES.remove(fake);
            removed++;
        }

        return removed;
    }

    public static int clear() {
        checkWorld();

        int count = ENTITIES.size();
        for (MfFakePlayerEntity fake : ENTITIES) fake.despawn();
        ENTITIES.clear();

        return count;
    }

    public static List<MfFakePlayerEntity> all() {
        checkWorld();
        return ENTITIES;
    }

    /**
     * Moves every fake player to the block you are looking at (or in front of you).
     */
    public static int teleport() {
        checkWorld();

        if (ENTITIES.isEmpty()) return 0;

        Vec3d pos = spawnPosition();
        for (MfFakePlayerEntity fake : ENTITIES) {
            fake.refreshPositionAndAngles(pos.x, pos.y, pos.z, fake.getYaw(), fake.getPitch());
        }

        return ENTITIES.size();
    }

    public static int setHealth(float health) {
        checkWorld();

        for (MfFakePlayerEntity fake : ENTITIES) {
            if (health > 20) {
                fake.setHealth(20);
                fake.setAbsorptionAmount(health - 20);
            } else {
                fake.setHealth(Math.max(1, health));
                fake.setAbsorptionAmount(0);
            }
        }

        return ENTITIES.size();
    }

    /**
     * Drops everything when the world changes - entities from the old world are gone anyway.
     */
    private static void checkWorld() {
        if (mc.world == lastWorld) return;

        for (MfFakePlayerEntity fake : ENTITIES) {
            if (mc.world != null) mc.world.getPlayers().remove(fake);
        }

        ENTITIES.clear();
        lastWorld = mc.world;
    }

    /**
     * @return a real-looking profile: own UUID, own name, and the skin textures of a player with
     *         that name if the tab list knows one (otherwise the default skin).
     */
    private static GameProfile profile(String name) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("ManasluFluxFake:" + name).getBytes()), name);

        if (mc.getNetworkHandler() != null && mc.player != null) {
            PlayerListEntry known = mc.getNetworkHandler().getPlayerListEntry(name);
            if (known != null) {
                var textures = known.getProfile().properties().get("textures");
                if (!textures.isEmpty()) profile.properties().putAll("textures", textures);
            }
        }

        // Nothing to copy from - leave the properties empty, the default skin is used.
        return profile;
    }

    /**
     * @return where to put a fake player: on top of the block you look at, else in front of you.
     */
    private static Vec3d spawnPosition() {
        if (mc.player == null) return Vec3d.ZERO;

        HitResult hit = mc.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos().up();
            return new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }

        Vec3d look = mc.player.getRotationVec(1.0f).normalize().multiply(2.5);
        return new Vec3d(mc.player.getX() + look.x, mc.player.getY(), mc.player.getZ() + look.z);
    }
}
