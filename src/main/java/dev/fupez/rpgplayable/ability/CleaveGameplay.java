package dev.fupez.rpgplayable.ability;

import dev.fupez.rpgplayable.config.CleaveConfig;
import dev.fupez.rpgplayable.network.*;
import dev.fupez.rpgplayable.player.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.*;
import net.minecraft.util.*;
import java.util.*;

/** Session clock and casts are server-only. UUID cooldowns outlive individual player entities. */
public final class CleaveGameplay {
    private static final Map<UUID, Active> CASTS = new HashMap<>();
    private static final AttachmentType<Long> LAST_REQUEST = AttachmentRegistry.create(
            Identifier.of("rpgplayable", "last_cleave_request"), b -> b.initializer(() -> -1L));
    private static long clock;
    private static CleaveRules rules = CleaveRules.DEFAULT;
    private static final class Active {
        final ServerPlayerEntity actor;
        final CleaveCast cast;
        final float yaw;
        final net.minecraft.registry.RegistryKey<net.minecraft.world.World> world;
        boolean interrupted;
        Active(ServerPlayerEntity actor) {
            this.actor = actor; this.cast = new CleaveCast(clock, rules);
            this.yaw = actor.getYaw(); this.world = actor.getEntityWorld().getRegistryKey();
        }
    }
    private CleaveGameplay() { }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(CleaveRequestPayload.ID, CleaveRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CleaveStatePayload.ID, CleaveStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CleaveRequestPayload.ID, (payload, context) -> {
            var player = context.player();
            if (player.getAttachedOrCreate(LAST_REQUEST) == clock) return;
            player.setAttached(LAST_REQUEST, clock);
            request(player);
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try { rules = CleaveConfig.load(FabricLoader.getInstance().getConfigDir().resolve("rpgplayable-cleave.properties")); }
            catch (java.io.IOException | IllegalArgumentException e) { throw new IllegalStateException("Cannot load Cleave tuning", e); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { CASTS.clear(); clock = 0; rules = CleaveRules.DEFAULT; });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, CleaveStatePayload.UPDATE));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> send(newPlayer, CleaveStatePayload.UPDATE));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> send(player, CleaveStatePayload.UPDATE));
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (world.isClient()) return ActionResult.PASS;
            var active = CASTS.get(player.getUuid());
            return active != null && busy(active)
                    ? ActionResult.FAIL : ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            clock++;
            var iterator = CASTS.entrySet().iterator();
            while (iterator.hasNext()) {
                var item = iterator.next();
                var active = item.getValue();
                var player = server.getPlayerManager().getPlayer(item.getKey());
                if (!active.interrupted && (player != active.actor || !eligible(active.actor)
                        || !active.world.equals(active.actor.getEntityWorld().getRegistryKey()))) {
                    active.interrupted = true;
                    if (player != null) send(player, CleaveStatePayload.INTERRUPTED);
                }
                if (!active.interrupted && active.cast.impactsAt(clock)) impact(active);
                if (active.cast.remaining(clock) == 0) {
                    if (player != null) send(player, CleaveStatePayload.UPDATE);
                    iterator.remove();
                } else if (player != null && clock % 5 == 0) send(player, CleaveStatePayload.UPDATE);
            }
        });
    }
    private static boolean eligible(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative()
                && player.getEntityWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) == player
                && RpgGameplay.progress(player).playerClass() == PlayerClass.WARRIOR;
    }
    private static boolean busy(Active active) {
        var phase = active.cast.phase(clock);
        return !active.interrupted && (phase == CleaveCast.Phase.WINDUP || phase == CleaveCast.Phase.RECOVERY);
    }
    public static boolean request(ServerPlayerEntity player) {
        var previous = CASTS.get(player.getUuid());
        if (!eligible(player) || (previous != null && previous.cast.remaining(clock) > 0)) {
            send(player, CleaveStatePayload.UNAVAILABLE);
            return false;
        }
        var active = new Active(player);
        CASTS.put(player.getUuid(), active);
        // A brief preparatory hand motion, then the attacking hand swings at the actual hit tick.
        player.swingHand(Hand.OFF_HAND, true);
        arc(active, false);
        player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), SoundCategory.PLAYERS, 0.5f, 0.8f);
        send(player, CleaveStatePayload.ACCEPTED);
        return true;
    }
    private static void impact(Active active) {
        var player = active.actor;
        var world = player.getEntityWorld();
        player.swingHand(Hand.MAIN_HAND, true);
        player.resetLastAttackedTicks();
        var candidates = world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(active.cast.rules().range(), 2, active.cast.rules().range()),
                target -> target.isAlive() && target.getType().getSpawnGroup() == SpawnGroup.MONSTER
                        && active.cast.rules().inArc(target.getX() - player.getX(), target.getY() - player.getY(), target.getZ() - player.getZ(), active.yaw)
                        && player.canSee(target));
        candidates.sort(Comparator.comparingDouble(player::squaredDistanceTo));
        int hits = 0;
        for (var target : candidates.stream().limit(8).toList()) {
            if (target.damage(world, world.getDamageSources().playerAttack(player),
                    (float) (active.cast.rules().damage() + RpgGameplay.rules().strengthBonus(RpgGameplay.progress(player))))) {
                hits++;
                world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.6), target.getZ(), 5, 0.2, 0.3, 0.2, 0.1);
            }
        }
        arc(active, true);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 0.7f, hits > 0 ? 0.9f : 1.2f);
    }
    private static void arc(Active active, boolean impact) {
        var player = active.actor;
        for (int i = 0; i < 9; i++) {
            double angle = Math.toRadians(active.yaw - 50 + i * 12.5);
            double radius = impact ? active.cast.rules().range() * 0.75 : 0.9;
            player.getEntityWorld().spawnParticles(impact ? ParticleTypes.SWEEP_ATTACK : ParticleTypes.ENCHANT,
                    player.getX() - Math.sin(angle) * radius, player.getY() + 1,
                    player.getZ() + Math.cos(angle) * radius, 1, 0, 0, 0, 0);
        }
    }
    private static void send(ServerPlayerEntity player, int result) {
        if (player.isDisconnected() || !ServerPlayNetworking.canSend(player, CleaveStatePayload.ID)) return;
        var active = CASTS.get(player.getUuid());
        var tuning = active == null ? rules : active.cast.rules();
        ServerPlayNetworking.send(player, new CleaveStatePayload(active == null ? 0 : active.cast.remaining(clock),
                tuning.cooldownTicks(), tuning.windupTicks(), tuning.recoveryTicks(), result, active != null && active.interrupted));
    }
}
