package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Trigger Bot: while the crosshair ray is on one of the chosen entities, the selected aura
 * module(s) are switched on; looking away switches them back off. Only modules this trigger
 * enabled are disabled again, so an aura you turned on yourself is left alone, and everything
 * is released when the trigger is switched off.
 *
 * <p>The ray is its own: {@code mc.crosshairTarget} stops at the vanilla attack reach, while
 * this one reaches {@code range} blocks, so an aura with longer reach still triggers.
 */
public class TriggerBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities that pull the trigger.")
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<List<Module>> auras = sgGeneral.add(new ModuleListSetting.Builder()
        .name("aura")
        .description("Modules switched on while your crosshair is on a target.")
        .defaultValue(SilentAura.class)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far the crosshair ray reaches, in blocks.")
        .defaultValue(3.0)
        .range(1.0, 8.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final List<Module> triggered = new ArrayList<>();

    public TriggerBot() {
        super(AddonTemplate.COMBAT_CATEGORY, "mf-trigger-bot", "Switches the chosen aura on while your crosshair is on a selected entity.");
    }

    @Override
    public void onDeactivate() {
        release();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (lookedAt() != null) engage();
        else release();
    }

    /** Switches on every selected aura that is not already running, remembering what we started. */
    private void engage() {
        for (Module aura : auras.get()) {
            if (aura == this) continue;

            // Already on: either ours (nothing to do) or the player's own (never touched).
            if (aura.isActive()) continue;

            // Ours, and the player just turned it off mid-look - drop it and respect that.
            if (triggered.remove(aura)) continue;

            aura.enable();
            triggered.add(aura);
        }
    }

    /** Switches off only what this trigger switched on. */
    private void release() {
        for (Module aura : triggered) aura.disable();
        triggered.clear();
    }

    /** First matching entity under the crosshair within range, or null. */
    private Entity lookedAt() {
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(mc.player.getRotationVec(1.0f).multiply(range.get()));
        double best = range.get() * range.get();

        Entity closest = null;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity.isRemoved() || !entity.isAlive()) continue;
            if (entity instanceof PlayerEntity player && player.isSpectator()) continue;
            if (!entities.get().contains(entity.getType())) continue;

            Box box = entity.getBoundingBox();

            if (box.contains(eye)) return entity;

            Optional<Vec3d> hit = box.raycast(eye, end);
            if (hit.isEmpty()) continue;

            double distance = eye.squaredDistanceTo(hit.get());
            if (distance < best) {
                best = distance;
                closest = entity;
            }
        }

        return closest;
    }
}
