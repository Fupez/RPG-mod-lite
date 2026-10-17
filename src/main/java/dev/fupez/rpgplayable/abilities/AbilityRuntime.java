package dev.fupez.rpgplayable.abilities;

import dev.fupez.rpgplayable.network.*;
import dev.fupez.rpgplayable.player.*;
import dev.fupez.rpgplayable.stats.DerivedStats;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;

/** Server-only resource, validation and timing boundary for the nine concrete abilities. */
public final class AbilityRuntime {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<PlayerClass, AbilityKit> KITS = Map.of(
            PlayerClass.WARRIOR, new WarriorKit(), PlayerClass.MAGE, new MageKit(), PlayerClass.ROGUE, new RogueKit());
    private static final AttachmentType<Long> LAST_REQUEST = AttachmentRegistry.create(
            Identifier.of("rpgplayable", "last_ability_request"), b -> b.initializer(() -> -1L));
    private static final Identifier RESTORE_PHASE = Identifier.of("rpgplayable", "restore_combat");
    private static long clock;
    private AbilityRuntime() { }
    private static final class State {
        PlayerClass type;
        DerivedStats stats;
        ResourcePool resources;
        final long[] readyAt = new long[3];
        Cast cast;
        State(ServerPlayerEntity player) {
            type = RpgGameplay.progress(player).playerClass(); stats = RpgGameplay.derived(player);
            resources = new ResourcePool(stats.maxMana(), stats.maxStamina());
        }
    }
    private static final class Cast {
        final ServerPlayerEntity actor;
        final net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension;
        final AbilityDefinition definition;
        final long startedAt;
        boolean impacted;
        Cast(ServerPlayerEntity actor, AbilityDefinition definition) {
            this.actor = actor; this.dimension = actor.getEntityWorld().getRegistryKey();
            this.definition = definition; this.startedAt = clock;
        }
        int elapsed() { return (int) Math.min(72000, Math.max(0, clock - startedAt)); }
    }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(AbilityRequestPayload.ID, AbilityRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CombatSnapshotPayload.ID, CombatSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AbilityAnimationPayload.ID, AbilityAnimationPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AbilityRequestPayload.ID, (payload, context) -> {
            var player = context.player();
            if (player.getAttachedOrCreate(LAST_REQUEST) == clock) return;
            player.setAttached(LAST_REQUEST, clock);
            request(player, payload.slot(), payload.requestId());
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try { AbilityCatalog.load(FabricLoader.getInstance().getConfigDir().resolve("rpgplayable-abilities.properties")); }
            catch (java.io.IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("Cannot load RPG ability tuning", failure);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear(); clock = 0; KITS.values().forEach(AbilityKit::clear); AbilityCatalog.reset();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, 0, CombatSnapshotPayload.UPDATE));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var state = STATES.get(handler.player.getUuid());
            if (state != null) interrupt(state, handler.player);
        });
        // Core restores copied attachments/native attributes first, then combat derives caps.
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(Identifier.of("rpgplayable", "restore_progress"), RESTORE_PHASE);
        ServerPlayerEvents.AFTER_RESPAWN.register(RESTORE_PHASE, (oldPlayer, newPlayer, alive) -> {
            var state = state(newPlayer); interrupt(state, oldPlayer);
            if (!alive) state.resources = new ResourcePool(state.stats.maxMana(), state.stats.maxStamina());
            send(newPlayer, 0, CombatSnapshotPayload.UPDATE);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            interrupt(state(player), player); send(player, 0, CombatSnapshotPayload.UPDATE);
        });
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (world.isClient()) return ActionResult.PASS;
            var state = STATES.get(player.getUuid());
            return state != null && state.cast != null ? ActionResult.FAIL : ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(AbilityRuntime::tick);
    }
    private static State state(ServerPlayerEntity player) {
        var state = STATES.computeIfAbsent(player.getUuid(), id -> new State(player));
        var type = RpgGameplay.progress(player).playerClass();
        var stats = RpgGameplay.derived(player);
        if (state.type != type) {
            state.type = type; state.resources = new ResourcePool(stats.maxMana(), stats.maxStamina());
        } else state.resources = state.resources.recover(stats.maxMana(), stats.maxStamina(), 0, 0);
        state.stats = stats;
        return state;
    }
    private static boolean eligible(ServerPlayerEntity player) {
        return player.isAlive() && player.isLoaded() && !player.isDisconnected() && !player.isCreative() && !player.isSpectator()
                && player.getEntityWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) == player
                && RpgGameplay.progress(player).selected();
    }
    public static boolean request(ServerPlayerEntity player, int slot) { return request(player, slot, 0); }
    public static boolean request(ServerPlayerEntity player, int slot, long requestId) {
        var state = state(player);
        var definition = AbilityCatalog.get(state.type, slot);
        int result;
        if (requestId < 0 || slot < 0 || slot > 2 || definition == null || !eligible(player)) result = CombatSnapshotPayload.UNAVAILABLE;
        else if (state.cast != null || clock < state.readyAt[slot]) result = CombatSnapshotPayload.NOT_READY;
        else if (!state.resources.canSpend(definition.resource(), definition.cost())) result = CombatSnapshotPayload.NO_RESOURCE;
        else if (!KITS.get(state.type).canStart(player, definition.id())) result = CombatSnapshotPayload.BLOCKED;
        else {
            state.resources = state.resources.spend(definition.resource(), definition.cost());
            state.readyAt[slot] = clock + definition.cooldownTicks();
            state.cast = new Cast(player, definition);
            AbilityVisuals.start(player, definition);
            animate(player, definition, false);
            send(player, requestId, CombatSnapshotPayload.ACCEPTED);
            return true;
        }
        send(player, Math.max(0, requestId), result);
        return false;
    }
    public static CombatSnapshotPayload snapshot(ServerPlayerEntity player) {
        return snapshot(state(player), 0, CombatSnapshotPayload.UPDATE);
    }
    private static CombatSnapshotPayload snapshot(State state, long requestId, int result) {
        int[] remaining = new int[3], totals = new int[3]; double[] costs = new double[3];
        for (var definition : AbilityCatalog.forClass(state.type)) {
            int slot = definition.slot(); totals[slot] = definition.cooldownTicks(); costs[slot] = definition.cost();
            remaining[slot] = (int) Math.max(0, Math.min(totals[slot], state.readyAt[slot] - clock));
        }
        var cast = state.cast;
        return new CombatSnapshotPayload(state.resources.mana(), state.stats.maxMana(), state.resources.stamina(), state.stats.maxStamina(),
                remaining, totals, costs, cast == null ? -1 : cast.definition.slot(), cast == null ? 0 : cast.elapsed(),
                cast == null ? 0 : cast.definition.windupTicks(), cast == null ? 0 : cast.definition.recoveryTicks(),
                cast == null ? 0 : cast.definition.animation().ordinal(), requestId, result);
    }
    private static void send(ServerPlayerEntity player, long requestId, int result) {
        if (!player.isDisconnected() && ServerPlayNetworking.canSend(player, CombatSnapshotPayload.ID))
            ServerPlayNetworking.send(player, snapshot(state(player), requestId, result));
    }
    private static void animate(ServerPlayerEntity player, AbilityDefinition definition, boolean cancelled) {
        var payload = new AbilityAnimationPayload(player.getId(), definition.animation().ordinal(),
                cancelled ? 0 : definition.windupTicks(), cancelled ? 0 : definition.recoveryTicks());
        for (var watcher : PlayerLookup.tracking(player))
            if (watcher != player && ServerPlayNetworking.canSend(watcher, AbilityAnimationPayload.ID)) ServerPlayNetworking.send(watcher, payload);
        if (!player.isDisconnected() && ServerPlayNetworking.canSend(player, AbilityAnimationPayload.ID)) ServerPlayNetworking.send(player, payload);
    }
    private static void interrupt(State state, ServerPlayerEntity recipient) {
        var cast = state.cast;
        if (cast == null) return;
        state.cast = null;
        AbilityVisuals.cancel(cast.actor, cast.definition.id());
        animate(cast.actor, cast.definition, true);
        send(recipient, 0, CombatSnapshotPayload.INTERRUPTED);
    }
    private static void tick(MinecraftServer server) {
        clock++;
        var iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var state = entry.getValue();
            var player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) state(player);
            state.resources = state.resources.recover(state.stats.maxMana(), state.stats.maxStamina(),
                    state.stats.manaRegen() / 20, state.stats.staminaRegen() / 20);
            var cast = state.cast;
            if (cast != null) {
                if (player != cast.actor || !eligible(cast.actor) || !cast.dimension.equals(cast.actor.getEntityWorld().getRegistryKey())) {
                    interrupt(state, player == null ? cast.actor : player);
                } else {
                    int elapsed = cast.elapsed();
                    if (!cast.impacted && elapsed >= cast.definition.windupTicks()) {
                        cast.impacted = true;
                        var kit = KITS.get(state.type);
                        if (kit.canStart(player, cast.definition.id())) {
                            // Force a fresh impact swing even after a long native item-use animation.
                            player.handSwinging = false;
                            player.swingHand(Hand.MAIN_HAND, true);
                            player.resetLastAttackedTicks();
                            kit.execute(player, cast.definition.id(), state.stats);
                        } else interrupt(state, player);
                    }
                    if (state.cast != null && elapsed >= cast.definition.windupTicks() + cast.definition.recoveryTicks()) state.cast = null;
                }
            }
            if (player != null && (clock % 10 == 0 || (state.cast != null && clock % 5 == 0)))
                send(player, 0, CombatSnapshotPayload.UPDATE);
            if (player == null && state.cast == null && Arrays.stream(state.readyAt).allMatch(deadline -> deadline <= clock)
                    && state.resources.mana() >= state.stats.maxMana() && state.resources.stamina() >= state.stats.maxStamina()) iterator.remove();
        }
        KITS.values().forEach(kit -> kit.tick(server));
    }
}
