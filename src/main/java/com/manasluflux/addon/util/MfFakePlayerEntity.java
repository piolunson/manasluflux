package com.manasluflux.addon.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * A client-side fake player that the client itself treats as a real player: it has its own
 * {@link PlayerListEntry} (so skins, name lookups and the tab list work), it lives in
 * {@code world.getPlayers()} next to the real ones, and normal player entities in the world.
 *
 * That is the difference to Meteor's fake player: Meteor's one borrows *your* player-list entry
 * and only exists as a loose entity, so anything that checks player lists (aura targeting,
 * player counts, nametags, most "real player only" features) ignores it.
 */
public class MfFakePlayerEntity extends OtherClientPlayerEntity {
    private final PlayerListEntry entry;
    private boolean spawned;

    public MfFakePlayerEntity(ClientWorld world, GameProfile profile, PlayerEntity template, float health, boolean copyInv) {
        super(world, profile);

        this.entry = new PlayerListEntry(profile, false);

        setPosition(template.getX(), template.getY(), template.getZ());
        setYaw(template.getYaw());
        setPitch(template.getPitch());
        setHeadYaw(template.getYaw());
        setVelocity(Vec3d.ZERO);
        setNoGravity(true);

        if (health > 20) {
            setHealth(20);
            setAbsorptionAmount(health - 20);
        } else {
            setHealth(Math.max(1, health));
        }

        if (copyInv) getInventory().clone(template.getInventory());
    }

    /**
     * Its own player-list entry instead of falling back to the local player's.
     */
    @Override
    protected PlayerListEntry getPlayerListEntry() {
        return entry;
    }

    public PlayerListEntry entry() {
        return entry;
    }

    public void spawn() {
        if (spawned || mc.world == null) return;

        spawned = true;

        mc.world.addEntity(this);
        // Vanilla adds every joining player here - doing the same makes the whole client
        // (including mods that iterate world.getPlayers()) see a real player.
        mc.world.getPlayers().add(this);

        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().getListedPlayerListEntries().add(entry);
    }

    public void despawn() {
        if (!spawned) return;

        spawned = false;

        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().getListedPlayerListEntries().remove(entry);

        if (mc.world != null) {
            mc.world.getPlayers().remove(this);
            mc.world.removeEntity(getId(), Entity.RemovalReason.DISCARDED);
        }

        setRemoved(Entity.RemovalReason.DISCARDED);
    }

    public boolean isSpawned() {
        return spawned;
    }
}
