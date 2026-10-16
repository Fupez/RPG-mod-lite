package dev.fupez.rpgplayable.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty intent: no client-supplied target, damage, timing or cooldown. */
public record CleaveRequestPayload() implements CustomPayload {
    public static final Id<CleaveRequestPayload> ID = new Id<>(Identifier.of("rpgplayable", "cleave"));
    public static final PacketCodec<RegistryByteBuf, CleaveRequestPayload> CODEC = PacketCodec.unit(new CleaveRequestPayload());
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
