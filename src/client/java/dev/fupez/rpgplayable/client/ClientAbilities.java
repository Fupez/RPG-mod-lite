package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class ClientAbilities {
    private static final ClientCombatState STATE = new ClientCombatState();
    private static ClientPlayerEntity actor;
    private ClientAbilities() { }
    public static ClientCombatState state() { return STATE; }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CombatSnapshotPayload.ID, (payload, context) -> {
            var receivingPlayer = context.player();
            context.client().execute(() -> {
                if (context.client().player != receivingPlayer) return;
                if (actor != receivingPlayer) { STATE.clear(); actor = receivingPlayer; }
                STATE.accept(payload);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(AbilityAnimationPayload.ID, (payload, context) -> {
            var receivingPlayer = context.player();
            context.client().execute(() -> {
                if (context.client().player != receivingPlayer) return;
                if (payload.entityId() != receivingPlayer.getId()) {
                    if (payload.windupTicks() > 0 && context.client().world != null
                            && context.client().world.getEntityById(payload.entityId()) instanceof net.minecraft.entity.LivingEntity observerActor) {
                        observerActor.handSwinging = false;
                        observerActor.swingHand(net.minecraft.util.Hand.OFF_HAND);
                    }
                    return;
                }
                if (actor != receivingPlayer) { STATE.clear(); actor = receivingPlayer; }
                STATE.animate(payload.style(), payload.windupTicks(), payload.recoveryTicks());
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { STATE.clear(); actor = null; });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (actor != null && client.player != actor) { STATE.clear(); actor = null; }
            if (!client.isPaused()) STATE.tick();
        });
    }
    public static void request(int slot) {
        var client = MinecraftClient.getInstance();
        if (client.player != null && client.player.isAlive() && !client.player.isSpectator()
                && ClientPlayNetworking.canSend(AbilityRequestPayload.ID))
            STATE.beginRequest(slot).ifPresent(ClientPlayNetworking::send);
    }
}
