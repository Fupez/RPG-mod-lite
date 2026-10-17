package dev.fupez.rpgplayable.client.fx;

import dev.fupez.rpgplayable.network.VisualEffectPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.*;

/** Compact native particles. Core marks bypass vanilla random thinning only after explicit quality/camera budgets. */
public final class ClientVisualEffects {
    private static final VisualEffectQueue<ClientWorld, Entity> QUEUE = new VisualEffectQueue<>();
    private static Entity observer;
    private ClientVisualEffects() { }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(VisualEffectPayload.ID, (payload, context) -> {
            var receivingWorld = context.client().world;
            var receivingPlayer = context.player();
            context.client().execute(() -> {
                var client = context.client();
                if (client.world == null || client.world != receivingWorld || client.player != receivingPlayer) return;
                bind(client);
                QUEUE.receive(client.world, client.world.getEntityById(payload.entityId()), payload);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { QUEUE.clear(); observer = null; });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            bind(client);
            if (client.world == null || client.player == null || client.isPaused()) return;
            var mode = client.options.getParticles().getValue();
            var quality = switch (mode) {
                case ALL -> VisualEffectQueue.Quality.ALL;
                case DECREASED -> VisualEffectQueue.Quality.DECREASED;
                case MINIMAL -> VisualEffectQueue.Quality.MINIMAL;
            };
            var camera = client.gameRenderer.getCamera().getCameraPos();
            for (var p : QUEUE.tick(client.world, camera, quality,
                    id -> {
                        var entity = client.world.getEntityById(id);
                        return entity != null && entity.isAlive() && !entity.isRemoved() ? entity : null;
                    }, Entity::getEntityPos)) {
                ParticleEffect particle = switch (p.sprite()) {
                    case DUST -> new DustParticleEffect(p.color(), p.scale());
                    case SPARK -> ParticleTypes.ELECTRIC_SPARK;
                    case GLOW -> ParticleTypes.END_ROD;
                    case EMBER -> ParticleTypes.SMALL_FLAME;
                    case CRIT -> ParticleTypes.CRIT;
                    case DEBRIS -> {
                        var state = client.world.getBlockState(BlockPos.ofFloored(p.position().add(0, -.2, 0)));
                        if (state.isAir()) state = Blocks.STONE.getDefaultState();
                        yield new BlockStateParticleEffect(ParticleTypes.BLOCK, state);
                    }
                };
                var pos = p.position(); var velocity = p.velocity();
                if (p.importance() == 0)
                    client.world.addImportantParticleClient(particle, true, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
                else client.world.addParticleClient(particle, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
            }
        });
    }
    private static void bind(MinecraftClient client) {
        if (observer != client.player) { QUEUE.clear(); observer = client.player; }
        QUEUE.bind(client.world);
    }
}
