package dev.fupez.rpgplayable.abilities;

import dev.fupez.rpgplayable.stats.DerivedStats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative movement, positional melee and short-lived thrown knife trails. */
public final class RogueKit implements AbilityKit {
    private static final double DASH_DISTANCE = 4.5;
    private static final double BACKSTAB_RANGE = 3.0;
    private static final int KNIFE_TICKS = 8;
    private static final double KNIFE_SPEED = 1.35;
    private static final int MAX_VOLLEYS = 256;
    private final List<Volley> volleys = new ArrayList<>();

    @Override
    public boolean canStart(ServerPlayerEntity player, AbilityId id) {
        return switch (id) {
            case DASH -> AbilityEffects.findDestination(player, AbilityEffects.forward(player), DASH_DISTANCE).isPresent();
            case BACKSTAB -> !AbilityEffects.cone(player, BACKSTAB_RANGE, 100).isEmpty();
            case FAN_OF_KNIVES -> volleys.size() < MAX_VOLLEYS;
            default -> false;
        };
    }

    @Override
    public void execute(ServerPlayerEntity player, AbilityId id, DerivedStats stats) {
        switch (id) {
            case DASH -> dash(player, stats);
            case BACKSTAB -> backstab(player, stats);
            case FAN_OF_KNIVES -> throwKnives(player, stats);
            default -> { }
        }
    }

