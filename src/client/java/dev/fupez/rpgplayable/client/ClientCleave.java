package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class ClientCleave {
    private static final ClientCleaveState STATE = new ClientCleaveState();
    private static ClientPlayerEntity actor;
    private ClientCleave() { }
    public static ClientCleaveState state() { return STATE; }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CleaveStatePayload.ID, (payload, context) -> {
            var receivingPlayer = context.player();
            context.client().execute(() -> {
                if (context.client().player != receivingPlayer) return;
                if (actor != receivingPlayer) { STATE.clear(); actor = receivingPlayer; }
                STATE.accept(payload);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { STATE.clear(); actor = null; });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (actor != null && client.player != actor) { STATE.clear(); actor = null; }
            if (!client.isPaused()) STATE.tick();
        });
    }
    public static void request() {
        var client = MinecraftClient.getInstance();
        if (client.player != null && client.player.isAlive() && !client.player.isSpectator()
                && ClientPlayNetworking.canSend(CleaveRequestPayload.ID) && STATE.beginRequest())
            ClientPlayNetworking.send(new CleaveRequestPayload());
    }
}
