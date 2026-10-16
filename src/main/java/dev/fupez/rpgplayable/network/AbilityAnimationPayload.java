package dev.fupez.rpgplayable.network;

import dev.fupez.rpgplayable.abilities.AnimationStyle;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server announces presentation only; 0/0 cancels an actor's current animation. */
public record AbilityAnimationPayload(int entityId, int style, int windupTicks, int recoveryTicks) implements CustomPayload {
    public static final Id<AbilityAnimationPayload> ID = new Id<>(Identifier.of("rpgplayable", "ability_animation"));
    public static final PacketCodec<RegistryByteBuf, AbilityAnimationPayload> CODEC = PacketCodec.ofStatic(
            (b, v) -> { b.writeVarInt(v.entityId); b.writeVarInt(v.style); b.writeVarInt(v.windupTicks); b.writeVarInt(v.recoveryTicks); },
            b -> new AbilityAnimationPayload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
    public AbilityAnimationPayload {
        if (entityId < 0 || style < 0 || style >= AnimationStyle.values().length || windupTicks < 0 || recoveryTicks < 0
                || windupTicks + (long) recoveryTicks > 72000 || (windupTicks == 0) != (recoveryTicks == 0))
            throw new IllegalArgumentException("Invalid ability animation");
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
