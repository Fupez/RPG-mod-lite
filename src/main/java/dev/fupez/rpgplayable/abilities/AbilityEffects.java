package dev.fupez.rpgplayable.abilities;

import java.util.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.math.*;

/** Bounded native operations reused by the three concrete kits. */
public final class AbilityEffects {
    private AbilityEffects() { }
    public static List<LivingEntity> hostiles(ServerPlayerEntity player, double radius) {
        var result = player.getEntityWorld().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
                target -> target.isAlive() && target.getType().getSpawnGroup() == SpawnGroup.MONSTER
                        && target.squaredDistanceTo(player) <= radius * radius && player.canSee(target));
        result.sort(Comparator.comparingDouble(player::squaredDistanceTo));
        return result.stream().limit(32).toList();
    }
    public static List<LivingEntity> cone(ServerPlayerEntity player, double radius, double degrees) {
        var forward = forward(player);
        double cosine = Math.cos(Math.toRadians(degrees / 2));
        return hostiles(player, radius).stream().filter(target -> {
            var delta = target.getEntityPos().subtract(player.getEntityPos()).multiply(1, 0, 1);
            return delta.lengthSquared() < 1e-8 || delta.normalize().dotProduct(forward) >= cosine;
        }).toList();
    }
    public static boolean damage(ServerPlayerEntity player, LivingEntity target, double amount, boolean magic) {
        if (!Double.isFinite(amount) || amount <= 0 || !target.isAlive() || !player.isAlive()
                || player.isDisconnected() || player.isCreative() || player.isSpectator()
                || player.getEntityWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) != player || target.getType().getSpawnGroup() != SpawnGroup.MONSTER
                || target.getEntityWorld() != player.getEntityWorld()) return false;
        var world = player.getEntityWorld();
        return target.damage(world, magic ? world.getDamageSources().indirectMagic(player, player)
                : world.getDamageSources().playerAttack(player), (float) Math.min(10000, amount));
    }
    public static Vec3d forward(ServerPlayerEntity player) {
        double yaw = Math.toRadians(player.getYaw());
        return new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
    }
    /** Checks the entire swept body path and a supported, loaded endpoint, never teleporting through a wall. */
    public static Optional<Vec3d> findDestination(ServerPlayerEntity player, Vec3d direction, double distance) {
        if (!Double.isFinite(distance) || distance <= 0 || distance > 16 || direction == null
                || !Double.isFinite(direction.lengthSquared()) || direction.lengthSquared() < 1e-8 || player.hasVehicle())
            return Optional.empty();
        var world = player.getEntityWorld();
        var start = player.getEntityPos();
        var unit = direction.normalize();
        Vec3d best = null;
        for (double step = 0.25; step <= distance + 1e-8; step += 0.25) {
            var destination = start.add(unit.multiply(step));
            var pos = BlockPos.ofFloored(destination);
            if (!world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos)
                    || !world.isSpaceEmpty(player, player.getBoundingBox().offset(destination.subtract(start)))) break;
            // A dash/blink should not strand the player over lava or an unsupported drop.
            var floor = BlockPos.ofFloored(destination.x, destination.y - 0.1, destination.z);
            if (world.isSpaceEmpty(player, player.getBoundingBox().offset(destination.subtract(start)).offset(0, -0.1, 0))
                    || !world.getFluidState(pos).isEmpty() || !world.getFluidState(floor).isEmpty()) continue;
            best = destination;
        }
        return best != null && best.squaredDistanceTo(start) >= 1 ? Optional.of(best) : Optional.empty();
    }
    public static boolean safeDash(ServerPlayerEntity player, Vec3d direction, double distance) {
        var destination = findDestination(player, direction, distance);
        if (destination.isEmpty()) return false;
        var pos = destination.get();
        player.networkHandler.requestTeleport(pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
        player.fallDistance = 0;
        return true;
    }
    public static void sound(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
        player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
    }
    public static void particles(ServerWorld world, ParticleEffect effect, Vec3d pos, int count, double spread, double speed) {
        world.spawnParticles(effect, pos.x, pos.y, pos.z, Math.min(64, Math.max(0, count)), spread, spread, spread, speed);
    }
}
