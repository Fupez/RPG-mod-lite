package dev.fupez.rpgplayable.abilities;

import dev.fupez.rpgplayable.stats.DerivedStats;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public interface AbilityKit {
    default boolean canStart(ServerPlayerEntity player, AbilityId id) { return true; }
    void execute(ServerPlayerEntity player, AbilityId id, DerivedStats stats);
    default void tick(MinecraftServer server) { }
    default void clear() { }
}
