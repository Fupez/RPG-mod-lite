package dev.fupez.rpgplayable.abilities;

import dev.fupez.rpgplayable.stats.DerivedStats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/** Warrior impacts; resource payments and cast timing belong to the common runtime. */
public final class WarriorKit implements AbilityKit {
    private static final double CHARGE_DISTANCE = 5.0;

    @Override
    public boolean canStart(ServerPlayerEntity player, AbilityId id) {
        return switch (id) {
            case HEAVY_STRIKE -> true;
            case CHARGE -> player.isOnGround() && !player.hasVehicle()
                    && AbilityEffects.findDestination(player, AbilityEffects.forward(player), CHARGE_DISTANCE)
                    .filter(destination -> destination.squaredDistanceTo(player.getEntityPos()) >= 0.25)
                    .isPresent();
            case GROUND_SLAM -> player.isOnGround() && !player.hasVehicle();
            default -> false;
        };
    }

    @Override
    public void execute(ServerPlayerEntity player, AbilityId id, DerivedStats stats) {
        switch (id) {
            case HEAVY_STRIKE -> heavyStrike(player, stats);
            case CHARGE -> charge(player, stats);
            case GROUND_SLAM -> groundSlam(player, stats);
            default -> { }
        }
    }

    private void heavyStrike(ServerPlayerEntity player, DerivedStats stats) {
        int hits = 0;
        for (var target : AbilityEffects.cone(player, 3.5, 110).stream().limit(8).toList()) {
            if (AbilityEffects.damage(player, target, AbilityCatalog.damage(AbilityId.HEAVY_STRIKE) + stats.meleeBonus() * 1.4, false)) {
                hits++;
                AbilityEffects.particles(player.getEntityWorld(), ParticleTypes.CRIT,
                        target.getEntityPos().add(0, target.getHeight() * 0.6, 0), 7, 0.22, 0.12);
            }
        }
        for (int i = 0; i < 9; i++) {
            double angle = Math.toRadians(player.getYaw() - 55 + i * 13.75);
            Vec3d position = player.getEntityPos().add(-Math.sin(angle) * 2.4, 1, Math.cos(angle) * 2.4);
            AbilityEffects.particles(player.getEntityWorld(), ParticleTypes.SWEEP_ATTACK, position, 1, 0, 0);
        }
        AbilityEffects.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, hits > 0 ? 0.8f : 1.15f);
        if (hits > 0) AbilityEffects.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 0.65f, 0.85f);
    }

    private void charge(ServerPlayerEntity player, DerivedStats stats) {
        // Recheck at impact: a ledge, obstacle or mount may have changed during windup.
        if (!canStart(player, AbilityId.CHARGE)) return;
        Vec3d start = player.getEntityPos();
        Vec3d direction = AbilityEffects.forward(player);
        var candidates = AbilityEffects.hostiles(player, CHARGE_DISTANCE + 1.5);
        if (!AbilityEffects.safeDash(player, direction, CHARGE_DISTANCE)) return;
        Vec3d end = player.getEntityPos();
        Vec3d travel = end.subtract(start);
        double horizontalSquared = travel.x * travel.x + travel.z * travel.z;
        int hits = 0;
        for (var target : candidates) {
            if (hits >= 4) break;
            double projection = horizontalSquared < 1.0e-6 ? 0 :
                    ((target.getX() - start.x) * travel.x + (target.getZ() - start.z) * travel.z)
                            / horizontalSquared;
            double fraction = Math.max(0, Math.min(1, projection));
            Vec3d closest = start.add(travel.multiply(fraction));
            double dx = target.getX() - closest.x;
            double dz = target.getZ() - closest.z;
            if (dx * dx + dz * dz > 1.5 * 1.5 || Math.abs(target.getY() - closest.y) > 1.8
                    || !player.canSee(target)) continue;
            if (AbilityEffects.damage(player, target, AbilityCatalog.damage(AbilityId.CHARGE) + stats.meleeBonus() * 0.9, false)) {
                hits++;
                target.takeKnockback(0.8, -direction.x, -direction.z);
                target.velocityModified = true;
                AbilityEffects.particles(player.getEntityWorld(), ParticleTypes.CRIT,
                        target.getEntityPos().add(0, 0.8, 0), 8, 0.25, 0.12);
            }
        }
        int segments = Math.max(1, (int) Math.ceil(travel.length() * 3));
        for (int i = 0; i <= segments; i++) {
            AbilityEffects.particles(player.getEntityWorld(), ParticleTypes.CLOUD,
                    start.add(travel.multiply((double) i / segments)).add(0, 0.2, 0), 2, 0.15, 0.025);
        }
        AbilityEffects.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.85f, hits > 0 ? 0.8f : 1.2f);
    }

    private void groundSlam(ServerPlayerEntity player, DerivedStats stats) {
        if (!canStart(player, AbilityId.GROUND_SLAM)) return;
        double knockback = 0.65 + Math.min(0.35, Math.max(0, stats.supportMultiplier() - 1) * 0.15);
        for (var target : AbilityEffects.hostiles(player, 4.0).stream().limit(12).toList()) {
            if (Math.abs(target.getY() - player.getY()) > 1.75) continue;
            if (AbilityEffects.damage(player, target, AbilityCatalog.damage(AbilityId.GROUND_SLAM) + stats.meleeBonus() * 1.1, false)) {
                knockAway(player, target, knockback);
                AbilityEffects.particles(player.getEntityWorld(), ParticleTypes.CRIT,
                        target.getEntityPos().add(0, 0.5, 0), 5, 0.2, 0.08);
            }
        }
        // Fixed-size rings keep both network traffic and native particle work bounded.
        for (int ring = 1; ring <= 2; ring++) {
            double radius = ring * 1.8;
            for (int i = 0; i < 20; i++) {
                double angle = i * Math.PI / 10;
                AbilityEffects.particles(player.getEntityWorld(), ParticleTypes.POOF,
                        player.getEntityPos().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius),
                        1, 0.08, 0.025);
            }
        }
        AbilityEffects.sound(player, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 0.85f, 0.65f);
        AbilityEffects.sound(player, SoundEvents.BLOCK_STONE_BREAK, 1.0f, 0.75f);
    }

    private static void knockAway(ServerPlayerEntity player, LivingEntity target, double strength) {
        double dx = player.getX() - target.getX();
        double dz = player.getZ() - target.getZ();
        if (dx * dx + dz * dz < 1.0e-6) {
            var forward = AbilityEffects.forward(player);
            dx = -forward.x;
            dz = -forward.z;
        }
        target.takeKnockback(strength, dx, dz);
        target.velocityModified = true;
    }
}