    private void dash(ServerPlayerEntity player, DerivedStats stats) {
        Vec3d from = player.getSyncedPos();
        if (!AbilityEffects.safeDash(player, AbilityEffects.forward(player), DASH_DISTANCE)) return;
        Vec3d to = player.getSyncedPos();
        AbilityVisuals.movement(player, AbilityId.DASH, from, to);
        AbilityEffects.sound(player, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 0.18f, 1.7f);
        // A brief native movement boost makes the landing responsive; Spirit extends it slightly.
        int duration = 16 + (int) Math.min(8, Math.max(0, stats.supportMultiplier() - 1) * 4);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 0, false, true, true));
        AbilityEffects.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.6f);
    }

    private void backstab(ServerPlayerEntity player, DerivedStats stats) {
        List<LivingEntity> targets = AbilityEffects.cone(player, BACKSTAB_RANGE, 100);
        if (targets.isEmpty()) return;
        LivingEntity target = targets.getFirst();
        AbilityVisuals.release(player, AbilityId.BACKSTAB);
        boolean behind = isBehind(player.getX(), player.getZ(), target.getX(), target.getZ(), target.getYaw());
        double damage = (AbilityCatalog.damage(AbilityId.BACKSTAB) + stats.meleeBonus() + agilityBonus(stats))
                * (behind ? 1.8 : 1.0);
        if (AbilityEffects.damage(player, target, damage, false)) {
            AbilityVisuals.hit(player, AbilityId.BACKSTAB,
                    target.getSyncedPos().add(0, target.getHeight() * 0.6, 0));
            AbilityEffects.sound(player, SoundEvents.ITEM_TRIDENT_HIT, 0.23f, behind ? 1.6f : 1.3f);
            AbilityEffects.sound(player, behind ? SoundEvents.ENTITY_PLAYER_ATTACK_CRIT
                    : SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, behind ? 1.2f : 1.0f);
        }
    }

    /** The attacker must be within the rear 120-degree sector of the target's facing. */
    static boolean isBehind(double attackerX, double attackerZ, double targetX, double targetZ, float targetYaw) {
        double dx = attackerX - targetX;
        double dz = attackerZ - targetZ;
        double length = Math.hypot(dx, dz);
        if (length < 0.001) return false;
        double yaw = Math.toRadians(targetYaw);
        return (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / length < -0.5;
    }

    private static double agilityBonus(DerivedStats stats) {
        return Math.min(5, Math.max(0, stats.movementSpeedMultiplier() - 1) * 8
                + Math.max(0, stats.attackSpeedBonus()) * 0.25);
    }

    private void throwKnives(ServerPlayerEntity player, DerivedStats stats) {
        if (volleys.size() >= MAX_VOLLEYS) return;
        var volley = new Volley(player, AbilityCatalog.damage(AbilityId.FAN_OF_KNIVES)
                + stats.meleeBonus() * 0.6 + agilityBonus(stats));
        Vec3d origin = player.getEyePos().add(0, -0.15, 0);
        Vec3d aim = player.getRotationVec(1.0f).normalize();
        for (int i = -2; i <= 2; i++) {
            volley.knives.add(new Knife(origin, aim.rotateY((float) Math.toRadians(i * 12)).multiply(KNIFE_SPEED)));
        }
        volleys.add(volley);
        AbilityEffects.sound(player, SoundEvents.ENTITY_ARROW_SHOOT, 0.8f, 1.5f);
        AbilityVisuals.release(player, AbilityId.FAN_OF_KNIVES);
        AbilityEffects.sound(player, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, 0.17f, 1.7f);
    }

    @Override
    public void tick(MinecraftServer server) {
        var iterator = volleys.iterator();
        while (iterator.hasNext()) {
            Volley volley = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(volley.player.getUuid());
            if (player != volley.player || !player.isAlive() || player.isSpectator()
                    || player.getEntityWorld() != volley.world || ++volley.age > KNIFE_TICKS) {
                iterator.remove();
                continue;
            }
            List<LivingEntity> targets = AbilityEffects.hostiles(player, 14);
            volley.knives.removeIf(knife -> advance(volley, knife, targets));
            if (volley.knives.isEmpty()) iterator.remove();
        }
    }

    private boolean advance(Volley volley, Knife knife, List<LivingEntity> targets) {
        Vec3d start = knife.position;
        Vec3d end = start.add(knife.velocity);
        // Check each small part before raycasting so even a chunk-edge cast cannot load terrain.
        for (int i = 0; i <= 5; i++) {
            if (!volley.world.isChunkLoaded(BlockPos.ofFloored(start.lerp(end, i / 5.0)))) return true;
        }
        var blockHit = volley.world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, volley.player));
        boolean blocked = blockHit.getType() != HitResult.Type.MISS;
        if (blocked) end = blockHit.getPos();
        LivingEntity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (LivingEntity target : targets) {
            if (volley.hit.contains(target.getUuid())) continue;
            var bounds = target.getBoundingBox().expand(0.15);
            var intersection = bounds.contains(start) ? java.util.Optional.of(start) : bounds.raycast(start, end);
            if (intersection.isPresent()) {
                double distance = intersection.get().squaredDistanceTo(start);
                if (distance < closestDistance) {
                    closest = target;
                    closestDistance = distance;
                }
            }
        }
        if (closest != null) end = start.add(knife.velocity.normalize().multiply(Math.sqrt(closestDistance)));
        AbilityVisuals.segment(volley.player, AbilityId.FAN_OF_KNIVES, start, end);
        if (closest != null) {
            volley.hit.add(closest.getUuid());
            if (AbilityEffects.damage(volley.player, closest, volley.damage, false)) {
                AbilityVisuals.impact(volley.player, AbilityId.FAN_OF_KNIVES, end);
            }
            return true;
        }
        if (blocked) AbilityVisuals.impact(volley.player, AbilityId.FAN_OF_KNIVES, end);
        knife.position = end;
        return blocked;
    }

    @Override
    public void clear() {
        volleys.clear();
    }

    private static final class Volley {
        final ServerPlayerEntity player;
        final ServerWorld world;
        final double damage;
        final List<Knife> knives = new ArrayList<>(5);
        final Set<UUID> hit = new HashSet<>();
        int age;

        Volley(ServerPlayerEntity player, double damage) {
            this.player = player;
            this.world = player.getEntityWorld();
            this.damage = damage;
        }
    }

    private static final class Knife {
        Vec3d position;
        final Vec3d velocity;

        Knife(Vec3d position, Vec3d velocity) {
            this.position = position;
            this.velocity = velocity;
        }
    }
}
