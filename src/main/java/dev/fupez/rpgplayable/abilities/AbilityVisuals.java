package dev.fupez.rpgplayable.abilities;

import dev.fupez.rpgplayable.network.VisualEffectPayload;
import dev.fupez.rpgplayable.network.VisualEffectPayload.Kind;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.sound.SoundEvents;

/** One nearby announcement replaces dozens of server particle packets. Never changes simulation. */
public final class AbilityVisuals {
    private AbilityVisuals() { }
    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(VisualEffectPayload.ID, VisualEffectPayload.CODEC);
    }
    public static void start(ServerPlayerEntity player, AbilityDefinition definition) {
        emit(player, Kind.START, definition.id(), player.getEntityPos(),
                player.getEntityPos().add(AbilityEffects.forward(player)), radius(definition.id()), definition.windupTicks());
        AbilityEffects.sound(player, switch (definition.playerClass()) {
            case WARRIOR -> SoundEvents.BLOCK_COPPER_HIT;
            case MAGE -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            case ROGUE -> SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN;
            default -> SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP;
        }, 0.16f, definition.playerClass() == dev.fupez.rpgplayable.player.PlayerClass.WARRIOR ? .8f : 1.6f);
    }
    public static void cancel(ServerPlayerEntity player, AbilityId id) {
        emit(player, Kind.CANCEL, id, player.getEntityPos(), player.getEntityPos(), 0, 0);
    }
    public static void release(ServerPlayerEntity player, AbilityId id) {
        emit(player, Kind.RELEASE, id, player.getEntityPos(),
                player.getEntityPos().add(AbilityEffects.forward(player)), radius(id), 0);
    }
    public static void movement(ServerPlayerEntity player, AbilityId id, Vec3d start, Vec3d end) {
        emit(player, Kind.RELEASE, id, start, end, 0, 0);
    }
    public static void segment(ServerPlayerEntity player, AbilityId id, Vec3d start, Vec3d end) {
        emit(player, Kind.TRAVEL, id, start, end, 0, 0);
    }
    public static void hit(ServerPlayerEntity player, AbilityId id, Vec3d point) {
        emit(player, Kind.HIT, id, point, point, 0, 0);
    }
    public static void impact(ServerPlayerEntity player, AbilityId id, Vec3d point) {
        emit(player, Kind.IMPACT, id, point, point, 0, 0);
    }
    public static void levelUp(ServerPlayerEntity player) {
        emit(player, Kind.LEVEL_UP, AbilityId.HEAVY_STRIKE, player.getEntityPos(), player.getEntityPos(), 1.1f, 0);
    }
    private static float radius(AbilityId id) {
        return switch (id) { case HEAVY_STRIKE -> 3.5f; case GROUND_SLAM, ARCANE_BURST -> 4; default -> 0; };
    }
    static VisualEffectPayload safePayload(Kind kind, AbilityId id, int entityId, Vec3d origin,
                                           Vec3d end, float radius, int duration, long seed) {
        try { return new VisualEffectPayload(kind, id.ordinal(), entityId, origin, end, radius, duration, seed); }
        catch (IllegalArgumentException invalidCosmeticGeometry) { return null; }
    }
    private static void emit(ServerPlayerEntity player, Kind kind, AbilityId id, Vec3d origin, Vec3d end, float radius, int duration) {
        var world = player.getEntityWorld();
        // A cosmetic position at the world boundary must never abort an accepted gameplay action.
        var payload = safePayload(kind, id, player.getId(), origin, end, radius, duration,
                world.getTime() * 31 + player.getId() * 17L + id.ordinal());
        if (payload == null) return;
        // 40 blocks is also the client cut-off; a resolved projectile may be ahead of its caster.
        for (var watcher : PlayerLookup.around(world, origin, 40))
            if (ServerPlayNetworking.canSend(watcher, VisualEffectPayload.ID)) ServerPlayNetworking.send(watcher, payload);
    }
}
