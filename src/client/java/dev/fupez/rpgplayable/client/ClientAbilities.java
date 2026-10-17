package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

public final class ClientAbilities {
    private static final ClientCombatState STATE = new ClientCombatState();
    private static final AbilityAnimations<ClientWorld, Entity> ANIMATIONS = new AbilityAnimations<>();
    private static ClientPlayerEntity actor;
    private static ClientWorld actorWorld;
    private ClientAbilities() { }
    public static ClientCombatState state() { return STATE; }
    public static AbilityAnimations.Pose animation(AbstractClientPlayerEntity player, float tickDelta) {
        var world = MinecraftClient.getInstance().world;
        if (world == null || player.getEntityWorld() != world || !player.isAlive() || player.isRemoved()
                || player.isSpectator() || world.getEntityById(player.getId()) != player)
            return AbilityAnimations.Pose.NONE;
        return ANIMATIONS.sample(world, player.getId(), player, tickDelta);
    }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CombatSnapshotPayload.ID, (payload, context) -> {
            var receivingPlayer = context.player();
            var receivingWorld = context.client().world;
            context.client().execute(() -> {
                if (context.client().player != receivingPlayer || context.client().world != receivingWorld) return;
                bindActor(context.client());
                STATE.accept(payload);
                if (payload.result() == CombatSnapshotPayload.INTERRUPTED)
                    ANIMATIONS.receive(receivingWorld, receivingPlayer.getId(), receivingPlayer, 0, 0, 0);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(AbilityAnimationPayload.ID, (payload, context) -> {
            var receivingPlayer = context.player();
            var receivingWorld = context.client().world;
            context.client().execute(() -> {
                if (context.client().player != receivingPlayer || context.client().world != receivingWorld
                        || receivingWorld == null) return;
                bindActor(context.client());
                var entity = receivingWorld.getEntityById(payload.entityId());
                if (!(entity instanceof AbstractClientPlayerEntity player) || !player.isAlive() || player.isRemoved()) return;
                ANIMATIONS.receive(receivingWorld, payload.entityId(), player, payload.style(), payload.windupTicks(), payload.recoveryTicks());
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STATE.clear(); ANIMATIONS.clear(); actor = null; actorWorld = null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            bindActor(client);
            if (!client.isPaused()) {
                STATE.tick();
                ANIMATIONS.tick(client.world, id -> client.world == null ? null : client.world.getEntityById(id),
                        entity -> entity.isAlive() && !entity.isRemoved());
            }
        });
    }
    private static void bindActor(MinecraftClient client) {
        if (actor != client.player || actorWorld != client.world) {
            STATE.clear(); ANIMATIONS.clear(); actor = client.player; actorWorld = client.world;
        }
    }
    public static void request(int slot) {
        var client = MinecraftClient.getInstance();
        if (client.player != null && client.player.isAlive() && !client.player.isSpectator()
                && ClientPlayNetworking.canSend(AbilityRequestPayload.ID))
            STATE.beginRequest(slot).ifPresent(ClientPlayNetworking::send);
    }
}
