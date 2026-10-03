package com.manasluflux.addon.modules;

import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared target finding for the combat modules. Scans the full rendered entity list so
 * Meteor fake players are found too, and skips friends, dead, spectator and creative players.
 */
final class CombatTargets {
    private CombatTargets() {}

    static Player findTarget(double range) {
        if (mc.player == null || mc.level == null) return null;

        Player best = null;
        double bestDist = Double.MAX_VALUE;

        // mc.level.players() misses Meteor fake players (they are added as plain client-side
        // entities, not through the player registry), so scan the full entity list too.
        List<Player> candidates = new ArrayList<>(mc.level.players());
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player player) candidates.add(player);
        }

        for (Player player : candidates) {
            if (player == mc.player) continue;
            if (player.isDeadOrDying() || player.isSpectator() || player.isCreative()) continue;
            if (!Friends.get().shouldAttack(player)) continue;

            double dist = mc.player.distanceTo(player);
            if (dist > range || dist >= bestDist) continue;

            best = player;
            bestDist = dist;
        }

        return best;
    }
}
