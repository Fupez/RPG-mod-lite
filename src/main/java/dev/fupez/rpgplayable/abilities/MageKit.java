package dev.fupez.rpgplayable.abilities;

import dev.fupez.rpgplayable.stats.DerivedStats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** Native particle spells; projectiles exist only as bounded server tick state. */
public final class MageKit implements AbilityKit {
    private static final int MAX_BOLTS = 128;
    private static final int BOLT_LIFETIME = 28;
    private static final double BOLT_SPEED = 0.9;
    private static final double BLINK_DISTANCE = 7;
    private final List<Firebolt> bolts = new ArrayList<>();

    @Override
    public boolean canStart(ServerPlayerEntity player, AbilityId id) {
        return switch (id) {
            case FIREBOLT -> bolts.size() < MAX_BOLTS;
            case BLINK -> AbilityEffects.findDestination(player, AbilityEffects.forward(player), BLINK_DISTANCE).isPresent();
            case ARCANE_BURST -> true;
            default -> false;
        };
    }

    @Override
    public void execute(ServerPlayerEntity player, AbilityId id, DerivedStats stats) {
        switch (id) {
            case FIREBOLT -> {
                if (bolts.size() >= MAX_BOLTS) return;
                bolts.add(new Firebolt(player, player.getEntityWorld().getRegistryKey(),
                        player.getEyePos(), player.getRotationVec(1).normalize().multiply(BOLT_SPEED),
                        AbilityCatalog.damage(AbilityId.FIREBOLT) * stats.magicMultiplier()));
                AbilityEffects.sound(player, SoundEvents.ENTITY_BLAZE_SHOOT, 0.65f, 1.3f);
            }
            case ARCANE_BURST -> arcaneBurst(player, stats);
            case BLINK -> blink(player);
            default -> { }
        }
    }

    private void arcaneBurst(ServerPlayerEntity player, DerivedStats stats) {
        ServerWorld world = player.getEntityWorld();
        Vec3d center = player.getEntityPos().add(0, 0.75, 0);
        // A ring communicates the same four-block radius used by the server hit query.
        for (int point = 0; point < 32; point++) {
            double angle = point * Math.PI * 2 / 32;
            AbilityEffects.particles(world, ParticleTypes.ENCHANTED_HIT,
                    center.add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4), 2, 0.08, 0.02);
        }
        AbilityEffects.particles(world, ParticleTypes.ELECTRIC_SPARK, center, 24, 0.7, 0.12);
        AbilityEffects.sound(player, SoundEvents.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.4f);
        boolean hit = false;
        for (LivingEntity target : AbilityEffects.hostiles(player, 4)) {
            hit |= AbilityEffects.damage(player, target,
                    AbilityCatalog.damage(AbilityId.ARCANE_BURST) * stats.magicMultiplier(), true);
        }
        if (hit) {
            player.heal((float) Math.min(4, 1.5 * stats.supportMultiplier()));
            AbilityEffects.particles(world, ParticleTypes.ENCHANT, center, 12, 0.5, 0.08);
        }
    }

    private void blink(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        Vec3d departure = player.getEntityPos().add(0, 0.8, 0);
        if (!AbilityEffects.safeDash(player, AbilityEffects.forward(player), BLINK_DISTANCE)) return;
        AbilityEffects.particles(world, ParticleTypes.PORTAL, departure, 24, 0.5, 0.25);
        AbilityEffects.particles(world, ParticleTypes.REVERSE_PORTAL,
                player.getEntityPos().add(0, 0.8, 0), 24, 0.5, 0.12);
        AbilityEffects.sound(player, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.65f, 1.2f);
    }

    @Override
    public void tick(MinecraftServer server) {
        Iterator<Firebolt> iterator = bolts.iterator();
        while (iterator.hasNext()) {
            Firebolt bolt = iterator.next();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(bolt.owner.getUuid());
            if (owner != bolt.owner || !owner.isAlive() || owner.isSpectator() || owner.isCreative()
                    || !owner.getEntityWorld().getRegistryKey().equals(bolt.dimension)
                    || --bolt.remaining < 0 || !advance(owner, bolt)) {
                iterator.remove();
            }
        }
    }

    private boolean advance(ServerPlayerEntity owner, Firebolt bolt) {
        ServerWorld world = owner.getEntityWorld();
        Vec3d start = bolt.position;
        Vec3d end = start.add(bolt.velocity);
        // Check each substep before touching blocks so a spell cannot load a remote chunk.
        for (int step = 0; step <= 2; step++) {
            BlockPos sample = BlockPos.ofFloored(start.lerp(end, step / 2.0));
            if (!world.getChunkManager().isChunkLoaded(sample.getX() >> 4, sample.getZ() >> 4)) return false;
        }
        HitResult block = world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner));
        Vec3d unobstructedEnd = block.getType() == HitResult.Type.MISS ? end : block.getPos();
        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class,
                new Box(start, unobstructedEnd).expand(0.25),
                entity -> entity instanceof Monster && entity.isAlive() && !entity.isRemoved());
        // Segment entry distance, not entity center distance, determines the first impact.
        LivingEntity closest = null;
        Vec3d closestPoint = null;
        double nearest = start.squaredDistanceTo(unobstructedEnd);
        candidates.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(start)));
        for (int index = 0; index < Math.min(32, candidates.size()); index++) {
            LivingEntity target = candidates.get(index);
            var intersection = target.getBoundingBox().raycast(start, unobstructedEnd);
            Vec3d point = target.getBoundingBox().contains(start)
                    ? start : intersection.orElse(null);
            if (point != null && point.squaredDistanceTo(start) < nearest) {
                nearest = point.squaredDistanceTo(start);
                closest = target;
                closestPoint = point;
            }
        }
        Vec3d visibleEnd = closestPoint == null ? unobstructedEnd : closestPoint;
        for (int step = 1; step <= 3; step++) {
            var point = start.lerp(visibleEnd, step / 3.0);
            // Close flame sprites cover the first-person camera; retain the traveling trail beyond the hand.
            if (point.squaredDistanceTo(owner.getEyePos()) >= 2.25)
                AbilityEffects.particles(world, ParticleTypes.SMALL_FLAME, point, 2, 0.035, 0.005);
        }
        if (closest != null) {
            AbilityEffects.damage(owner, closest, bolt.damage, true);
            impact(world, visibleEnd);
            return false;
        }
        if (block.getType() != HitResult.Type.MISS) {
            impact(world, visibleEnd);
            return false;
        }
        bolt.position = end;
        return true;
    }

    private void impact(ServerWorld world, Vec3d position) {
        AbilityEffects.particles(world, ParticleTypes.SMALL_FLAME, position, 12, 0.16, 0.04);
    }

    @Override
    public void clear() {
        bolts.clear();
    }

    private static final class Firebolt {
        private final ServerPlayerEntity owner;
        private final RegistryKey<World> dimension;
        private Vec3d position;
        private final Vec3d velocity;
        private final double damage;
        private int remaining = BOLT_LIFETIME;

        private Firebolt(ServerPlayerEntity owner, RegistryKey<World> dimension, Vec3d position, Vec3d velocity, double damage) {
            this.owner = owner;
            this.dimension = dimension;
            this.position = position;
            this.velocity = velocity;
            this.damage = damage;
        }
    }
}
