package dev.fupez.rpgplayable.player;

import dev.fupez.rpgplayable.RpgPlayable;
import dev.fupez.rpgplayable.config.ProgressionConfig;
import dev.fupez.rpgplayable.network.ProgressActionPayload;
import dev.fupez.rpgplayable.network.ProgressSnapshotPayload;
import dev.fupez.rpgplayable.persistence.ProgressCodec;
import dev.fupez.rpgplayable.stats.DerivedStats;
import dev.fupez.rpgplayable.stats.Stat;
import java.math.BigInteger;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.entry.RegistryEntry;
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
    public static DerivedStats derived(ServerPlayerEntity player) { return DerivedStats.calculate(progress(player), rules); }

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
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> restore(handler.player, false));
        // Fabric transfers attachments in DEFAULT_PHASE. Never derive attributes or send
        // a snapshot before that transfer, even if mod entrypoint order changes.
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, RESTORE_PHASE);
        ServerPlayerEvents.AFTER_RESPAWN.register(RESTORE_PHASE, (oldPlayer, newPlayer, alive) -> restore(newPlayer, !alive));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> restore(player, false));
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, damage) -> {
            if (victim.getType().getSpawnGroup() != SpawnGroup.MONSTER
                    || !(damage.getAttacker() instanceof ServerPlayerEntity player)
                    || !player.isAlive() || player.isSpectator() || player.isCreative()) return;
            awardXp(player, rules.killXp());
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
            case ProgressActionPayload.SELECT_WARRIOR -> Progression.select(before, PlayerClass.WARRIOR);
            case ProgressActionPayload.SELECT_MAGE -> Progression.select(before, PlayerClass.MAGE);
            case ProgressActionPayload.SELECT_ROGUE -> Progression.select(before, PlayerClass.ROGUE);
            case ProgressActionPayload.ALLOCATE_STRENGTH -> Progression.allocate(before, Stat.STRENGTH);
            case ProgressActionPayload.ALLOCATE_VITALITY -> Progression.allocate(before, Stat.VITALITY);
            case ProgressActionPayload.ALLOCATE_AGILITY -> Progression.allocate(before, Stat.AGILITY);
            case ProgressActionPayload.ALLOCATE_INTELLIGENCE -> Progression.allocate(before, Stat.INTELLIGENCE);
            case ProgressActionPayload.ALLOCATE_SPIRIT -> Progression.allocate(before, Stat.SPIRIT);
            default -> before;
        };
        boolean accepted = !after.equals(before);
        if (accepted) {
            player.setAttached(PROGRESS, after);
            applyNativeStats(player);
        }
        send(player, action.requestId(), accepted);
    }

    public static void awardXp(ServerPlayerEntity player, long amount) {
        awardXp(player, BigInteger.valueOf(amount));
    }

    public static void awardXp(ServerPlayerEntity player, BigInteger amount) {
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Invalid XP award");
        if (!player.isAlive() || player.isCreative() || player.isSpectator()
                || player.getEntityWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) != player) return;
        var before = progress(player);
        var after = Progression.addXp(before, amount, rules);
        if (after.equals(before)) return;
        player.setAttached(PROGRESS, after);
        if (after.level().compareTo(before.level()) > 0) {
            player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.55f, 1.1f);
            dev.fupez.rpgplayable.abilities.AbilityVisuals.levelUp(player);
        }
        send(player, 0, true);
    }

    private static void restore(ServerPlayerEntity player, boolean respawnHeal) {
        var normalized = Progression.addXp(progress(player), BigInteger.ZERO, rules);
        player.setAttached(PROGRESS, normalized);
        applyNativeStats(player);
        if (respawnHeal) player.setHealth(player.getMaxHealth());
        send(player, 0, true);
    }

    private static void applyNativeStats(ServerPlayerEntity player) {
        var stats = derived(player);
        modifier(player, EntityAttributes.ATTACK_DAMAGE, STRENGTH_MODIFIER, stats.meleeBonus(), EntityAttributeModifier.Operation.ADD_VALUE);
        modifier(player, EntityAttributes.BLOCK_BREAK_SPEED, Identifier.of("rpgplayable", "strength_mining"),
                stats.miningSpeedMultiplier() - 1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, EntityAttributes.MAX_HEALTH, Identifier.of("rpgplayable", "vitality"),
                stats.maxHealthBonus(), EntityAttributeModifier.Operation.ADD_VALUE);
        modifier(player, EntityAttributes.MOVEMENT_SPEED, Identifier.of("rpgplayable", "agility_speed"),
                stats.movementSpeedMultiplier() - 1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, EntityAttributes.ATTACK_SPEED, Identifier.of("rpgplayable", "agility_attack"),
                stats.attackSpeedBonus(), EntityAttributeModifier.Operation.ADD_VALUE);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void modifier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> type, Identifier id,
                                 double value, EntityAttributeModifier.Operation operation) {
        var attribute = player.getAttributeInstance(type);
        if (attribute == null) throw new IllegalStateException("Player missing RPG attribute " + id);
        attribute.removeModifier(id);
        if (value != 0) attribute.addTemporaryModifier(new EntityAttributeModifier(id, value, operation));
    }

    private static void send(ServerPlayerEntity player, long requestId, boolean accepted) {
        if (!player.isDisconnected() && ServerPlayNetworking.canSend(player, ProgressSnapshotPayload.ID)) {
            var state = progress(player);
            ServerPlayNetworking.send(player, new ProgressSnapshotPayload(state, rules.xpToNextLevel(state.level()),
                    rules.strengthBonus(state), requestId, accepted, derived(player)));
        }
    }
}
