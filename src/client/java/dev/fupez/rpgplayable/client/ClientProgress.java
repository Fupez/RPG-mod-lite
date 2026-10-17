package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.ProgressActionPayload;
import dev.fupez.rpgplayable.network.ProgressSnapshotPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class ClientProgress {
    private static final ClientProgressState STATE = new ClientProgressState();
    private static ClientPlayerEntity actor;
    private ClientProgress() { }
    public static ClientProgressState state() { return STATE; }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ProgressSnapshotPayload.ID, (payload, context) -> {
            var receivingPlayer = context.player();
            context.client().execute(() -> {
                if (context.client().player != receivingPlayer) return;
                if (actor != receivingPlayer) { STATE.clear(); actor = receivingPlayer; }
                STATE.accept(payload);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (actor != null && client.player != actor) clear();
            if (!client.isPaused()) STATE.tick();
        });
    }

    public static boolean request(int action) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || !client.player.isAlive() || client.player.isSpectator()
                || !ClientPlayNetworking.canSend(ProgressActionPayload.ID)) return false;
        var request = STATE.beginRequest(action);
        request.ifPresent(ClientPlayNetworking::send);
        return request.isPresent();
    }

    private static void clear() { STATE.clear(); actor = null; }
}
