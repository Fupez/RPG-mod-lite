package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.ProgressActionPayload;
import dev.fupez.rpgplayable.network.ProgressSnapshotPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;

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
                if (STATE.accept(payload)) celebrate(context.client());
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (actor != null && client.player != actor) clear();
            STATE.tick();
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
    private static void celebrate(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        var player = client.player;
        for (int i = 0; i < 18; i++) {
            var random = player.getRandom();
            client.world.addParticleClient(ParticleTypes.HAPPY_VILLAGER,
                    player.getX() + (random.nextDouble() - 0.5) * 1.4,
                    player.getY() + 0.4 + random.nextDouble() * 1.6,
                    player.getZ() + (random.nextDouble() - 0.5) * 1.4, 0, 0.1, 0);
        }
    }
}
