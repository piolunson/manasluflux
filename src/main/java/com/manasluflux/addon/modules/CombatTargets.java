package com.manasluflux.addon.modules;

import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared target finding for the combat modules. Scans the full rendered entity list so
 * Meteor fake players are found too, and skips friends, dead, spectator and creative players.
 */
final class CombatTargets {
    private CombatTargets() {}

    static PlayerEntity findTarget(double range) {
        if (mc.player == null || mc.world == null) return null;

        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;

        // mc.world.getPlayers() misses Meteor fake players (they are added as plain client-side
        // entities, not through the player registry), so scan the full entity list too.
        List<PlayerEntity> candidates = new ArrayList<>(mc.world.getPlayers());
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player) candidates.add(player);
        }

        for (PlayerEntity player : candidates) {
            if (player == mc.player) continue;
            if (player.isDead() || player.isSpectator() || player.isCreative()) continue;
            if (!Friends.get().shouldAttack(player)) continue;

            double dist = mc.player.distanceTo(player);
            if (dist > range || dist >= bestDist) continue;

            best = player;
            bestDist = dist;
        }

        return best;
    }
}
