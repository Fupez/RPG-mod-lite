package dev.fupez.rpgplayable.player;

import dev.fupez.rpgplayable.RpgPlayable;
import dev.fupez.rpgplayable.config.ProgressionConfig;
import dev.fupez.rpgplayable.network.ProgressActionPayload;
import dev.fupez.rpgplayable.network.ProgressSnapshotPayload;
import dev.fupez.rpgplayable.persistence.ProgressCodec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import java.io.IOException;

/** One server-side mutation boundary for this slice. No client state is authoritative. */
public final class RpgGameplay {
    private static final Identifier STRENGTH_MODIFIER = Identifier.of("rpgplayable", "strength");
    private static final Identifier RESTORE_PHASE = Identifier.of("rpgplayable", "restore_progress");
    private static final AttachmentType<PlayerProgress> PROGRESS = AttachmentRegistry.create(
            Identifier.of("rpgplayable", "progress"), builder -> builder
                    .initializer(() -> PlayerProgress.DEFAULT).persistent(ProgressCodec.CODEC).copyOnDeath());
    private static ProgressionRules rules = ProgressionRules.DEFAULT;

    private RpgGameplay() { }
    public static PlayerProgress progress(ServerPlayerEntity player) { return player.getAttachedOrCreate(PROGRESS); }
    public static ProgressionRules rules() { return rules; }

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(ProgressActionPayload.ID, ProgressActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ProgressSnapshotPayload.ID, ProgressSnapshotPayload.CODEC);
        // Fabric 1.21.10 invokes this receiver on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(ProgressActionPayload.ID,
                (payload, context) -> handleAction(context.player(), payload));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                rules = ProgressionConfig.load(FabricLoader.getInstance().getConfigDir().resolve("rpgplayable.properties"));
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("Cannot load RPG Playable server tuning", failure);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> rules = ProgressionRules.DEFAULT);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> restore(handler.player));
        // Fabric transfers attachments in DEFAULT_PHASE. Never derive attributes or send
        // a snapshot before that transfer, even if mod entrypoint order changes.
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, RESTORE_PHASE);
        ServerPlayerEvents.AFTER_RESPAWN.register(RESTORE_PHASE, (oldPlayer, newPlayer, alive) -> restore(newPlayer));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> restore(player));
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, damage) -> {
            if (victim.getType().getSpawnGroup() != SpawnGroup.MONSTER
                    || !(damage.getAttacker() instanceof ServerPlayerEntity player)
                    || !player.isAlive() || player.isSpectator() || player.isCreative()) return;
            var before = progress(player);
            var after = Progression.addXp(before, rules.killXp(), rules);
            if (!after.equals(before)) {
                player.setAttached(PROGRESS, after);
                if (after.level() > before.level())
                    player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 1.1f);
                send(player, 0, true);
            }
        });
        RpgPlayable.LOGGER.info("Registered RPG Playable progression");
    }

    public static void handleAction(ServerPlayerEntity player, ProgressActionPayload action) {
        // Reject stale player objects, dead players and spectators, even if a request was queued earlier.
        if (!player.isAlive() || player.isSpectator()
                || player.getEntityWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) != player) {
            send(player, action.requestId(), false);
            return;
        }
        var before = progress(player);
        var after = switch (action.action()) {
            case ProgressActionPayload.SELECT_WARRIOR -> Progression.selectWarrior(before);
            case ProgressActionPayload.ALLOCATE_STRENGTH -> Progression.allocateStrength(before);
            default -> before;
        };
        boolean accepted = !after.equals(before);
        if (accepted) {
            player.setAttached(PROGRESS, after);
            applyStrength(player);
        }
        send(player, action.requestId(), accepted);
    }

    private static void restore(ServerPlayerEntity player) {
        var normalized = Progression.addXp(progress(player), 0, rules);
        player.setAttached(PROGRESS, normalized);
        applyStrength(player);
        send(player, 0, true);
    }

    private static void applyStrength(ServerPlayerEntity player) {
        var attribute = player.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
        if (attribute == null) throw new IllegalStateException("Player has no attack damage attribute");
        attribute.removeModifier(STRENGTH_MODIFIER);
        double bonus = rules.strengthBonus(progress(player));
        if (bonus != 0) attribute.addTemporaryModifier(new EntityAttributeModifier(
                STRENGTH_MODIFIER, bonus, EntityAttributeModifier.Operation.ADD_VALUE));
    }

    private static void send(ServerPlayerEntity player, long requestId, boolean accepted) {
        if (!player.isDisconnected() && ServerPlayNetworking.canSend(player, ProgressSnapshotPayload.ID)) {
            var state = progress(player);
            ServerPlayNetworking.send(player, new ProgressSnapshotPayload(state, rules.xpToNextLevel(state.level()),
                    rules.strengthBonus(state), requestId, accepted));
        }
    }
}
